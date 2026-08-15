package com.vicarriers.maxicodescanner

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Live 1Z → consignee lookup from the private GitHub list the ERN publisher updates.
 */
class ErnNamesClient(
    private val owner: String = BuildConfig.ERN_NAMES_OWNER,
    private val repo: String = BuildConfig.ERN_NAMES_REPO,
    private val token: String = BuildConfig.ERN_NAMES_TOKEN,
    private val branch: String = "main",
    private val path: String = "ern_names.tsv",
) {
    private var etag: String? = null
    private var names: Map<String, String> = emptyMap()

    fun prefetch() {
        runCatching { refresh() }
    }

    fun nameForTracking(tracking: String): String? {
        val keys = lookupKeys(tracking)
        if (keys.isEmpty()) return null
        runCatching { refresh() }
        val found = keys.firstNotNullOfOrNull { names[it] }
        Log.i(TAG, "lookup rows=${names.size} hit=${found != null}")
        return found
    }

    private fun refresh() {
        if (token.isBlank() || owner.isBlank() || repo.isBlank()) {
            Log.w(TAG, "refresh skipped: missing credentials")
            return
        }

        val connection = openConnection()
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                Log.i(TAG, "refresh not-modified rows=${names.size}")
                return
            }
            val body = readBody(connection, code)
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("ERN names fetch failed: HTTP $code")
            }
            val parsed = parseResponse(body)
            if (parsed.isEmpty()) {
                throw IOException("ERN names fetch returned no rows")
            }
            names = parsed
            etag = connection.getHeaderField("ETag")
            Log.i(TAG, "refresh http=$code rows=${names.size}")
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(): HttpURLConnection {
        val url = URL("https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/vnd.github.raw")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "MaxiCodeScanner-ErnNames")
        etag?.let { connection.setRequestProperty("If-None-Match", it) }
        return connection
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    companion object {
        private const val TAG = "ErnNames"

        private val serviceAliases = mapOf(
            "02" to "DK",
            "DK" to "02",
            "93" to "D9",
            "D9" to "93",
            "99" to "DG",
            "DG" to "99",
        )

        fun lookupKeys(tracking: String): List<String> {
            val compact = tracking.filter { !it.isWhitespace() }.uppercase()
            if (compact.isEmpty()) return emptyList()
            val keys = linkedSetOf(compact)
            if (compact.length == 18 && compact.startsWith("1Z")) {
                val account = compact.substring(2, 8)
                val service = compact.substring(8, 10)
                val tail = compact.substring(10)
                serviceAliases[service]?.let { keys += "1Z$account$it$tail" }
            }
            if (compact.length > 11) {
                keys += compact.takeLast(11)
            }
            return keys.toList()
        }

        fun parseResponse(body: String): Map<String, String> {
            val tsv = parseTsv(body)
            if (tsv.isNotEmpty()) return tsv
            val decoded = decodeGithubContentsJson(body) ?: return emptyMap()
            return parseTsv(decoded)
        }

        fun parseTsv(text: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for (line in text.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("tracking\t", ignoreCase = true)) {
                    continue
                }
                val tab = trimmed.indexOf('\t')
                if (tab <= 0) continue
                val tracking = trimmed.substring(0, tab).filter { !it.isWhitespace() }.uppercase()
                val name = trimmed.substring(tab + 1).trim()
                if (tracking.isNotEmpty() && name.isNotEmpty()) {
                    out[tracking] = name
                }
            }
            return out
        }

        fun decodeGithubContentsJson(body: String): String? {
            val marker = "\"content\""
            val start = body.indexOf(marker)
            if (start < 0) return null
            val colon = body.indexOf(':', start + marker.length)
            val quote = body.indexOf('"', colon + 1)
            if (colon < 0 || quote < 0) return null
            val encoded = StringBuilder()
            var i = quote + 1
            while (i < body.length) {
                val ch = body[i++]
                when (ch) {
                    '"' -> break
                    '\\' -> {
                        if (i >= body.length) break
                        when (val escaped = body[i++]) {
                            'n', 'r', 't' -> Unit
                            else -> encoded.append(escaped)
                        }
                    }
                    else -> encoded.append(ch)
                }
            }
            if (encoded.isEmpty()) return null
            return runCatching {
                String(Base64.getDecoder().decode(encoded.toString()), StandardCharsets.UTF_8)
            }.getOrNull()
        }
    }
}
