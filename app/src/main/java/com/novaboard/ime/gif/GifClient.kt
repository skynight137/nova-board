package com.novaboard.ime.gif

import com.novaboard.ime.BuildConfig
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import org.json.JSONObject

data class GifItem(
    val slug: String,
    val title: String,
    val previewUrl: String,
    val contentUrl: String,
)

class GifClient {
    fun load(query: String): List<GifItem> {
        val key = BuildConfig.KLIPY_API_KEY.trim()
        if (key.isEmpty()) throw GifClientException.NotConfigured
        val endpoint =
            if (query.isBlank()) {
                "/api/v1/$key/gifs/trending"
            } else {
                "/api/v1/$key/gifs/search?q=${URLEncoder.encode(query.trim(), "UTF-8")}"
            }
        val connection =
            (URL("https://api.klipy.com$endpoint").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
        try {
            if (connection.responseCode !in 200..299) {
                throw GifClientException.HttpFailure(connection.responseCode)
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): List<GifItem> {
        val root = JSONObject(body)
        if (!root.optBoolean("result")) throw GifClientException.InvalidResponse
        val data = root.optJSONObject("data") ?: return emptyList()
        val items = data.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val file = item.optJSONObject("file") ?: continue
                val medium = file.optJSONObject("md") ?: file.optJSONObject("sm") ?: continue
                val preview =
                    medium.optJSONObject("webp")?.optString("url").orEmpty().ifBlank {
                        medium.optJSONObject("jpg")?.optString("url").orEmpty()
                    }
                val content = medium.optJSONObject("gif")?.optString("url").orEmpty()
                if (item.optString("slug").isNotBlank() && preview.isNotBlank() && content.isNotBlank()) {
                    add(
                        GifItem(
                            slug = item.optString("slug"),
                            title = item.optString("title").ifBlank { "GIF" },
                            previewUrl = preview,
                            contentUrl = content,
                        ),
                    )
                }
            }
        }
    }
}

sealed class GifClientException : Exception() {
    data object NotConfigured : GifClientException()
    data object InvalidResponse : GifClientException()
    data class HttpFailure(val code: Int) : GifClientException()
}
