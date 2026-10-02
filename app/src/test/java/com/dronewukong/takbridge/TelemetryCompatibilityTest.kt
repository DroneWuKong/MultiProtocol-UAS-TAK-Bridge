package com.dronewukong.takbridge

import com.dronewukong.takbridge.mavlink.*
import com.dronewukong.takbridge.transport.ConnectionProfile
import com.dronewukong.takbridge.cot.CotFormatter
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class TelemetryCompatibilityTest {
    private fun fixture(name: String): ByteArray = javaClass.getResource("/telemetry/$name.hex")!!
        .readText().trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun GhstPassthrough.accept(name: String) = fixture(name).let { feed(it, it.size) }
    private fun MavlinkGpsParser.accept(name: String) = fixture(name).let { feed(it, it.size) }

    @Test fun ghstDecodesActualDownlinkTypesUnitsAndEquator() {
        val parser = GhstPassthrough()
        var pos: GpsPosition? = null
        parser.onGpsPosition = { pos = it }
        val wire = fixture("ghst-primary") + fixture("ghst-secondary-1")
        wire.forEach { parser.feed(byteArrayOf(it), 1) }
        assertTrue(pos!!.hasValidFix)
        assertEquals(0.0,pos!!.lat,0.0); assertEquals(-87.5,pos!!.lon,0.00001)
        assertEquals(-25.0,pos!!.altMsl,0.0)
        assertEquals(12.5,pos!!.groundSpeed,0.0); assertEquals(90.0,pos!!.heading,0.0)
        assertEquals(12,pos!!.satellites)
    }

    @Test fun ghstNeedsFixFlagsAndFreshPrimary() {
        var now=10000L
        val parser=GhstPassthrough { now }
        var pos: GpsPosition?=null; parser.onGpsPosition={pos=it}
        parser.accept("ghst-primary"); parser.accept("ghst-secondary-0")
        assertFalse(pos!!.hasValidFix)
        parser.accept("ghst-secondary-1"); assertTrue(pos!!.hasValidFix)
        now+=4000; parser.accept("ghst-secondary-1")
        assertFalse(pos!!.hasValidFix); assertEquals(10000L,pos!!.timestampMs)
    }

    @Test fun ghstExpiresSecondaryAndClearsOnReconnect() {
        var now=10000L
        val parser=GhstPassthrough { now }
        var pos: GpsPosition?=null; parser.onGpsPosition={pos=it}
        parser.accept("ghst-secondary-1"); parser.accept("ghst-primary")
        assertTrue(pos!!.hasValidFix)
        now+=4000; parser.accept("ghst-primary"); assertFalse(pos!!.hasValidFix)
        parser.reset(); parser.accept("ghst-primary"); assertFalse(pos!!.hasValidFix)
    }

    @Test fun controllerRcOnlyIsRecognizedWithoutInventingGps() {
        val router=ProtocolRouter(); var positions=0
        router.onGpsPosition={positions++}
        val bytes=fixture("controller-rc-only"); router.feed(bytes,bytes.size)
        assertEquals(3L,router.ghstParser.rcFramesReceived)
        assertEquals(0,positions)
        assertTrue(router.getStatusString().contains("controller channels only"))
        assertNull(router.getMspGpsRequest())
    }

    @Test fun radioCrcFailureDoesNotBecomeGps() {
        val parser=GhstPassthrough(); var positions=0
        parser.onGpsPosition={positions++}
        val corrupt=fixture("ghst-primary"); corrupt[corrupt.lastIndex]=(corrupt.last().toInt() xor 1).toByte()
        parser.feed(corrupt,corrupt.size)
        assertEquals(0,positions); assertEquals(1L,parser.crcErrors)
        parser.accept("ghst-primary"); parser.accept("ghst-secondary-1")
        assertTrue(positions>0)
    }

    @Test fun crsfIsSeparateFromGhst() {
        val parser=GhstPassthrough(); var pos: GpsPosition?=null; parser.onGpsPosition={pos=it}
        parser.accept("crsf-gps")
        assertTrue(pos!!.hasValidFix); assertEquals(12.5,pos!!.groundSpeed,0.0)
        assertEquals(-25.0,pos!!.altMsl,0.0); assertEquals(90.0,pos!!.heading,0.0)
    }

    @Test fun mavlinkV1AndV2GoldenPacketsDecodeAtEveryChunkBoundary() {
        for (version in 1..2) {
            val wire=fixture("mav$version-gps")
            for (split in 0..wire.size) {
                val parser=MavlinkGpsParser(); var pos: GpsPosition?=null; parser.onGpsPosition={pos=it}
                parser.feed(wire.copyOfRange(0,split),split)
                parser.feed(wire.copyOfRange(split,wire.size),wire.size-split)
                assertTrue("version $version split $split",pos!!.hasValidFix)
                assertEquals(12.5,pos!!.groundSpeed,0.0); assertEquals(123.0,pos!!.altMsl,0.0)
            }
        }
    }

    @Test fun mavlinkCrcFailureResynchronizes() {
        val parser=MavlinkGpsParser(); var positions=0; parser.onGpsPosition={positions++}
        val bad=fixture("mav2-gps"); bad[12]=(bad[12].toInt() xor 1).toByte()
        parser.feed(bad,bad.size); assertEquals(0,positions)
        parser.accept("mav1-gps"); assertEquals(1,positions); assertTrue(parser.crcErrors>0)
    }

    @Test fun signedMavlinkConsumesSignatureBeforeNextPacket() {
        val parser=MavlinkGpsParser(); var positions=0; parser.onGpsPosition={positions++}
        val bytes=fixture("mav2-signed-gps")+fixture("mav1-gps")
        parser.feed(bytes,bytes.size)
        assertEquals(2,positions); assertEquals(1L,parser.signedFrames)
    }

    @Test fun truncatedV2NoFixAndUnknownMeasurementsStayInvalid() {
        val parser=MavlinkGpsParser(); var pos: GpsPosition?=null; parser.onGpsPosition={pos=it}
        parser.accept("mav2-no-fix")
        assertFalse(pos!!.hasValidFix); assertEquals(-1.0,pos!!.groundSpeed,0.0)
        assertEquals(-1.0,pos!!.heading,0.0)
    }

    @Test fun globalPositionRequiresFreshSameSourceFixEvidence() {
        var now=10000L
        val parser=MavlinkGpsParser { now }; var pos: GpsPosition?=null; parser.onGpsPosition={pos=it}
        parser.accept("mav2-global"); assertFalse(pos!!.hasValidFix)
        parser.accept("mav2-gps"); parser.accept("mav1-global"); assertTrue(pos!!.hasValidFix)
        now+=4000; parser.accept("mav2-global"); assertFalse(pos!!.hasValidFix)
    }

    @Test fun mavlinkPinsSourceUntilResetAndHeartbeatDoesNotRefreshGps() {
        var now=10000L
        val parser=MavlinkGpsParser { now }; var count=0; var pos: GpsPosition?=null
        parser.onGpsPosition={pos=it;count++}
        parser.accept("mav1-gps"); parser.accept("mav2-other-source"); assertEquals(1,count)
        now+=6000; parser.accept("mav2-heartbeat"); assertFalse(pos!!.isFresh(now))
        parser.reset(); parser.accept("mav2-other-source"); assertEquals(2,count)
    }

    @Test fun profileCannotPollTacticalOrAutoStreams() {
        for (profile in ConnectionProfile.values()) {
            val router=ProtocolRouter(); router.setProtocol(profile.protocol)
            assertEquals(profile==ConnectionProfile.DIRECT_MSP,
                router.getMspGpsRequest(profile.pollsMsp)!=null)
        }
        assertTrue(ConnectionProfile.CONTROLLER_GHST.dtr); assertTrue(ConnectionProfile.CONTROLLER_MAVLINK.dtr)
    }

    @Test fun routerReconnectDoesNotReusePartialPackets() {
        val router=ProtocolRouter(); var count=0; router.onGpsPosition={count++}
        val wire=fixture("mav2-gps"); router.feed(wire.copyOfRange(0,20),20)
        router.reset(); router.feed(wire.copyOfRange(20,wire.size),wire.size-20)
        assertEquals(0,count); router.feed(wire,wire.size); assertEquals(1,count)
    }

    @Test fun cotPipelineHandlesEscapingLocaleAndUnknownHae() {
        val locale=Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val router=ProtocolRouter(); var xml=""
            router.onGpsPosition={if(it.hasValidFix) xml=CotFormatter.buildDroneSA("test", "J's & drone", it)}
            val wire=fixture("ghst-primary")+fixture("ghst-secondary-1"); router.feed(wire,wire.size)
            val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
            val point=doc.getElementsByTagName("point").item(0).attributes
            assertEquals("9999999.00",point.getNamedItem("hae").nodeValue)
            assertEquals("-87.5",point.getNamedItem("lon").nodeValue)
            assertEquals("J's & drone",doc.getElementsByTagName("contact").item(0).attributes.getNamedItem("callsign").nodeValue)
        } finally { Locale.setDefault(locale) }
    }

    @Test fun cotRejectsStaleOrInvalidPosition() {
        val good=GpsPosition(0.0,-87.5,0.0,1.0,90.0,3,12,1.0)
        assertTrue(good.hasValidFix)
        for (bad in listOf(good.copy(timestampMs=0),good.copy(lat=91.0),good.copy(fixType=0))) {
            assertThrows(IllegalArgumentException::class.java) { CotFormatter.buildDroneSA("test","test",bad) }
        }
    }

    @Test fun mspRejectsOversizedV2AndEchoedRequests() {
        val parser=MspGpsParser(); var count=0; parser.onGpsPosition={count++}
        val oversize=byteArrayOf(36,88,62,0,106,0,-1,127)
        parser.feed(oversize,oversize.size)
        val request=MspGpsParser.MSP_REQUEST_RAW_GPS; parser.feed(request,request.size)
        assertEquals(0,count)
    }
}
