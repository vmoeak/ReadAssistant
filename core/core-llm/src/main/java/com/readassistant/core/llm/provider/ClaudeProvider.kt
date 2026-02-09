package com.readassistant.core.llm.provider

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.readassistant.core.llm.api.*
import com.readassistant.core.llm.streaming.SseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject

class ClaudeProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : LlmProvider {

    override val providerType = ProviderType.CLAUDE

    private fun baseUrl(config: LlmConfig) = config.baseUrl.ifBlank { "https://api.anthropic.com" }.trimEnd('/')

    private fun buildBody(messages: List<LlmMessage>, config: LlmConfig, stream: Boolean): String {
        val body = JsonObject()
        body.addProperty("model", config.modelName.ifBlank { "claude-sonnet-4-5-20250929" })
        body.addProperty("max_tokens", config.maxTokens)
        body.addProperty("stream", stream)
        val sys = messages.firstOrNull { it.role == "system" }
        if (sys != null) body.addProperty("system", sys.content)
        val arr = JsonArray()
        messages.filter { it.role != "system" }.forEach { m ->
            val o = JsonObject(); o.addProperty("role", m.role); o.addProperty("content", m.content); arr.add(o)
        }
        body.add("messages", arr)
        return gson.toJson(body)
    }

    private fun buildRequest(config: LlmConfig, jsonBody: String): Request {
        return Request.Builder()
            .url("${baseUrl(config)}/v1/messages")
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    override suspend fun sendMessage(messages: List<LlmMessage>, config: LlmConfig): String = withContext(Dispatchers.IO) {
        val request = buildRequest(config, buildBody(messages, config, false))
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: throw IOException("Empty response")
        if (!response.isSuccessful) throw IOException("API error ${response.code}: $body")
        val json = gson.fromJson(body, JsonObject::class.java)
        json.getAsJsonArray("content")?.get(0)?.asJsonObject?.get("text")?.asString
            ?: throw IOException("Invalid response format")
    }

    override fun streamMessage(messages: List<LlmMessage>, config: LlmConfig): Flow<LlmStreamChunk> = callbackFlow {
        val request = buildRequest(config, buildBody(messages, config, true))
        val parser = SseParser()
        val call = httpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(LlmStreamChunk.Error(e.message ?: "Network error")); close()
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) { trySend(LlmStreamChunk.Error("API error ${response.code}")); close(); return }
                try {
                    val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val event = parser.parse(line!!)
                        if (event != null) {
                            when (event.event) {
                                "content_block_delta" -> {
                                    try {
                                        val j = gson.fromJson(event.data, JsonObject::class.java)
                                        val text = j.getAsJsonObject("delta")?.get("text")?.asString
                                        if (text != null) trySend(LlmStreamChunk.Delta(text))
                                    } catch (_: Exception) {}
                                }
                                "message_stop" -> trySend(LlmStreamChunk.Done)
                            }
                        }
                    }
                    reader.close()
                } catch (e: Exception) { trySend(LlmStreamChunk.Error(e.message ?: "Stream error")) }
                close()
            }
        })
        awaitClose { call.cancel() }
    }

    override suspend fun testConnection(config: LlmConfig): Boolean = try {
        sendMessage(listOf(LlmMessage("user", "Hi")), config.copy(maxTokens = 10)); true
    } catch (_: Exception) { false }
}
