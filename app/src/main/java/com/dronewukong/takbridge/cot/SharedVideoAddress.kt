package com.dronewukong.takbridge.cot

import java.net.URI

/** A deliberately public address: CoT recipients receive the entire value. Never accepts userinfo. */
object SharedVideoAddress {
    fun validate(text: String): String {
        val value = text.trim()
        require(value.length in 1..2048 && value.none { it.isWhitespace() || it.code < 32 }) {
            "Enter a complete RTSP address without spaces"
        }
        val uri = try { URI(value) } catch (_: Exception) {
            throw IllegalArgumentException("Invalid RTSP address")
        }
        require(uri.scheme.equals("rtsp", true) && !uri.host.isNullOrBlank() &&
            (uri.port == -1 || uri.port in 1..65535)) { "Use rtsp://host:port/path" }
        require(uri.rawUserInfo == null) { "Do not share usernames or passwords in a video address" }
        require(uri.rawFragment == null) { "Remove the address fragment" }
        // Do not resolve DNS or probe the camera merely because a setting was edited.
        val host = uri.host.removePrefix("[").removeSuffix("]").lowercase()
        require(host != "localhost" && host != "::1" && host != "::" && host != "0.0.0.0" &&
            !host.startsWith("127.") && !host.endsWith(".localhost")) { "Use an address reachable by other TAK devices" }
        return "rtsp" + value.substring(value.indexOf(':'))
    }
}
