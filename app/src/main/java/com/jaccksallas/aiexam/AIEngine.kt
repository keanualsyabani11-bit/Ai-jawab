// language: Kotlin | path: app/src/main/java/com/jaccksallas/aiexam/AIEngine.kt
// runtime: Android API 26+ | target: Android device

package com.jaccksallas.aiexam

import com.jaccksallas.aiexam.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ParsedQuestion(
    val questionText: String,
    val choices: Map<String, String>,   // "A" -> "teks pilihan"
    val isEssay: Boolean
)

data class AIResult(
    val answer: String,         // "B" untuk pilgan, teks untuk essay
    val explanation: String,
    val confidence: Float
)

class AIEngine {

    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    // ─── OCR Result Parser ───────────────────────────────────────────────────

    fun parseOcrText(rawOcr: String): ParsedQuestion {
        val lines = rawOcr.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // Deteksi pilihan: pola "A.", "A)", "1.", "1)"
        val choicePattern = Regex("""^([A-Da-d1-4])[.)]\s+(.+)""")
        val choices = mutableMapOf<String, String>()
        val questionLines = mutableListOf<String>()

        for (line in lines) {
            val match = choicePattern.matchEntire(line)
            if (match != null) {
                val key = match.groupValues[1].uppercase()
                    .replace("1", "A").replace("2", "B")
                    .replace("3", "C").replace("4", "D")
                choices[key] = match.groupValues[2]
            } else if (choices.isEmpty()) {
                // Sebelum ada pilihan, semua adalah bagian soal
                questionLines.add(line)
            }
        }

        val questionText = questionLines.joinToString(" ").trim()
        val isEssay = choices.isEmpty()

        return ParsedQuestion(questionText, choices, isEssay)
    }

    // ─── AI Solve ────────────────────────────────────────────────────────────

    suspend fun solve(parsed: ParsedQuestion): AIResult = withContext(Dispatchers.IO) {
        try {
            val prompt = buildPrompt(parsed)
            val raw = callApi(prompt)
            parseApiResponse(raw, parsed.isEssay)
        } catch (e: Exception) {
            AIResult("Error: ${e.message?.take(80)}", "", 0f)
        }
    }

    // Raw text solve (untuk input manual)
    suspend fun solveRaw(question: String): AIResult = withContext(Dispatchers.IO) {
        val parsed = parseOcrText(question)
        solve(parsed)
    }

    // ─── Score suggestion ────────────────────────────────────────────────────

    fun scoreAdvice(current: Int, max: Int, kkm: Int): String {
        val pct = (current.toFloat() / max * 100).toInt()
        return when {
            current >= kkm -> "Lulus — nilai $current/$max ($pct%) di atas KKM $kkm"
            else -> {
                val gap = kkm - current
                "Butuh +$gap poin → KKM $kkm (sekarang $current/$max)"
            }
        }
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private fun buildPrompt(q: ParsedQuestion): String {
        return if (q.isEssay) {
            """
            Jawab soal berikut dengan singkat dan tepat. Langsung ke jawaban, tidak perlu salam.

            Soal: ${q.questionText}
            """.trimIndent()
        } else {
            val choicesText = q.choices.entries
                .sortedBy { it.key }
                .joinToString("\n") { "${it.key}. ${it.value}" }
            """
            Pilih jawaban yang benar. Balas HANYA dengan huruf pilihan (A/B/C/D) lalu penjelasan 1 kalimat.
            Format: [HURUF]. [penjelasan singkat]

            Soal: ${q.questionText}
            Pilihan:
            $choicesText
            """.trimIndent()
        }
    }

    private fun callApi(prompt: String): String {
        val url = URL("$endpoint?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 12000
            readTimeout = 20000
        }

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 256)
            })
        }.toString()

        OutputStreamWriter(conn.outputStream).use { it.write(body) }

        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return response
    }

    private fun parseApiResponse(raw: String, isEssay: Boolean): AIResult {
        return try {
            val text = JSONObject(raw)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            if (isEssay) {
                AIResult(text, "", 0.9f)
            } else {
                // Ambil huruf pertama sebagai jawaban
                val letter = Regex("""^([A-Da-d])""").find(text)?.groupValues?.get(1)?.uppercase() ?: "?"
                val explanation = text.substringAfter(".").trim()
                AIResult(letter, explanation, 0.95f)
            }
        } catch (e: Exception) {
            AIResult("Parse error", raw.take(100), 0f)
        }
    }
}
