package com.dronewukong.takbridge

import android.os.Looper
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import com.dronewukong.takbridge.bridge.BridgeApplication
import com.dronewukong.takbridge.cot.TakPublication
import com.dronewukong.takbridge.mavlink.GpsPosition
import com.dronewukong.takbridge.transport.*
import com.dronewukong.takbridge.ui.TakVideoSettingsActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.ServerSocket
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class TakVideoSettingsTest {
    private val app get() = RuntimeEnvironment.getApplication() as BridgeApplication
    @Test fun aircraftIdentityPersistsAcrossLoadsAndCallsignEditsAndPreservesExistingUid() {
        val first=ConfigStore.loadTakConfig(app)
        assertEquals(first.uid,ConfigStore.loadTakConfig(app).uid)
        ConfigStore.saveTakConfig(app,first.copy(callsign="RENAMED"))
        Robolectric.buildActivity(com.dronewukong.takbridge.ui.MainActivity::class.java).use { controller ->
            val a=controller.setup().get()
            a.findViewById<EditText>(R.id.editCallsign).setText("ANOTHER-NAME")
            controller.pause()
        }
        assertEquals(first.uid,ConfigStore.loadTakConfig(app).uid)
        ConfigStore.saveTakConfig(app,first.copy(uid="existing-deployment-uid"))
        assertEquals("existing-deployment-uid",ConfigStore.loadTakConfig(app).uid)
    }
    @Test fun optInPersistsWithoutOpeningUsbOrNetworkAndDisableErasesAddress() {
        Robolectric.buildActivity(TakVideoSettingsActivity::class.java).use { controller ->
            val a=controller.setup().get()
            val enabled=a.findViewById<CheckBox>(R.id.takVideoEnabled)
            val address=a.findViewById<EditText>(R.id.takVideoAddress)
            assertFalse(enabled.isChecked)
            enabled.isChecked=true;address.setText("rtsp://camera.example:8554/live")
            a.findViewById<Button>(R.id.takVideoSave).performClick()
            assertTrue(ConfigStore.loadTakConfig(a).videoEnabled)
            assertEquals("rtsp://camera.example:8554/live",ConfigStore.loadTakConfig(a).videoUrl)
            assertFalse(app.session.running); assertFalse(app.session.sender.isTcpConnected)
            enabled.isChecked=false;a.findViewById<Button>(R.id.takVideoSave).performClick()
            assertFalse(ConfigStore.loadTakConfig(a).videoEnabled)
            assertEquals("",ConfigStore.loadTakConfig(a).videoUrl)
        }
    }
    @Test fun credentialsAreRejectedWithoutSavingOrEchoingThePassword() {
        Robolectric.buildActivity(TakVideoSettingsActivity::class.java).use { controller ->
            val a=controller.setup().get()
            a.findViewById<CheckBox>(R.id.takVideoEnabled).isChecked=true
            a.findViewById<EditText>(R.id.takVideoAddress).setText("rtsp://user:secret@camera/live")
            a.findViewById<Button>(R.id.takVideoSave).performClick()
            assertFalse(ConfigStore.loadTakConfig(a).videoEnabled)
            assertFalse(a.findViewById<TextView>(R.id.takVideoStatus).text.contains("secret"))
            assertFalse(a.findViewById<EditText>(R.id.takVideoAddress).isSaveEnabled)
        }
    }
    @Test fun runningSessionRejectsSettingsChangesAndReplayNeverSendsTheLink() {
        ConfigStore.saveTakConfig(app,TakConfig(videoEnabled=true,videoUrl="rtsp://private.example/live?token=private-value"))
        app.session.startReplay();shadowOf(Looper.getMainLooper()).idle()
        try {
            assertTrue(app.session.lastCot.contains("__video"))
            assertEquals(0L,app.session.sender.tcpSentCount)
            assertEquals(0L,app.session.sender.multicastSentCount)
            Robolectric.buildActivity(TakVideoSettingsActivity::class.java).use { controller ->
                val a=controller.setup().get()
                a.findViewById<CheckBox>(R.id.takVideoEnabled).isChecked=false
                a.findViewById<Button>(R.id.takVideoSave).performClick()
                assertTrue(ConfigStore.loadTakConfig(a).videoEnabled)
                assertTrue(a.findViewById<TextView>(R.id.takVideoStatus).text.contains("Stop"))
            }
            val report=app.session.diagnostics()
            assertFalse(report.contains("private.example"));assertFalse(report.contains("private-value"))
            assertTrue(report.contains("SOCKET_WRITES_ONLY"));assertTrue(report.contains("UNCONFIRMED"))
        } finally { app.session.stop() }
    }
    @Test fun actualTcpPeerReceivesPositionAndVideoWithoutClaimingAtakAcknowledgement() {
        val sender=TakSender(app)
        ServerSocket(0).use { server ->
            server.soTimeout=5000
            val config=TakConfig(multicastEnabled=false,tcpEnabled=true,tcpHost="localhost",tcpPort=server.localPort,
                videoEnabled=true,videoUrl="rtsp://camera.example/live?channel=1&profile=low")
            sender.updateConfig(config)
            try {
                sender.start()
                server.accept().use { peer ->
                    peer.soTimeout=5000
                    val now=System.currentTimeMillis()
                    val position=GpsPosition(41.88,-87.63,200.0,10.0,90.0,3,12,1.0,now)
                    val publication=TakPublication.build(position,config,false,now)
                    sender.send(publication.xml,publication.validUntilMs)
                    val bytes=ByteArray(publication.xml.toByteArray().size)
                    java.io.DataInputStream(peer.getInputStream()).readFully(bytes)
                    assertEquals(publication.xml,bytes.toString(Charsets.UTF_8))
                    val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(bytes.inputStream())
                    assertEquals("41.88",doc.getElementsByTagName("point").item(0).attributes.getNamedItem("lat").nodeValue)
                    val deadline=System.nanoTime()+2_000_000_000L
                    while(sender.lastTcpWriteMs==0L && System.nanoTime()<deadline) Thread.sleep(5)
                    assertEquals(1L,sender.tcpSentCount);assertTrue(sender.lastTcpWriteMs>=now)
                    sender.send("already expired",System.currentTimeMillis()-1)
                    assertEquals(1L,sender.expiredCount)
                }
            } finally { sender.stop() }
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun settingsRenderAtPhoneSizeWithScrollableActions() {
        Robolectric.buildActivity(TakVideoSettingsActivity::class.java).use { controller ->
            val a=controller.setup().get()
            val view=a.window.decorView
            val w=393; val h=800
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(w,android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(h,android.view.View.MeasureSpec.EXACTLY))
            view.layout(0,0,w,h)
            val bitmap=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file=java.io.File("build/reports/ui/tak-video-settings.png")
            file.parentFile!!.mkdirs();file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            assertTrue(a.findViewById<Button>(R.id.takVideoSave).isEnabled)
        }
    }
}
