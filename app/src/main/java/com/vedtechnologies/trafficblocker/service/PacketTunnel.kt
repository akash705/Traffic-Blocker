package com.vedtechnologies.trafficblocker.service

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.IOException

/**
 * Drop-only packet tunnel. Reads all packets from the VPN tun interface
 * and discards them. Uses blocking reads so the thread waits efficiently
 * for each packet. To stop, close the ParcelFileDescriptor — this
 * interrupts the blocking read and the loop exits.
 */
class PacketTunnel(
    private val vpnFd: ParcelFileDescriptor
) {
    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun start() {
        running = true
        thread = Thread({
            Log.d(TAG, "PacketTunnel started — dropping all packets")
            val inputStream = FileInputStream(vpnFd.fileDescriptor)
            val buffer = ByteArray(MAX_PACKET_SIZE)

            try {
                while (running) {
                    val length = inputStream.read(buffer)
                    if (length < 0) {
                        // FD closed
                        break
                    }
                    // Drop — do nothing. Every packet is silently discarded.
                }
            } catch (e: IOException) {
                // Expected when vpnFd is closed to stop the tunnel
                if (running) {
                    Log.e(TAG, "PacketTunnel read error", e)
                }
            }
            Log.d(TAG, "PacketTunnel stopped")
        }, "PacketTunnel-drain").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    companion object {
        private const val TAG = "PacketTunnel"
        private const val MAX_PACKET_SIZE = 32767
    }
}
