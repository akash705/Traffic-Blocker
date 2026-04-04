package com.akash.apptrafficblocker.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class BlocklistRepository(
    private val context: Context,
    private val dao: BlocklistDao
) {

    val allSources: Flow<List<BlocklistSource>> = dao.getAll()

    private val blocklistDir: File
        get() = File(context.filesDir, "blocklists").also { it.mkdirs() }

    suspend fun addBlocklist(url: String, name: String): BlocklistSource {
        val source = BlocklistSource(url = url, name = name)
        val id = dao.insert(source).toInt()
        val inserted = source.copy(id = id)
        refreshBlocklist(inserted)
        return dao.getById(id) ?: inserted
    }

    suspend fun removeBlocklist(id: Int) {
        dao.deleteById(id)
        getFileForSource(id).delete()
    }

    suspend fun toggleBlocklist(id: Int) {
        val source = dao.getById(id) ?: return
        dao.update(source.copy(enabled = !source.enabled))
    }

    suspend fun refreshBlocklist(source: BlocklistSource) {
        try {
            val domains = downloadDomains(source.url)
            val file = getFileForSource(source.id)
            withContext(Dispatchers.IO) {
                file.writeText(domains.joinToString("\n"))
            }
            dao.update(
                source.copy(
                    domainCount = domains.size,
                    lastUpdated = System.currentTimeMillis(),
                    lastError = null
                )
            )
            Log.d(TAG, "Refreshed ${source.name}: ${domains.size} domains")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh ${source.name}", e)
            dao.update(source.copy(lastError = e.message))
            throw e
        }
    }

    suspend fun refreshAll() {
        val sources = dao.getEnabled()
        for (source in sources) {
            try {
                refreshBlocklist(source)
            } catch (_: Exception) {
                // Error already saved to DB in refreshBlocklist
            }
        }
    }

    suspend fun loadBlockedDomains(): Set<String> = withContext(Dispatchers.IO) {
        val domains = HashSet<String>(500_000)
        val enabledSources = dao.getEnabled()

        for (source in enabledSources) {
            val file = getFileForSource(source.id)
            if (!file.exists()) continue

            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        domains.add(trimmed)
                    }
                }
            }
        }

        Log.d(TAG, "Loaded ${domains.size} blocked domains from ${enabledSources.size} sources")
        domains
    }

    private suspend fun downloadDomains(urlString: String): List<String> =
        withContext(Dispatchers.IO) {
            val domains = mutableListOf<String>()
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.requestMethod = "GET"

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
                }

                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.forEachLine { line ->
                        val domain = parseDomainLine(line)
                        if (domain != null) {
                            domains.add(domain)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            domains
        }

    private fun parseDomainLine(line: String): String? {
        val trimmed = line.trim()

        // Skip empty lines and comments
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return null
        }

        // Handle hosts-file format: "0.0.0.0 domain.com" or "127.0.0.1 domain.com"
        val parts = trimmed.split("\\s+".toRegex())
        val domain = if (parts.size >= 2 &&
            (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")
        ) {
            parts[1]
        } else if (parts.size == 1) {
            // Plain domain list format (one domain per line)
            parts[0]
        } else {
            return null
        }

        // Strip www. prefix for normalized matching
        val normalized = domain.lowercase().removePrefix("www.")

        // Basic validation: must contain a dot and no path separators
        if (!normalized.contains('.') || normalized.contains('/')) {
            return null
        }

        return normalized
    }

    private fun getFileForSource(id: Int): File = File(blocklistDir, "$id.txt")

    companion object {
        private const val TAG = "BlocklistRepository"
    }
}
