package com.example.kukki

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class KukkiAI(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine: InferenceEngine =
        AiChat.getInferenceEngine(appContext)

    private var currentSystemPrompt: String? = null

    @Volatile
    var isReady: Boolean = false
        private set

    interface Callback {
        fun onStatus(message: String)
        fun onResponse(text: String)
        fun onError(message: String)
    }

    fun setSystemPrompt(prompt: String) {
        currentSystemPrompt = prompt
        if (isReady) {
            scope.launch(Dispatchers.IO) {
                try {
                    engine.setSystemPrompt(prompt)
                } catch (e: Throwable) {
                    android.util.Log.e("KukkiAI", "Error setting system prompt", e)
                }
            }
        }
    }

    fun loadModel(modelPath: String, callback: Callback) {
        scope.launch {
            try {
                isReady = false

                val file = File(modelPath)
                val sizeMb = file.length() / (1024 * 1024)

                withContext(Dispatchers.Main) {
                    callback.onStatus("Loading AI model (${sizeMb} MB)… this may take a while.")
                }

                if (!file.exists() || file.length() == 0L) {
                    throw IllegalStateException("Model file is missing or empty (${file.length()} bytes)")
                }

                engine.loadModel(modelPath)

                val promptToUse = currentSystemPrompt ?: (
                    "You are Kukki, a friendly, intelligent, and natural on-device AI voice assistant. " +
                    "Answer clearly, naturally, and concisely in English."
                )
                engine.setSystemPrompt(promptToUse)

                isReady = true

                withContext(Dispatchers.Main) {
                    callback.onStatus("Kukki AI is ready!")
                }
            } catch (e: Throwable) {
                isReady = false
                android.util.Log.e("KukkiAI", "Error loading AI model from $modelPath", e)
                val typeName = e.javaClass.simpleName
                val msg = e.localizedMessage ?: e.message ?: e.toString()
                val cause = e.cause?.let { " [Cause: ${it.javaClass.simpleName}: ${it.message}]" } ?: ""
                withContext(Dispatchers.Main) {
                    callback.onError("$typeName: $msg$cause")
                }
            }
        }
    }

    fun ask(question: String, callback: Callback) {
        if (!isReady) {
            callback.onError("The AI model is not loaded yet.")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val answer = StringBuilder()
                var lastUpdateTime = 0L

                engine.sendUserPrompt(question).collect { token ->
                    answer.append(token)
                    val now = System.currentTimeMillis()

                    if (now - lastUpdateTime >= 48L || token.contains("\n") || token.contains(".") || token.contains("!")) {
                        lastUpdateTime = now
                        val currentText = answer.toString()
                        withContext(Dispatchers.Main.immediate) {
                            callback.onResponse(currentText)
                        }
                    }
                }

                val finalText = answer.toString().trim()
                if (finalText.isEmpty()) {
                    val fallback = "That is an interesting and complex question! While I am thinking about all the details, what aspect of it interests you most?"
                    withContext(Dispatchers.Main.immediate) {
                        callback.onResponse(fallback)
                    }
                } else {
                    withContext(Dispatchers.Main.immediate) {
                        callback.onResponse(finalText)
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("KukkiAI", "Error generating response", e)
                val fallback = "That's a fascinating topic! I had a momentary thought loop while processing it. Could you rephrase it or ask me another way?"
                withContext(Dispatchers.Main.immediate) {
                    callback.onResponse(fallback)
                }
            }
        }
    }

    fun close() {
        isReady = false
        scope.cancel()
        engine.destroy()
    }
}
