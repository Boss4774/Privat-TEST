package com.friend.ai

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class ChatVM(app: Application) : AndroidViewModel(app) {
    private val engine = Engine(app)
    private val mem = Memory(app)
    val messages = mutableStateListOf<Msg>()
    var status by mutableStateOf("")
    var ready by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var pending by mutableStateOf<List<JSONObject>>(emptyList())

    init {
        messages.addAll(mem.history)
        if (engine.hasModel()) loadModel()
    }

    private fun loadModel() {
        viewModelScope.launch(Dispatchers.Default) {
            status = "Завантажую модель…"
            try {
                engine.load()
                ready = true
                status = ""
            } catch (e: Throwable) {
                ready = false
                status = "Помилка моделі: ${e.message}"
            }
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            ready = false
            status = "Копіюю модель (до кількох хвилин)…"
            try {
                engine.close()
                val tmp = File(engine.modelFile.parentFile, "model.tmp")
                getApplication<Application>().contentResolver.openInputStream(uri)!!.use { i ->
                    tmp.outputStream().use { o -> i.copyTo(o) }
                }
                tmp.renameTo(engine.modelFile)
                loadModel()
            } catch (e: Throwable) {
                status = "Не вдалося скопіювати: ${e.message}"
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank() || busy || !ready) return
        busy = true
        viewModelScope.launch(Dispatchers.Default) {
            messages.add(Msg("user", text))
            messages.add(Msg("model", "…"))
            val idx = messages.lastIndex
            try {
                val raw = engine.generate(Prompt.build(mem, text)) {
                    messages[idx] = Msg("model", Actions.clean(it).ifBlank { "…" })
                }
                val clean = Actions.clean(raw).ifBlank { "Готово." }
                messages[idx] = Msg("model", clean)
                mem.history.add(Msg("user", text))
                mem.history.add(Msg("model", clean))
                val (remember, others) = Actions.parse(raw).partition { it.optString("a") == "remember" }
                remember.forEach { mem.addFact(it.optString("fact")) }
                if (others.isNotEmpty()) pending = others
                mem.save()
                compact()
            } catch (e: Throwable) {
                messages[idx] = Msg("model", "Помилка: ${e.message}")
            }
            busy = false
        }
    }

    /** Коли історія довга — стискаємо найстаріші 10 повідомлень у підсумок. */
    private suspend fun compact() {
        if (mem.history.size <= 20) return
        val old = mem.history.take(10)
        val txt = old.joinToString("\n") { (if (it.role == "user") "Користувач: " else "Друг: ") + it.text }
        val p = "<start_of_turn>user\nСтисло перекажи цю розмову у 3-4 реченнях українською, збережи важливі факти і домовленості:\n$txt<end_of_turn>\n<start_of_turn>model\n"
        val s = engine.generate(p) {}
        mem.summary = (mem.summary + " " + s).trim().takeLast(900)
        repeat(10) { mem.history.removeAt(0) }
        mem.save()
    }
}
