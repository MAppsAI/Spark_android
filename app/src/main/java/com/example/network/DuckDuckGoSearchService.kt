package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class SearchSnippet(
    val title: String,
    val snippet: String,
    val url: String
)

data class SearchResult(
    val query: String,
    val snippets: List<SearchSnippet>,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

class DuckDuckGoSearchService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Searches DuckDuckGo for top web results and snippets.
     * Uses DuckDuckGo HTML Lite & Instant Answer API.
     */
    suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext SearchResult(query, emptyList(), true)
        }

        val encodedQuery = try {
            URLEncoder.encode(trimmed, "UTF-8")
        } catch (_: Exception) {
            trimmed
        }

        val snippets = mutableListOf<SearchSnippet>()

        // 1. Try DuckDuckGo Instant Answer JSON API
        try {
            val apiUrl = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        val abstractText = json.optString("AbstractText", "")
                        val abstractSource = json.optString("AbstractSource", "DuckDuckGo")
                        val abstractUrl = json.optString("AbstractURL", "")

                        if (abstractText.isNotBlank()) {
                            snippets.add(
                                SearchSnippet(
                                    title = "$abstractSource: $trimmed",
                                    snippet = abstractText,
                                    url = abstractUrl.ifBlank { "https://duckduckgo.com/?q=$encodedQuery" }
                                )
                            )
                        }

                        // Related topics
                        val relatedTopics = json.optJSONArray("RelatedTopics")
                        if (relatedTopics != null) {
                            for (i in 0 until relatedTopics.length()) {
                                if (snippets.size >= 4) break
                                val topic = relatedTopics.optJSONObject(i) ?: continue
                                val text = topic.optString("Text", "")
                                val firstUrl = topic.optString("FirstURL", "")
                                if (text.isNotBlank()) {
                                    val title = text.take(60).substringBefore(" - ")
                                    snippets.add(
                                        SearchSnippet(
                                            title = title.ifBlank { "Result ${snippets.size + 1}" },
                                            snippet = text,
                                            url = firstUrl.ifBlank { "https://duckduckgo.com/?q=$encodedQuery" }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. If API returned few results, query DuckDuckGo HTML search for organic web pages
        if (snippets.size < 3) {
            try {
                val htmlUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"
                val request = Request.Builder()
                    .url(htmlUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        val parsed = parseDdgHtml(html)
                        for (item in parsed) {
                            if (snippets.none { it.url == item.url || it.title == item.title }) {
                                snippets.add(item)
                                if (snippets.size >= 5) break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (snippets.isNotEmpty()) {
            SearchResult(
                query = trimmed,
                snippets = snippets,
                isSuccess = true
            )
        } else {
            SearchResult(
                query = trimmed,
                snippets = emptyList(),
                isSuccess = false,
                errorMessage = "No web search results found on DuckDuckGo."
            )
        }
    }

    private fun parseDdgHtml(html: String): List<SearchSnippet> {
        val list = mutableListOf<SearchSnippet>()
        try {
            // Match result blocks: class="result__body" or class="result__snippet"
            val linkPattern = Pattern.compile(
                "<a[^>]+class=[\"']result__url[\"'][^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE
            )
            val snippetPattern = Pattern.compile(
                "<a[^>]+class=[\"']result__snippet[\"'][^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE
            )
            val titlePattern = Pattern.compile(
                "<a[^>]+class=[\"']result__title[\"'][^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE
            )

            val snippetMatcher = snippetPattern.matcher(html)
            val titleMatcher = titlePattern.matcher(html)

            while (snippetMatcher.find() && list.size < 5) {
                val rawSnippet = snippetMatcher.group(1) ?: continue
                val cleanSnippet = rawSnippet.replace("<[^>]+>".toRegex(), "").trim()
                var title = "Web Result ${list.size + 1}"
                if (titleMatcher.find()) {
                    title = titleMatcher.group(1)?.replace("<[^>]+>".toRegex(), "")?.trim() ?: title
                }
                list.add(
                    SearchSnippet(
                        title = title,
                        snippet = cleanSnippet,
                        url = "https://duckduckgo.com"
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
