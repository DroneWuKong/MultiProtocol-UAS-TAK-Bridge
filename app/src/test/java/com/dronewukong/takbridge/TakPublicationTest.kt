package com.dronewukong.takbridge

import com.dronewukong.takbridge.cot.*
import com.dronewukong.takbridge.mavlink.GpsPosition
import com.dronewukong.takbridge.transport.TakConfig
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class TakPublicationTest {
    private val now = 1_800_000_000_000L
    private val position = GpsPosition(41.88, -87.63, 201.2, 12.5, 270.0, 3, 12, 1.1, now)
    private val config = TakConfig(uid = "aircraft-'&1", callsign = "Pilot's & <drone>", videoEnabled = true,
        videoUrl = "rtsp://192.168.1.20:8554/camera?channel=1&profile=low")
    private fun document(xml: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
    private fun element(xml: String, tag: String) = document(xml).getElementsByTagName(tag).item(0) as Element

    @Test fun videoSharesTheRealPositionAndIdentityInOneWellFormedEvent() {
        val pub = TakPublication.build(position, config, false, now)
        assertEquals(position.lat.toString(), element(pub.xml,"point").getAttribute("lat"))
        assertEquals(position.lon.toString(), element(pub.xml,"point").getAttribute("lon"))
        assertEquals("9999999.00", element(pub.xml,"point").getAttribute("hae"))
        assertEquals(config.uid, element(pub.xml,"event").getAttribute("uid"))
        assertEquals(config.callsign, element(pub.xml,"contact").getAttribute("callsign"))
        assertEquals(config.videoUrl, element(pub.xml,"__video").getAttribute("url"))
        assertEquals(CotFormatter.videoUid(config.uid), element(pub.xml,"__video").getAttribute("uid"))
        assertEquals(1, document(pub.xml).getElementsByTagName("event").length)
        assertEquals(now + 5000, pub.validUntilMs)
    }
    @Test fun disablingSharingExplicitlyClearsTheSameVideoIdentity() {
        val xml = TakPublication.build(position,config.copy(videoEnabled=false),false,now).xml
        assertEquals("", element(xml,"__video").getAttribute("url"))
        assertEquals(CotFormatter.videoUid(config.uid), element(xml,"__video").getAttribute("uid"))
        assertFalse(xml.contains("192.168.1.20"))
    }
    @Test fun legacyVideoHelperRequiresRealFreshPosition() {
        val xml = CotFormatter.buildVideoAlias("aircraft", "video", "demo", config.videoUrl, position, now)
        assertEquals("41.88",element(xml,"point").getAttribute("lat"))
        assertThrows(IllegalArgumentException::class.java) {
            CotFormatter.buildVideoAlias("aircraft", "video", "demo", config.videoUrl, position.copy(fixType=0), now)
        }
    }
    @Test fun invalidStaleFutureOrAlreadyExpiredFixesNeverPublish() {
        for (bad in listOf(position.copy(lat=Double.NaN), position.copy(fixType=0),
            position.copy(timestampMs=now-5001), position.copy(timestampMs=now+1))) {
            assertThrows(IllegalArgumentException::class.java) { TakPublication.build(bad,config,false,now) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            TakPublication.build(position.copy(timestampMs=now-1000),config.copy(staleSeconds=1),false,now)
        }
        assertEquals(now+1000,TakPublication.build(position,config.copy(staleSeconds=1),false,now).validUntilMs)
    }
    @Test fun sharedUrlsRejectCredentialsFragmentsInvalidPortsAndLoopbackWithoutEchoingSecrets() {
        for (url in listOf("rtsp://user:secret@example.org/live", "rtsp://host:99999/a", "file:///etc/x",
            "rtsp://host/a#secret", "rtsp://127.0.0.1/a", "rtsp://[::1]/a", "rtsp://localhost/a",
            "rtsp://0.0.0.0/a", "rtsp://host/a b", "rtsp://")) {
            val error=assertThrows(IllegalArgumentException::class.java) { SharedVideoAddress.validate(url) }
            assertFalse(error.message!!.contains("secret"))
        }
        assertEquals("rtsp://[2001:db8::2]:8554/live", SharedVideoAddress.validate("RTSP://[2001:db8::2]:8554/live"))
    }
    @Test fun unknownAccuracyAndUnsafeXmlCannotLeakMalformedAttributes() {
        val xml=TakPublication.build(position.copy(hdop=Double.POSITIVE_INFINITY),config,false,now).xml
        assertEquals("9999999.0",element(xml,"point").getAttribute("ce"))
        for (bad in listOf("bad\u0000name", "bad\u0001name", "bad\uD800name")) {
            assertThrows(IllegalArgumentException::class.java) { TakPublication.build(position,config.copy(callsign=bad),false,now) }
        }
    }
    @Test fun legacyExplicitPointHelpersEscapeStringsAndUseDotDecimals() {
        val old=Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val xmls=listOf(CotFormatter.buildSPI("a'&", "b'&", "Pilot's & drone",41.0,-87.0,12.5),
                CotFormatter.buildSensorFov("a'&","b'&","Pilot's & drone",270.5,lat=41.0,lon=-87.0,hae=12.5),
                CotFormatter.buildEmergency("a'&","Pilot's & drone",41.0,-87.0,12.5,type="test'&"),
                CotFormatter.buildUavTasking("a'&","Pilot's & drone","host:8088:tcp?x=1&y=2",lat=41.0,lon=-87.0))
            xmls.forEach { document(it) }
            assertEquals("12.50",element(xmls[0],"point").getAttribute("hae"))
            assertEquals("270.5",element(xmls[1],"sensor").getAttribute("azimuth"))
        } finally { Locale.setDefault(old) }
    }
    @Test fun concurrentPublicationDoesNotCorruptTimestamps() {
        val pool=Executors.newFixedThreadPool(4)
        try {
            val tasks=(0..99).map { i -> pool.submit<String> {
                val t=now+i*1000L
                TakPublication.build(position.copy(timestampMs=t),config,false,t).xml
            } }
            tasks.forEachIndexed { i, future ->
                val event=element(future.get(5,TimeUnit.SECONDS),"event")
                assertEquals(now+i*1000L,java.time.Instant.parse(event.getAttribute("time")).toEpochMilli())
            }
        } finally { pool.shutdownNow() }
    }
    @Test fun simulationIsClearlyIdentifiedAndKeepsVideoMetadataLocalToItsCaller() {
        val xml=TakPublication.build(position,config,true,now).xml
        assertEquals("TAKBridge-SIMULATION",element(xml,"event").getAttribute("uid"))
        assertEquals("SIMULATION",element(xml,"contact").getAttribute("callsign"))
    }
}
