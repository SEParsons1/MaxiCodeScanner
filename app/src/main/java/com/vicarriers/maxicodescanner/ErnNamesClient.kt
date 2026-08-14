package com.vicarriers.maxicodescanner

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

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

    fun nameForTracking(tracking: String): String? {
        val key = tracking.filter { !it.isWhitespace() }.uppercase()
        if (key.isEmpty()) return null
        runCatching { refresh() }
        return names[key]
    }

    private fun refresh() {
        if (token.isBlank() || owner.isBlank() || repo.isBlank()) return

        val connection = openConnection()
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return
            val body = readBody(connection, code)
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("ERN names fetch failed: HTTP $code")
            }
            names = parseTsv(body)
            etag = connection.getHeaderField("ETag")
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
    }
}
