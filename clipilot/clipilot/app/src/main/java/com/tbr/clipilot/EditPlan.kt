package com.tbr.clipilot

import org.json.JSONObject

data class EditPlan(
    val summary: String,
    val startMs: Long = 0,
    val endMs: Long? = null,
    val muteAudio: Boolean = false,
    val grayscale: Boolean = false
) {
    fun validate(durationMs: Long): EditPlan {
        require(durationMs > 0) { "Não consegui detectar a duração do vídeo." }
        val start = startMs.coerceIn(0, durationMs - 1)
        val end = (endMs ?: durationMs).coerceIn(start + 1, durationMs)
        return copy(startMs = start, endMs = end)
    }

    companion object {
        fun fromJson(raw: String): EditPlan {
            val cleaned = raw.substringAfter("```json", raw).substringAfter("```", raw).substringBeforeLast("```").trim()
            val json = JSONObject(cleaned)
            return EditPlan(
                summary = json.optString("summary", "Plano de edição pronto"),
                startMs = json.optLong("start_ms", 0L).coerceAtLeast(0),
                endMs = if (json.isNull("end_ms") || !json.has("end_ms")) null else json.optLong("end_ms"),
                muteAudio = json.optBoolean("mute_audio", false),
                grayscale = json.optBoolean("grayscale", false)
            )
        }
    }
}
