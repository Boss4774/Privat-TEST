package com.friend.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.resume

class Engine(private val ctx: Context) {
    private var llm: LlmInference? = null
    val modelFile: File get() = File(ctx.filesDir, "model.task")
    fun hasModel() = modelFile.exists()

    fun load() {
        close()
        val opts = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(3072)
            .build()
        llm = LlmInference.createFromOptions(ctx, opts)
    }

    fun close() {
        try { llm?.close() } catch (e: Throwable) {}
        llm = null
    }

    suspend fun generate(prompt: String, onPartial: (String) -> Unit): String =
        withTimeout(240_000) {
            suspendCancellableCoroutine { cont ->
                val sb = StringBuilder()
                llm!!.generateResponseAsync(prompt) { part, done ->
                    sb.append(part)
                    onPartial(sb.toString())
                    if (done && cont.isActive) cont.resume(sb.toString())
                }
            }
        }
}
