package com.rafiq.zoyaai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var brain: ZoyaBrain
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        brain = ZoyaBrain(this)
        val prefs = getSharedPreferences("ZoyaPrefs", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#050811"))
            setPadding(50, 80, 50, 50)
        }
        val title = TextView(this).apply { text = "M . A . Y . A"; textSize = 26f; setTextColor(Color.parseColor("#38BDF8")); gravity = Gravity.CENTER }
        val gIn = EditText(this).apply { hint = "Gemini API Key"; setText(prefs.getString("g_key", "")); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val eIn = EditText(this).apply { hint = "ElevenLabs API Key"; setText(prefs.getString("e_key", "")); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val saveBtn = Button(this).apply {
            text = "Save Keys & Enable Accessibility"
            setOnClickListener {
                prefs.edit().putString("g_key", gIn.text.toString().trim()).putString("e_key", eIn.text.toString().trim()).apply()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val orb = TextView(this).apply {
            text = "TAP TO\nTALK"; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; width = 420; height = 420
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; colors = intArrayOf(Color.parseColor("#06B6D4"), Color.parseColor("#0F172A")); gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = 220f }
        }
        statusText = TextView(this).apply { text = "Orb par tap karke bolein..."; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, 50, 0, 0) }

        root.addView(title); root.addView(gIn); root.addView(eIn); root.addView(saveBtn); root.addView(orb); root.addView(statusText)
        setContentView(root)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { runOnUiThread { statusText.text = "Sun rahi hu..." } }
                override fun onResults(r: Bundle?) { r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { runOnUiThread { statusText.text = "Aap: $it" }; handleCmd(it) } }
                override fun onError(e: Int) { runOnUiThread { statusText.text = "Phir se tap karein" } }
                override fun onBeginningOfSpeech() {}; override fun onRmsChanged(v: Float) {}; override fun onBufferReceived(b: ByteArray?) {}; override fun onEndOfSpeech() {}; override fun onPartialResults(p: Bundle?) {}; override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        orb.setOnClickListener { startListening() }
    }

    private fun startListening() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN") }
        speechRecognizer.startListening(i)
    }

    private fun handleCmd(txt: String) {
        val p = getSharedPreferences("ZoyaPrefs", MODE_PRIVATE)
        val gKey = p.getString("g_key", "") ?: ""
        val eKey = p.getString("e_key", "") ?: ""
        if (gKey.isBlank()) { statusText.text = "Pehle Gemini Key save karein!"; return }

        brain.askGemini(txt, gKey) { res ->
            try {
                val obj = JSONObject(res)
                val act = obj.optString("action", "chat")
                val rep = obj.optString("reply", "Thik hai")
                runOnUiThread { statusText.text = "Maya: $rep" }
                brain.speak(rep, eKey) {
                    if (act == "post_reel") runOnUiThread { autoReel(obj.optString("song", "Hindi Song"), obj.optString("caption", "#reels #viral"), eKey) }
                    else if (act == "confirm_share") ZoyaAccessibilityService.instance?.clickByText("Share")
                }
            } catch (e: Exception) { runOnUiThread { statusText.text = "Error: ${e.message}" } }
        }
    }

    private fun autoReel(song: String, caption: String, eKey: String) {
        packageManager.getLaunchIntentForPackage("com.instagram.android")?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(this) }
        handler.postDelayed({ ZoyaAccessibilityService.instance?.clickByText("Create") }, 3000)
        handler.postDelayed({ ZoyaAccessibilityService.instance?.tapCoordinates(200f, 1100f) }, 5000)
        handler.postDelayed({ ZoyaAccessibilityService.instance?.clickByText("Next") }, 6500)
        handler.postDelayed({ ZoyaAccessibilityService.instance?.clickByText("Audio") }, 8500)
        handler.postDelayed({ ZoyaAccessibilityService.instance?.clickByText("Search music"); ZoyaAccessibilityService.instance?.inputText(song) }, 10000)
        handler.postDelayed({ ZoyaAccessibilityService.instance?.tapCoordinates(500f, 550f) }, 12500)
        handler.postDelayed({ ZoyaAccessibilityService.instance?.clickByText("Done"); ZoyaAccessibilityService.instance?.clickByText("Next") }, 14500)
        handler.postDelayed({
            ZoyaAccessibilityService.instance?.clickByText("Write a caption"); ZoyaAccessibilityService.instance?.inputText(caption)
            brain.speak("Gaana aur caption laga diya hai. Share daba du?", eKey) { runOnUiThread { startListening() } }
        }, 17000)
    }
}
