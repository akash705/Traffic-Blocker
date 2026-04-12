package com.akash.apptrafficblocker.service

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.akash.apptrafficblocker.data.PrefsManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * DNS-level domain blocker with per-app blocking modes.
 *
 * Uses /proc/net/udp UID lookup to identify which app sent each DNS query:
 * - "block_all" apps: ALL DNS queries return 0.0.0.0 (no internet)
 * - "block_domains" apps: only blocklisted domains return 0.0.0.0
 */
class DnsPacketProxy(
    private val vpnFd: ParcelFileDescriptor,
    private val blockedDomains: Set<String>,
    private val vpnService: VpnService,
    private val appBlockingModes: Map<String, String>,
    private val context: Context,
    private val backgroundBlockingApps: Set<String> = emptySet(),
    private val upstreamDns: InetAddress = InetAddress.getByName("8.8.8.8"),
    private val onDnsQuery: ((domain: String, packageName: String?, blocked: Boolean, queryType: String) -> Unit)? = null
) {
    @Volatile
    private var running = false

    /** Updated by BlockerService when the foreground app changes */
    @Volatile
    var foregroundPackage: String? = null
    private var thread: Thread? = null

    // Cache UID -> package name lookups
    private val uidToPackage = HashMap<Int, String?>()

    // If all apps have the same mode, skip UID lookup entirely
    private val uniformMode: String? = run {
        val modes = appBlockingModes.values.toSet()
        if (modes.size == 1) modes.first() else null
    }

    // When UID lookup fails with mixed modes, use the most restrictive
    private val fallbackMode: String = if (appBlockingModes.values.any { it == PrefsManager.MODE_BLOCK_ALL }) {
        PrefsManager.MODE_BLOCK_ALL
    } else {
        PrefsManager.MODE_BLOCK_DOMAINS
    }

    fun start() {
        running = true
        // Pre-populate UID cache for target apps
        for (pkg in appBlockingModes.keys) {
            try {
                val uid = context.packageManager.getApplicationInfo(pkg, 0).uid
                uidToPackage[uid] = pkg
            } catch (_: Exception) {}
        }

        thread = Thread({
            Log.d(TAG, "DnsPacketProxy started — ${blockedDomains.size} domains in blocklist, ${appBlockingModes.size} apps configured")
            val input = FileInputStream(vpnFd.fileDescriptor)
            val output = FileOutputStream(vpnFd.fileDescriptor)
            val buffer = ByteArray(MAX_PACKET_SIZE)

            try {
                while (running) {
                    val length = input.read(buffer)
                    if (length < 0) break
                    if (length == 0) continue

                    try {
                        handlePacket(buffer, length, output)
                    } catch (e: Exception) {
                        if (running) {
                            Log.w(TAG, "Error handling packet", e)
                        }
                    }
                }
            } catch (e: IOException) {
                if (running) {
                    Log.e(TAG, "DnsPacketProxy read error", e)
                }
            }
            Log.d(TAG, "DnsPacketProxy stopped")
        }, "DnsPacketProxy").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun handlePacket(packet: ByteArray, length: Int, output: FileOutputStream) {
        if (length < 20) return

        val version = (packet[0].toInt() and 0xF0) shr 4
        if (version != 4) return // Only handle IPv4

        val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
        if (length < ipHeaderLength + 8) return

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return // Not UDP

        val udpOffset = ipHeaderLength
        val srcPort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or
                (packet[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or
                (packet[udpOffset + 3].toInt() and 0xFF)

        if (dstPort != 53) return // Not DNS

        val dnsOffset = udpOffset + 8
        if (length < dnsOffset + 12) return

        val domain = parseDnsName(packet, dnsOffset + 12, length) ?: return
        val normalizedDomain = domain.lowercase().removePrefix("www.")

        // Extract query type (A=1, AAAA=28) from the question section
        val questionEnd = findQuestionEnd(packet, dnsOffset + 12, length)
        val queryType = if (questionEnd != null && questionEnd >= dnsOffset + 12 + 4) {
            // Query type is 2 bytes right after the QNAME (before QCLASS)
            val typeOffset = questionEnd - 4
            ((packet[typeOffset].toInt() and 0xFF) shl 8) or (packet[typeOffset + 1].toInt() and 0xFF)
        } else {
            1 // Default to Type A
        }

        // Determine blocking mode and source app for the source port
        val sourceInfo = getSourceInfoForPort(srcPort)
        val blockingMode = sourceInfo.first
        val sourcePackage = sourceInfo.second

        // Block background data: if the app has background blocking enabled
        // and it's NOT currently in the foreground, block all its DNS queries
        val isInBackground = sourcePackage != null
                && sourcePackage in backgroundBlockingApps
                && foregroundPackage != sourcePackage

        val shouldBlock = when {
            isInBackground -> true // Block ALL DNS for background apps
            blockingMode == PrefsManager.MODE_BLOCK_ALL -> true
            blockingMode == PrefsManager.MODE_BLOCK_DOMAINS -> isDomainBlocked(normalizedDomain)
            else -> isDomainBlocked(normalizedDomain)
        }

        val queryTypeStr = when (queryType) {
            DNS_TYPE_A -> "A"
            DNS_TYPE_AAAA -> "AAAA"
            else -> "OTHER"
        }

        if (shouldBlock) {
            Log.d(TAG, "Blocking DNS: $domain (type=$queryType, mode=$blockingMode)")
            onDnsQuery?.invoke(domain, sourcePackage, true, queryTypeStr)
            val response = buildBlockedResponse(packet, length, ipHeaderLength, udpOffset, dnsOffset, queryType)
            if (response != null) {
                output.write(response)
            }
        } else {
            onDnsQuery?.invoke(domain, sourcePackage, false, queryTypeStr)
            forwardDnsQuery(packet, length, ipHeaderLength, udpOffset, dnsOffset, output)
        }
    }

    /**
     * Look up which app owns the given source port via /proc/net/udp,
     * then return its blocking mode and package name.
     *
     * Optimizations:
     * - If all apps share the same mode, skips UID lookup entirely
     * - If UID lookup fails (common on Android 10+), falls back to most restrictive mode
     */
    private fun getSourceInfoForPort(srcPort: Int): Pair<String, String?> {
        // Fast path: all apps have the same mode, no need for UID lookup
        uniformMode?.let {
            // Still try to resolve package for logging
            val uid = getUidForPort(srcPort)
            val pkg = if (uid >= 0) uidToPackage.getOrPut(uid) {
                context.packageManager.getNameForUid(uid)
            } else null
            return it to pkg
        }

        val uid = getUidForPort(srcPort)
        if (uid < 0) return fallbackMode to null

        val pkg = uidToPackage.getOrPut(uid) {
            context.packageManager.getNameForUid(uid)
        }
        if (pkg == null) return fallbackMode to null

        return (appBlockingModes[pkg] ?: PrefsManager.MODE_BLOCK_ALL) to pkg
    }

    /**
     * Read /proc/net/udp and /proc/net/udp6 to find the UID owning the given local port.
     */
    private fun getUidForPort(port: Int): Int {
        try {
            val uid = searchProcNet("/proc/net/udp", port)
            if (uid >= 0) return uid
            return searchProcNet("/proc/net/udp6", port)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read /proc/net/udp", e)
        }
        return -1
    }

    private fun searchProcNet(path: String, targetPort: Int): Int {
        val file = File(path)
        if (!file.exists()) return -1

        file.bufferedReader().useLines { lines ->
            for (line in lines) {
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size < 8) continue

                val localAddr = parts[1]
                val colonIdx = localAddr.lastIndexOf(':')
                if (colonIdx < 0) continue

                val portHex = localAddr.substring(colonIdx + 1)
                val localPort = portHex.toIntOrNull(16) ?: continue

                if (localPort == targetPort) {
                    return parts[7].toIntOrNull() ?: -1
                }
            }
        }
        return -1
    }

    private fun isDomainBlocked(domain: String): Boolean {
        // Block known DoH/DoT providers to prevent DNS bypass via encrypted DNS
        if (domain in DOH_DOMAINS) return true

        if (domain in blockedDomains) return true

        // Check parent domains
        var d = domain
        while (true) {
            val dot = d.indexOf('.')
            if (dot < 0 || dot == d.length - 1) break
            d = d.substring(dot + 1)
            if (d in blockedDomains) return true
        }
        return false
    }

    private fun parseDnsName(packet: ByteArray, offset: Int, length: Int): String? {
        val sb = StringBuilder()
        var pos = offset

        while (pos < length) {
            val labelLen = packet[pos].toInt() and 0xFF
            if (labelLen == 0) break
            if (labelLen >= 0xC0) return null

            pos++
            if (pos + labelLen > length) return null

            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until labelLen) {
                sb.append(packet[pos + i].toInt().toChar())
            }
            pos += labelLen
        }

        return if (sb.isNotEmpty()) sb.toString() else null
    }

    /**
     * Build a DNS response that blocks the queried domain.
     *
     * Returns 0.0.0.0 for A queries, :: for AAAA queries, and NXDOMAIN for
     * any other type. This ensures both IPv4 and IPv6 resolution is blocked.
     */
    private fun buildBlockedResponse(
        originalPacket: ByteArray,
        originalLength: Int,
        ipHeaderLength: Int,
        udpOffset: Int,
        dnsOffset: Int,
        queryType: Int = 1
    ): ByteArray? {
        val srcIp = ByteArray(4)
        val dstIp = ByteArray(4)
        System.arraycopy(originalPacket, 12, srcIp, 0, 4)
        System.arraycopy(originalPacket, 16, dstIp, 0, 4)

        val srcPort = ((originalPacket[udpOffset].toInt() and 0xFF) shl 8) or
                (originalPacket[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((originalPacket[udpOffset + 2].toInt() and 0xFF) shl 8) or
                (originalPacket[udpOffset + 3].toInt() and 0xFF)

        val questionEnd = findQuestionEnd(originalPacket, dnsOffset + 12, originalLength)
            ?: return null

        val questionBytes = questionEnd - (dnsOffset + 12)

        // Build answer section based on query type
        val answerBytes: ByteArray? = when (queryType) {
            DNS_TYPE_A -> {
                // Return 0.0.0.0 for A queries
                val answer = ByteArray(16)
                val aBuf = ByteBuffer.wrap(answer)
                aBuf.putShort(0xC00C.toShort()) // Name pointer
                aBuf.putShort(DNS_TYPE_A.toShort())
                aBuf.putShort(1)  // Class IN
                aBuf.putInt(3600) // TTL — cache the block for 1 hour
                aBuf.putShort(4)  // RDLENGTH
                aBuf.put(0); aBuf.put(0); aBuf.put(0); aBuf.put(0) // 0.0.0.0
                answer
            }
            DNS_TYPE_AAAA -> {
                // Return :: (all-zeros IPv6) for AAAA queries
                val answer = ByteArray(28)
                val aaaaBuf = ByteBuffer.wrap(answer)
                aaaaBuf.putShort(0xC00C.toShort()) // Name pointer
                aaaaBuf.putShort(DNS_TYPE_AAAA.toShort())
                aaaaBuf.putShort(1)  // Class IN
                aaaaBuf.putInt(3600) // TTL
                aaaaBuf.putShort(16) // RDLENGTH (IPv6 = 16 bytes)
                // 16 bytes of zeros = ::
                for (i in 0 until 16) aaaaBuf.put(0)
                answer
            }
            else -> null // NXDOMAIN for other types (HTTPS, SVCB, etc.)
        }

        val answerCount: Short = if (answerBytes != null) 1 else 0
        val dnsResponseSize = 12 + questionBytes + (answerBytes?.size ?: 0)
        val dns = ByteArray(dnsResponseSize)
        val buf = ByteBuffer.wrap(dns)

        // DNS header
        buf.put(originalPacket[dnsOffset])     // Transaction ID high
        buf.put(originalPacket[dnsOffset + 1]) // Transaction ID low
        // Flags: QR=1 (response), AA=1 (authoritative), RCODE=0 if answer, 3 (NXDOMAIN) if no answer
        val flags: Short = if (answerBytes != null) 0x8580.toShort() else 0x8583.toShort()
        buf.putShort(flags)
        buf.putShort(1)           // QDCOUNT
        buf.putShort(answerCount) // ANCOUNT
        buf.putShort(0)           // NSCOUNT
        buf.putShort(0)           // ARCOUNT

        // Copy question section
        System.arraycopy(originalPacket, dnsOffset + 12, dns, 12, questionBytes)

        // Copy answer section if present
        if (answerBytes != null) {
            System.arraycopy(answerBytes, 0, dns, 12 + questionBytes, answerBytes.size)
        }

        // Build IP + UDP wrapper
        val udpLength = 8 + dnsResponseSize
        val ipTotalLength = 20 + udpLength
        val ip = ByteArray(ipTotalLength)
        val ipBuf = ByteBuffer.wrap(ip)

        ipBuf.put(0x45.toByte())
        ipBuf.put(0)
        ipBuf.putShort(ipTotalLength.toShort())
        ipBuf.putShort(0)
        ipBuf.putShort(0x4000.toShort())
        ipBuf.put(64)
        ipBuf.put(17)
        ipBuf.putShort(0)
        ipBuf.put(dstIp)
        ipBuf.put(srcIp)

        val checksum = computeIpChecksum(ip, 20)
        ip[10] = (checksum shr 8).toByte()
        ip[11] = (checksum and 0xFF).toByte()

        // UDP
        val udpBuf = ByteBuffer.wrap(ip, 20, udpLength)
        udpBuf.putShort(dstPort.toShort())
        udpBuf.putShort(srcPort.toShort())
        udpBuf.putShort(udpLength.toShort())
        udpBuf.putShort(0)
        System.arraycopy(dns, 0, ip, 28, dnsResponseSize)

        return ip
    }

    private fun findQuestionEnd(packet: ByteArray, nameStart: Int, length: Int): Int? {
        var pos = nameStart
        while (pos < length) {
            val labelLen = packet[pos].toInt() and 0xFF
            if (labelLen == 0) {
                pos++
                pos += 4
                return if (pos <= length) pos else null
            }
            if (labelLen >= 0xC0) {
                pos += 2
                pos += 4
                return if (pos <= length) pos else null
            }
            pos += 1 + labelLen
        }
        return null
    }

    private fun forwardDnsQuery(
        packet: ByteArray,
        length: Int,
        ipHeaderLength: Int,
        udpOffset: Int,
        dnsOffset: Int,
        output: FileOutputStream
    ) {
        val dnsPayloadLength = length - dnsOffset
        if (dnsPayloadLength <= 0) return

        val dnsQuery = ByteArray(dnsPayloadLength)
        System.arraycopy(packet, dnsOffset, dnsQuery, 0, dnsPayloadLength)

        val srcIp = ByteArray(4)
        val dstIp = ByteArray(4)
        System.arraycopy(packet, 12, srcIp, 0, 4)
        System.arraycopy(packet, 16, dstIp, 0, 4)

        val srcPort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or
                (packet[udpOffset + 1].toInt() and 0xFF)

        try {
            val socket = DatagramSocket()
            vpnService.protect(socket)

            val request = DatagramPacket(dnsQuery, dnsQuery.size, upstreamDns, 53)
            socket.soTimeout = 5000
            socket.send(request)

            val responseBuffer = ByteArray(MAX_PACKET_SIZE)
            val response = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(response)
            socket.close()

            val dnsResponseLength = response.length
            val udpLength = 8 + dnsResponseLength
            val ipTotalLength = 20 + udpLength
            val responsePacket = ByteArray(ipTotalLength)
            val buf = ByteBuffer.wrap(responsePacket)

            buf.put(0x45.toByte())
            buf.put(0)
            buf.putShort(ipTotalLength.toShort())
            buf.putShort(0)
            buf.putShort(0x4000.toShort())
            buf.put(64)
            buf.put(17)
            buf.putShort(0)
            buf.put(dstIp)
            buf.put(srcIp)

            val checksum = computeIpChecksum(responsePacket, 20)
            responsePacket[10] = (checksum shr 8).toByte()
            responsePacket[11] = (checksum and 0xFF).toByte()

            val udpBuf = ByteBuffer.wrap(responsePacket, 20, udpLength)
            udpBuf.putShort(53)
            udpBuf.putShort(srcPort.toShort())
            udpBuf.putShort(udpLength.toShort())
            udpBuf.putShort(0)

            System.arraycopy(responseBuffer, 0, responsePacket, 28, dnsResponseLength)

            output.write(responsePacket)
        } catch (e: Exception) {
            if (running) {
                Log.w(TAG, "Failed to forward DNS query", e)
            }
        }
    }

    private fun computeIpChecksum(header: ByteArray, length: Int): Int {
        var sum = 0L
        var i = 0
        while (i < length) {
            val word = ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    companion object {
        private const val TAG = "DnsPacketProxy"
        private const val MAX_PACKET_SIZE = 32767
        private const val DNS_TYPE_A = 1
        private const val DNS_TYPE_AAAA = 28

        // Known DoH/DoT providers — block their DNS resolution so browsers
        // can't bypass our port-53 interception via encrypted DNS.
        private val DOH_DOMAINS = setOf(
            "dns.google",
            "dns.google.com",
            "dns64.dns.google",
            "cloudflare-dns.com",
            "one.one.one.one",
            "1dot1dot1dot1.cloudflare-dns.com",
            "mozilla.cloudflare-dns.com",
            "dns.cloudflare.com",
            "security.cloudflare-dns.com",
            "family.cloudflare-dns.com",
            "dns.quad9.net",
            "dns9.quad9.net",
            "dns10.quad9.net",
            "dns11.quad9.net",
            "doh.opendns.com",
            "dns.adguard-dns.com",
            "dns-unfiltered.adguard.com",
            "dns-family.adguard.com",
            "dns.nextdns.io",
            "doh.cleanbrowsing.org",
            "doh.dns.sb",
            "dns.alidns.com",
            "doh.pub",
        )
    }
}
