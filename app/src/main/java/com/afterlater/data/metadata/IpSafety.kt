package com.afterlater.data.metadata

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Rejects loopback, private, link-local, carrier-grade NAT, multicast, documentation,
 * and other reserved addresses. This is the SSRF gate used both by the HTML fetcher
 * and by the image loader, so a hostile page cannot make the phone probe its own LAN.
 */
object IpSafety {

    fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }

        val bytes = address.address
        return when (address) {
            is Inet4Address -> isPublicIpv4(bytes)
            is Inet6Address -> isPublicIpv6(bytes)
            else -> false
        }
    }

    private fun isPublicIpv4(bytes: ByteArray): Boolean {
        if (bytes.size != 4) return false
        val a = bytes[0].toInt() and 0xff
        val b = bytes[1].toInt() and 0xff
        val c = bytes[2].toInt() and 0xff

        return when {
            a == 0 || a == 10 || a == 127 -> false
            a == 100 && b in 64..127 -> false
            a == 169 && b == 254 -> false
            a == 172 && b in 16..31 -> false
            a == 192 && b == 0 && c in 0..2 -> false
            a == 192 && b == 88 && c == 99 -> false
            a == 192 && b == 168 -> false
            a == 198 && (b == 18 || b == 19) -> false
            a == 198 && b == 51 && c == 100 -> false
            a == 203 && b == 0 && c == 113 -> false
            a >= 224 -> false
            else -> true
        }
    }

    private fun isPublicIpv6(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return false

        val mappedIpv4 = bytes.take(10).all { it == 0.toByte() } &&
            bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()
        if (mappedIpv4) return isPublicIpv4(bytes.copyOfRange(12, 16))

        val compatibleIpv4 = bytes.take(12).all { it == 0.toByte() }
        if (compatibleIpv4) return isPublicIpv4(bytes.copyOfRange(12, 16))

        val b0 = bytes[0].toInt() and 0xff
        val b1 = bytes[1].toInt() and 0xff
        val b2 = bytes[2].toInt() and 0xff
        val b3 = bytes[3].toInt() and 0xff

        return when {
            (b0 and 0xfe) == 0xfc -> false
            b0 == 0xff -> false
            b0 == 0x20 && b1 == 0x01 && b2 == 0x0d && b3 == 0xb8 -> false
            b0 == 0x20 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00 -> false
            b0 == 0x20 && b1 == 0x02 -> false
            b0 == 0x00 && b1 == 0x64 && b2 == 0xff && b3 == 0x9b -> false
            else -> true
        }
    }
}
