package com.afterlater.data.metadata

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale

object HostSafety {

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun isAllowedHostname(host: String): Boolean {
        val normalized = host.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return false
        if (normalized == "localhost" ||
            normalized.endsWith(".localhost") ||
            normalized.endsWith(".local") ||
            normalized.endsWith(".internal") ||
            normalized.endsWith(".home.arpa") ||
            normalized == "[::1]"
        ) {
            return false
        }

        val literal = when {
            IPV4.matches(normalized) -> runCatching { InetAddress.getByName(normalized) }.getOrNull()
            normalized.contains(':') -> runCatching {
                InetAddress.getByName(normalized.removePrefix("[").removeSuffix("]"))
            }.getOrNull()
            else -> null
        }
        return literal?.let(IpSafety::isPublic) ?: true
    }
}

object SafeDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (!HostSafety.isAllowedHostname(hostname)) {
            throw UnknownHostException("Blocked host")
        }
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.isEmpty()) throw UnknownHostException(hostname)
        if (addresses.any { !IpSafety.isPublic(it) }) {
            throw UnknownHostException("Blocked address")
        }
        return addresses
    }
}
