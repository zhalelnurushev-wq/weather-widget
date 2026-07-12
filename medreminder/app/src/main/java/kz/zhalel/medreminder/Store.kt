package kz.zhalel.medreminder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class Food { BEFORE, DURING, AFTER, NONE }

data class Med(
    val id: String,
    val name: String,
    val perDay: Int,
    val days: Int,
    val food: Food,
    val startDay: Long
) {
    fun activeOn(day: Long) = day in startDay until (startDay + days)
    fun dayNumber(day: Long) = (day - startDay + 1).toInt()
}

/** Хранилище: лекарства, лог приёмов, повторы уведомлений, режим дня. */
object Store {
    private const val PREFS = "med_prefs"
    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun today(): Long = LocalDate.now().toEpochDay()

    // Режим дня (минуты от начала суток)
    fun wake(c: Context) = p(c).getInt("wake", 7 * 60)
    fun sleep(c: Context) = p(c).getInt("sleep", 23 * 60)
    fun setWake(c: Context, v: Int) = p(c).edit().putInt("wake", v).apply()
    fun setSleep(c: Context, v: Int) = p(c).edit().putInt("sleep", v).apply()

    // Лекарства
    fun meds(c: Context): List<Med> {
        val arr = JSONArray(p(c).getString("meds", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Med(
                o.getString("id"), o.getString("name"), o.getInt("perDay"),
                o.getInt("days"), Food.valueOf(o.getString("food")), o.getLong("startDay")
            )
        }
    }

    private fun saveMeds(c: Context, meds: List<Med>) {
        val arr = JSONArray()
        meds.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id).put("name", m.name).put("perDay", m.perDay)
                    .put("days", m.days).put("food", m.food.name).put("startDay", m.startDay)
            )
        }
        p(c).edit().putString("meds", arr.toString()).apply()
    }

    fun addMed(c: Context, m: Med) = saveMeds(c, meds(c) + m)
    fun removeMed(c: Context, id: String) = saveMeds(c, meds(c).filter { it.id != id })

    // Лог приёмов: ключ "medId|day|slot" -> {"at": millis}
    private fun log(c: Context) = JSONObject(p(c).getString("log", "{}"))
    fun isTaken(c: Context, key: String) = log(c).has(key)
    fun takenAt(c: Context, key: String): Long = log(c).optJSONObject(key)?.optLong("at") ?: 0L
    fun setTaken(c: Context, key: String, taken: Boolean) {
        val l = log(c)
        if (taken) l.put(key, JSONObject().put("at", System.currentTimeMillis())) else l.remove(key)
        p(c).edit().putString("log", l.toString()).apply()
        clearRepeat(c, key)
    }

    // Повторы уведомлений: ключ -> {"n": счётчик, "at": время следующего повтора}
    private fun reps(c: Context) = JSONObject(p(c).getString("reps", "{}"))
    fun repeatCount(c: Context, key: String) = reps(c).optJSONObject(key)?.optInt("n") ?: 0
    fun setRepeat(c: Context, key: String, n: Int, at: Long) {
        val r = reps(c)
        r.put(key, JSONObject().put("n", n).put("at", at))
        p(c).edit().putString("reps", r.toString()).apply()
    }
    fun clearRepeat(c: Context, key: String) {
        val r = reps(c)
        r.remove(key)
        p(c).edit().putString("reps", r.toString()).apply()
    }
    fun pendingRepeats(c: Context): Map<String, Pair<Int, Long>> {
        val r = reps(c)
        val out = mutableMapOf<String, Pair<Int, Long>>()
        r.keys().forEach { k ->
            val o = r.getJSONObject(k)
            out[k] = o.getInt("n") to o.getLong("at")
        }
        return out
    }
}
