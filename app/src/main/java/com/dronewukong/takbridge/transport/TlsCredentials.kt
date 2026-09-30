package com.dronewukong.takbridge.transport

import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/** Encrypted PKCS12 stays app-private; its password is session-only, never persisted. */
object TlsCredentials {
    fun loadClient(file: File, password: String): KeyStore {
        val store = KeyStore.getInstance("PKCS12")
        file.inputStream().use { store.load(it, password.toCharArray()) }
        val keys = store.aliases().toList().filter { store.isKeyEntry(it) }
        require(keys.isNotEmpty()) { "PKCS12 has no client private key" }
        keys.forEach { alias ->
            require(store.getKey(alias, password.toCharArray()) != null) { "Client key cannot be unlocked" }
            (store.getCertificate(alias) as X509Certificate).checkValidity()
        }
        return store
    }

    fun loadCa(file: File): List<X509Certificate> = file.inputStream().use {
        CertificateFactory.getInstance("X.509").generateCertificates(it)
            .map { cert -> cert as X509Certificate }.also { certs ->
                require(certs.isNotEmpty()) { "No CA certificates found" }
                certs.forEach { cert ->
                    require(cert.basicConstraints >= 0) { "Trust file must contain CA certificates" }
                    cert.checkValidity()
                }
            }
    }

    fun context(clientPath: String, password: String, caPath: String): SSLContext {
        val client = clientPath.takeIf { it.isNotBlank() }?.let { loadClient(File(it), password) }
        val kmf = client?.let { store ->
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(store, password.toCharArray())
            }
        }
        // An explicit CA file takes precedence. Otherwise use CA certificates from the
        // PKCS12 chain, never the client leaf as a trust anchor; fall back to system CAs.
        val cas = if (caPath.isNotBlank()) loadCa(File(caPath)) else client?.aliases()?.toList()
            ?.flatMap { alias -> client.getCertificateChain(alias)?.toList()
                ?: listOfNotNull(client.getCertificate(alias)) }
            ?.filterIsInstance<X509Certificate>()?.filter { it.basicConstraints >= 0 }.orEmpty()
        val trust = if (cas.isEmpty()) null else KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            cas.forEachIndexed { i, cert -> cert.checkValidity(); setCertificateEntry("ca-$i", cert) }
        }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(trust as KeyStore?)
        return SSLContext.getInstance("TLS").apply { init(kmf?.keyManagers, tmf.trustManagers, null) }
    }
}
