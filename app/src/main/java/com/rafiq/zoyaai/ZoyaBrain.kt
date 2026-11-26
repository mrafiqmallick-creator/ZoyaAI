package com.rafiq.zoyaai

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale

class ZoyaBrain(private val context: Context) {

    private val client = OkHttpClient()
    private var fallbackTts: TextToSpeech? = null

    init {
        fallbackTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                fallbackTts?.language = Locale("hi", "IN")
                fallbackTts?.setPitch(1.1f)
            }
        }
    }

    fun speak(text: String, elevenKey: String, onDone: () -> Unit) {
        if (elevenKey.isBlank()) {
            fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MAYA_ID")
            onDone()
            return
        }
        val json = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2")
        }
        val req = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/21m00Tcm4TlvDq8ikWAM")
            .addHeader("xi-api-key", elevenKey)
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MAYA_ID")
                onDone()
            }
            override fun onResponse(call: Call, response: Response) {
                val bytes = response.body?.bytes()
                if (!response.isSuccessful || bytes == null) {
                    fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MAYA_ID")
                    onDone()
                    return
                }
                val file = File(context.cacheDir, "maya_reply.mp3")
                file.writeBytes(bytes)
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener { it.release(); onDone() }
                    start()
                }
            }
        })
    }

    fun askGemini(prompt: String, geminiKey: String, onResult: (String) -> Unit) {
        val sys = "Tum Maya ho, smart Indian female AI. Chote Hindi/Hinglish mein jawab do. Reel post ke liye JSON do: {\"action\":\"post_reel\",\"song\":\"hindi_song\",\"caption\":\"viral_caption\",\"reply\":\"Haan abhi karti hu\"}. Share ke liye: {\"action\":\"confirm_share\",\"reply\":\"Reel post ho rahi hai\"}. Warna: {\"action\":\"chat\",\"reply\":\"jawab\"}"
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "$sys\nUser: $prompt")))))
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("""{"action":"chat","reply":"Network check karo."}""")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val txt = JSONObject(response.body?.string() ?: "").getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").replace("```json", "").replace("```", "").trim()
                    onResult(txt)
                } catch (e: Exception) {
                    onResult("""{"action":"chat","reply":"Phir se bolo."}""")
                }
            }
        })
    }
}
