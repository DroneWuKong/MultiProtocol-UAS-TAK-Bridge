package com.dronewukong.takbridge

import com.dronewukong.takbridge.transport.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TakTransportTest {
    companion object {
        private lateinit var dir: File
        private const val PASSWORD = "fixture-password"
        @BeforeClass @JvmStatic fun certificates() {
            // Ephemeral software-only PKI; no private keys or release credentials in the repo.
            dir = Files.createTempDirectory("tak-tls-test").toFile()
            fun keytool(vararg args: String) {
                val cmd = listOf(File(System.getProperty("java.home"), "bin/keytool").path) + args
                val process = ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start()
                val text = process.inputStream.bufferedReader().readText()
                check(process.waitFor() == 0) { text }
            }
            fun generate(name: String, vararg extra: String) = keytool("-genkeypair", "-alias", name,
                "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=$name", "-validity", "30",
                "-storetype", "PKCS12", "-keystore", "$name.p12", "-storepass", PASSWORD, *extra)
            generate("ca", "-ext", "bc=ca:true")
            keytool("-exportcert", "-rfc", "-alias", "ca", "-keystore", "ca.p12", "-storepass", PASSWORD, "-file", "ca.pem")
            for (name in listOf("server", "client")) {
                generate(name)
                keytool("-certreq", "-alias", name, "-keystore", "$name.p12", "-storepass", PASSWORD, "-file", "$name.csr")
                keytool("-gencert", "-alias", "ca", "-keystore", "ca.p12", "-storepass", PASSWORD,
                    "-infile", "$name.csr", "-outfile", "$name.pem", "-rfc", "-validity", "30",
                    "-ext", "SAN=dns:localhost", "-ext", "KU=digitalSignature,keyEncipherment",
                    "-ext", "EKU=serverAuth,clientAuth")
                keytool("-importcert", "-noprompt", "-alias", "ca", "-keystore", "$name.p12", "-storepass", PASSWORD, "-file", "ca.pem")
                keytool("-importcert", "-noprompt", "-alias", name, "-keystore", "$name.p12", "-storepass", PASSWORD, "-file", "$name.pem")
            }
            generate("unrelated", "-ext", "bc=ca:true")
            keytool("-exportcert", "-rfc", "-alias", "unrelated", "-keystore", "unrelated.p12", "-storepass", PASSWORD, "-file", "unrelated.pem")
        }
        @AfterClass @JvmStatic fun cleanup() { if (::dir.isInitialized) dir.deleteRecursively() }
    }
    private val sender = TakSender(RuntimeEnvironment.getApplication())
    @After fun stop() { sender.stop() }
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue("$message: ${sender.lastError}", condition())
    }
    private fun configure(port: Int, tls: Boolean = false, host: String = "localhost") {
        sender.updateConfig(TakConfig(multicastEnabled = false, tcpEnabled = true, tcpHost = host, tcpPort = port, useTls = tls))
        if (tls) { sender.tlsCertPath = File(dir, "client.p12").path; sender.tlsCertPassword = PASSWORD }
    }
    private fun tlsServer(): SSLServerSocket {
        val context = TlsCredentials.context(File(dir, "server.p12").path, PASSWORD, File(dir, "ca.pem").path)
        return (context.serverSocketFactory.createServerSocket(0) as SSLServerSocket).apply {
            needClientAuth = true; soTimeout = 10000
        }
    }

    @Test fun tcpWritesAreCompleteAndStopClosesSocket() {
        ServerSocket(0).use { server ->
            server.soTimeout = 10000
            configure(server.localPort)
            val received = CompletableFuture.supplyAsync {
                server.accept().use { socket -> socket.soTimeout = 10000; socket.getInputStream().readBytes().toString(Charsets.UTF_8) }
            }
            sender.start(); await("TCP connect") { sender.isTcpConnected }
            val messages = (1..20).map { "<event uid='$it'/>" }
            messages.forEachIndexed { i, xml ->
                sender.send(xml, System.currentTimeMillis() + 5000)
                await("TCP write") { sender.tcpSentCount == (i + 1).toLong() }
            }
            sender.stop()
            assertEquals(messages.joinToString(""), received.get(10, TimeUnit.SECONDS))
            assertFalse(sender.isTcpConnected)
        }
    }

    @Test fun peerEofReconnectsWhileIdleAndExpiredEventsAreDropped() {
        ServerSocket(0).use { server ->
            server.soTimeout = 10000
            configure(server.localPort)
            sender.start()
            server.accept().use { await("First connection") { sender.isTcpConnected } }
            await("EOF noticed") { !sender.isTcpConnected }
            sender.send("expired", System.currentTimeMillis() - 1)
            server.accept().use { second ->
                second.soTimeout = 8000
                await("Reconnect") { sender.isTcpConnected }
                sender.send("fresh\n", System.currentTimeMillis() + 5000)
                assertEquals("fresh", second.getInputStream().bufferedReader().readLine())
                assertEquals(1, sender.tcpSentCount)
            }
        }
    }

    @Test fun passwordProtectedClientCertificateCompletesMutualTls() {
        tlsServer().use { server ->
            configure(server.localPort, true)
            val received = CompletableFuture.supplyAsync {
                server.accept().use { socket -> socket.soTimeout = 8000; socket.getInputStream().bufferedReader().readLine() }
            }
            sender.start(); await("mTLS connect") { sender.isTcpConnected }
            sender.send("<event/>\n", System.currentTimeMillis() + 5000)
            assertEquals("<event/>", received.get(10, TimeUnit.SECONDS))
        }
    }

    @Test fun wrongHostnameIsRejected() = rejectedTls(host = "127.0.0.1")
    @Test fun untrustedServerIsRejected() = rejectedTls(ca = File(dir, "unrelated.pem").path)
    private fun rejectedTls(host: String = "localhost", ca: String = "") {
        tlsServer().use { server ->
            configure(server.localPort, true, host); sender.tlsCaPath = ca
            val peer = CompletableFuture.runAsync {
                try { server.accept().use { socket -> socket.soTimeout = 8000; socket.getInputStream().read() } } catch (_: Exception) { }
            }
            sender.start(); await("TLS failure") { sender.lastError?.startsWith("TLS:") == true }
            assertFalse(sender.isTcpConnected); assertEquals(0, sender.tcpSentCount)
            sender.stop(); peer.get(10, TimeUnit.SECONDS)
        }
    }

    @Test fun wrongPasswordAndLeafTrustFileAreRejected() {
        try { TlsCredentials.loadClient(File(dir, "client.p12"), "wrong"); fail("Accepted wrong password") } catch (_: Exception) { }
        try { TlsCredentials.loadCa(File(dir, "client.pem")); fail("Accepted leaf as CA") } catch (_: IllegalArgumentException) { }
    }

    @Test fun rapidStartStopCannotResurrectAnOldConnection() {
        ServerSocket(0).use { server ->
            configure(server.localPort)
            repeat(20) { sender.start(); sender.stop() }
            Thread.sleep(200)
            assertFalse(sender.isTcpConnected); assertFalse(sender.isMulticastConnected)
            assertEquals(0, sender.tcpSentCount)
        }
    }
}
