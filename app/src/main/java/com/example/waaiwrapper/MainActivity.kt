package com.example.waaiwrapper

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.BufferedReader
import java.io.InputStreamReader

// --- Retrofit API Definition ---
interface AiApiService {
    @POST("/api/ai_index")
    suspend fun getAiIndex(@Body request: AiIndexRequest): AiIndexResponse
}

data class AiIndexRequest(
    @SerializedName("answers") val answers: List<String>,
    @SerializedName("user_message") val userMessage: String
)

data class AiIndexResponse(
    @SerializedName("index") val index: Int?
)

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var aiAnswers: List<String> = emptyList()
    private var isAutoMode: Boolean = false

    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl("http://37.140.243.234:3000")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AiApiService::class.java)
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let { loadContextFromFile(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        setupWebView()

        findViewById<View>(R.id.btn_select_file).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "text/plain"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            filePickerLauncher.launch(intent)
        }

        findViewById<View>(R.id.btn_toggle_auto).setOnClickListener {
            isAutoMode = !isAutoMode
            val modeText = if (isAutoMode) "Auto" else "Manual"
            Toast.makeText(this, "Mode: $modeText", Toast.LENGTH_SHORT).show()
            webView.evaluateJavascript("window.setAutoMode($isAutoMode)", null)
        }
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectScriptFromAssets("whatsapp_injector.js")
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(WhatsAppInterface(), "Android")
        webView.loadUrl("https://web.whatsapp.com")
    }

    private fun injectScriptFromAssets(fileName: String) {
        try {
            val inputStream = assets.open(fileName)
            val script = inputStream.bufferedReader().use { it.readText() }
            webView.evaluateJavascript(script, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadContextFromFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val content = reader.readText()
                    // Split by double newline or triple dash to separate answers
                    // or just line by line if preferred. Using newline as default.
                    aiAnswers = content.split("\n")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    
                    Toast.makeText(this, "Loaded ${aiAnswers.size} answers!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading file", Toast.LENGTH_SHORT).show()
        }
    }

    inner class WhatsAppInterface {
        @JavascriptInterface
        fun onTriggerDetected(message: String) {
            runOnUiThread {
                if (aiAnswers.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Knowledge base is empty!", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                Toast.makeText(this@MainActivity, "Thinking...", Toast.LENGTH_SHORT).show()
                fetchAiIndex(message)
            }
        }
    }

    private fun fetchAiIndex(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getAiIndex(AiIndexRequest(aiAnswers, message))
                val index = response.index
                
                withContext(Dispatchers.Main) {
                    if (index != null && index >= 0 && index < aiAnswers.size) {
                        injectReply(aiAnswers[index])
                    } else {
                        Toast.makeText(this@MainActivity, "AI couldn't find a match", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "AI Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun injectReply(reply: String) {
        val escapedReply = reply.replace("'", "\\'").replace("\n", "\\n").replace("\r", "")
        webView.evaluateJavascript("window.injectReply('$escapedReply')", null)
    }
}
