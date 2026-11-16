package com.friend.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Msg(val role: String, val text: String)

/** Довготривала пам'ять: факти про користувача + підсумок старих розмов + остання історія. */
class Memory(ctx: Context) {
    private val file = File(ctx.filesDir, "memory.json")
    val facts = mutableListOf<String>()
    val history = mutableListOf<Msg>()
    var summary = ""

    init {
        try {
            if (file.exists()) {
                val o = JSONObject(file.readText())
                summary = o.optString("summary")
                o.optJSONArray("facts")?.let { for (i in 0 until it.length()) facts.add(it.getString(i)) }
                o.optJSONArray("history")?.let {
                    for (i in 0 until it.length()) {
                        val m = it.getJSONObject(i)
                        history.add(Msg(m.getString("role"), m.getString("text")))
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    fun addFact(f: String) {
        val t = f.trim()
        if (t.isEmpty() || facts.any { it.equals(t, true) }) return
        facts.add(t)
        while (facts.size > 60) facts.removeAt(0)
    }

    @Synchronized
    fun save() {
        val o = JSONObject()
        o.put("summary", summary)
        o.put("facts", JSONArray(facts))
        val h = JSONArray()
        history.forEach { h.put(JSONObject().put("role", it.role).put("text", it.text)) }
        o.put("history", h)
        file.writeText(o.toString())
    }
}

object Prompt {
    private const val SYS = """Ти — близький друг користувача: теплий, уважний, з почуттям гумору. Говориш українською, неформально, коротко (1-4 речення), без списків і шаблонних фраз. Ти пам'ятаєш користувача, цікавишся його життям і підтримуєш його.
Ти також можеш керувати телефоном. Щоб виконати дію, додай в кінці відповіді рядок у форматі [[{...}]]. Доступні дії:
{"a":"alarm","h":7,"m":30,"label":"текст"}
{"a":"timer","sec":300,"label":"текст"}
{"a":"open","app":"назва застосунку"}
{"a":"call","number":"+380..."}
{"a":"sms","number":"+380...","text":"текст"}
{"a":"torch","on":true}
{"a":"event","title":"текст","start":"2026-11-20 18:00"}
{"a":"remember","fact":"важливий факт про користувача"}
Використовуй remember, коли користувач розповідає щось важливе про себе (імена, плани, вподобання). Не вигадуй дій, якщо їх не просили."""

    fun build(mem: Memory, userText: String): String {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm, EEEE"))
        val sb = StringBuilder()
        sb.append("<start_of_turn>user\n").append(SYS).append("\n\nЗараз: ").append(now)
        if (mem.facts.isNotEmpty()) {
            sb.append("\n\nЩо ти знаєш про користувача:\n")
            mem.facts.forEach { sb.append("- ").append(it).append("\n") }
        }
        if (mem.summary.isNotBlank()) sb.append("\nПідсумок попередніх розмов: ").append(mem.summary)
        sb.append("<end_of_turn>\n<start_of_turn>model\nЗрозумів, я на зв'язку.<end_of_turn>\n")

        var budget = 2500
        val recent = ArrayList<Msg>()
        for (m in mem.history.asReversed()) {
            if (budget - m.text.length < 0) break
            budget -= m.text.length
            recent.add(0, m)
        }
        recent.dropWhile { it.role != "user" }.forEach {
            sb.append("<start_of_turn>").append(it.role).append("\n").append(it.text).append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>user\n").append(userText).append("<end_of_turn>\n<start_of_turn>model\n")
        return sb.toString()
    }
}
