package com.readassistant.core.llm.provider

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.readassistant.core.llm.api.*
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

class GeminiProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) : LlmProvider {

    override val providerType = ProviderType.GEMINI

    private fun baseUrl(config: LlmConfig) = config.baseUrl.ifBlank { "https://generativelanguage.googleapis.com" }.trimEnd('/')

    private fun buildBody(messages: List<LlmMessage>, config: LlmConfig): String {
        val body = JsonObject()
        val sys = messages.firstOrNull { it.role == "system" }
        if (sys != null) {
            val sysObj = JsonObject(); val parts = JsonArray(); val p = JsonObject()
            p.addProperty("text", sys.content); parts.add(p); sysObj.add("parts", parts)
            body.add("systemInstruction", sysObj)
        }
        val contents = JsonArray()
        messages.filter { it.role != "system" }.forEach { m ->
            val c = JsonObject(); c.addProperty("role", if (m.role == "assistant") "model" else "user")
            val parts = JsonArray(); val p = JsonObject(); p.addProperty("text", m.content)
            parts.add(p); c.add("parts", parts); contents.add(c)
        }
        body.add("contents", contents)
        val gc = JsonObject(); gc.addProperty("temperature", config.temperature); gc.addProperty("maxOutputTokens", config.maxTokens)
        body.add("generationConfig", gc)
        return gson.toJson(body)
    }

    override suspend fun sendMessage(messages: List<LlmMessage>, config: LlmConfig): String {
        val model = config.modelName.ifBlank { "gemini-2.0-flash" }
        val url = "${baseUrl(config)}/v1beta/models/$model:generateContent?key=${config.apiKey}"
        val request = Request.Builder().url(url).addHeader("Content-Type", "application/json")
            .post(buildBody(messages, config).toRequestBody("application/json".toMediaType())).build()
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: throw IOException("Empty response")
        if (!response.isSuccessful) throw IOException("API error ${response.code}: $body")
        val json = gson.fromJson(body, JsonObject::class.java)
        return json.getAsJsonArray("candidates")?.get(0)?.asJsonObject
            ?.getAsJsonObject("content")?.getAsJsonArray("parts")?.get(0)?.asJsonObject
            ?.get("text")?.asString ?: throw IOException("Invalid response format")
    }

    override fun streamMessage(messages: List<LlmMessage>, config: LlmConfig): Flow<LlmStreamChunk> = callbackFlow {
        val model = config.modelName.ifBlank { "gemini-2.0-flash" }
        val url = "${baseUrl(config)}/v1beta/models/$model:streamGenerateContent?alt=sse&key=${config.apiKey}"
        val request = Request.Builder().url(url).addHeader("Content-Type", "application/json")
            .post(buildBody(messages, config).toRequestBody("application/json".toMediaType())).build()
        val call = httpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { trySend(LlmStreamChunk.Error(e.message ?: "Network error")); close() }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) { trySend(LlmStreamChunk.Error("API error ${response.code}")); close(); return }
                try {
                    val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line!!
                        if (l.startsWith("data: ")) {
                            try {
                                val j = gson.fromJson(l.removePrefix("data: "), JsonObject::class.java)
                                val text = j.getAsJsonArray("candidates")?.get(0)?.asJsonObject
                                    ?.getAsJsonObject("content")?.getAsJsonArray("parts")?.get(0)?.asJsonObject
                                    ?.get("text")?.asString
                                if (text != null) trySend(LlmStreamChunk.Delta(text))
                            } catch (_: Exception) {}
                        }
                    }
                    trySend(LlmStreamChunk.Done); reader.close()
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
