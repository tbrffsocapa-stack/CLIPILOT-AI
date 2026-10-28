package com.tbr.clipilot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeminiClient(private val apiKey: String) {
    suspend fun createPlan(command: String, durationMs: Long): EditPlan = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Adiciona tua chave da API Gemini nas configurações." }
        val instruction = """
            Você é o planejador de edição do Clipilot. Receberá um comando em português e a duração de um vídeo em milissegundos.
            Responda SOMENTE um objeto JSON válido, sem markdown, com os campos:
            {"summary":"resumo curto em português","start_ms":0,"end_ms":null,"mute_audio":false,"grayscale":false}
            start_ms e end_ms definem o trecho a manter; end_ms null significa até o fim. Use milissegundos e respeite duração=$durationMs.
            mute_audio só true se pedirem para remover/silenciar o áudio. grayscale só true se pedirem preto e branco.
            Não invente operações que o app não suporta. Se o pedido pedir algo não suportado, explique brevemente no summary e ainda devolva valores seguros.
            Comando do usuário: $command
        """.trimIndent()
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction)))))
            .put("generationConfig", JSONObject().put("temperature", 0.1).put("responseMimeType", "application/json"))
        val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey").openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 45000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("Erro da API ($code): ${response.take(500)}")
            val text = JSONObject(response).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            EditPlan.fromJson(text).validate(durationMs)
        } finally { conn.disconnect() }
    }
}
