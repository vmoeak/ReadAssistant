package com.readassistant.core.llm.provider

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.readassistant.core.llm.api.*
import com.readassistant.core.llm.streaming.SseParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject

class OpenAiProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : LlmProvider {

    override val providerType = ProviderType.OPENAI

    private fun baseUrl(config: LlmConfig) = config.baseUrl.ifBlank { "https://api.openai.com" }.trimEnd('/')

    private fun buildBody(messages: List<LlmMessage>, config: LlmConfig, stream: Boolean): String {
        val body = JsonObject()
        body.addProperty("model", config.modelName.ifBlank { "gpt-4o" })
        body.addProperty("temperature", config.temperature)
        body.addProperty("max_tokens", config.maxTokens)
        body.addProperty("stream", stream)
        val arr = JsonArray()
        messages.forEach { m ->
            val o = JsonObject(); o.addProperty("role", m.role); o.addProperty("content", m.content); arr.add(o)
        }
        body.add("messages", arr)
        return gson.toJson(body)
    }

    private fun buildRequest(config: LlmConfig, jsonBody: String): Request {
        return Request.Builder()
            .url("${baseUrl(config)}/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    override suspend fun sendMessage(messages: List<LlmMessage>, config: LlmConfig): String {
        val request = buildRequest(config, buildBody(messages, config, false))
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: throw IOException("Empty response")
        if (!response.isSuccessful) throw IOException("API error ${response.code}: $body")
        val json = gson.fromJson(body, JsonObject::class.java)
        return json.getAsJsonArray("choices")?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")?.get("content")?.asString
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
                        if (event != null && event.data.isNotEmpty()) {
                            if (event.data == "[DONE]") { trySend(LlmStreamChunk.Done); break }
                            try {
                                val j = gson.fromJson(event.data, JsonObject::class.java)
                                val content = j.getAsJsonArray("choices")?.get(0)?.asJsonObject
                                    ?.getAsJsonObject("delta")?.get("content")?.asString
                                if (content != null) trySend(LlmStreamChunk.Delta(content))
                            } catch (_: Exception) {}
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
