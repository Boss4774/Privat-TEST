package com.friend.ai

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

object Actions {
    private val rx = Regex("\\[\\[(.*?)\\]\\]", RegexOption.DOT_MATCHES_ALL)

    fun clean(text: String): String = rx.replace(text, "").substringBefore("[[").trim()

    fun parse(text: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        for (m in rx.findAll(text)) {
            val s = m.groupValues[1].trim()
            try {
                out.add(JSONObject(s))
            } catch (e: Exception) {
                try {
                    val arr = JSONArray("[$s]")
                    for (i in 0 until arr.length()) out.add(arr.getJSONObject(i))
                } catch (e2: Exception) {
                }
            }
        }
        return out
    }

    fun describe(o: JSONObject): String = when (o.optString("a")) {
        "alarm" -> "Будильник на %02d:%02d".format(o.optInt("h"), o.optInt("m"))
        "timer" -> "Таймер на ${o.optInt("sec")} с"
        "open" -> "Відкрити «${o.optString("app")}»"
        "call" -> "Подзвонити ${o.optString("number")}"
        "sms" -> "SMS для ${o.optString("number")}: ${o.optString("text")}"
        "torch" -> if (o.optBoolean("on", true)) "Увімкнути ліхтарик" else "Вимкнути ліхтарик"
        "event" -> "Подія «${o.optString("title")}» ${o.optString("start")}"
        else -> "Невідома дія"
    }

    private fun start(ctx: Context, i: Intent) {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun run(ctx: Context, o: JSONObject): String {
        try {
            when (o.optString("a")) {
                "alarm" -> start(
                    ctx, Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, o.optInt("h"))
                        .putExtra(AlarmClock.EXTRA_MINUTES, o.optInt("m"))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, o.optString("label"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                )
                "timer" -> start(
                    ctx, Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, o.optInt("sec", 60))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, o.optString("label"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                )
                "open" -> {
                    val pm = ctx.packageManager
                    val q = o.optString("app").lowercase()
                    val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    val hit = pm.queryIntentActivities(main, 0)
                        .firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
                        ?: return "Не знайшов застосунок «$q»"
                    start(ctx, pm.getLaunchIntentForPackage(hit.activityInfo.packageName)!!)
                }
                "call" -> start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(o.optString("number")))))
                "sms" -> start(
                    ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(o.optString("number"))))
                        .putExtra("sms_body", o.optString("text"))
                )
                "torch" -> {
                    val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val id = cm.cameraIdList.firstOrNull {
                        cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } ?: return "Ліхтарика не знайдено"
                    cm.setTorchMode(id, o.optBoolean("on", true))
                }
                "event" -> {
                    val t = LocalDateTime.parse(o.optString("start").trim().replace(' ', 'T'))
                    val ms = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    start(
                        ctx, Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.Events.TITLE, o.optString("title"))
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ms)
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ms + 3_600_000L)
                    )
                }
                else -> return "Невідома дія"
            }
            return "✓ " + describe(o)
        } catch (e: Exception) {
            return "Не вийшло: ${e.message}"
        }
    }
}
