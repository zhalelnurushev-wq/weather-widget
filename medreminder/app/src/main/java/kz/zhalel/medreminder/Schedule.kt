package kz.zhalel.medreminder

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class Dose(val med: Med, val slot: Int, val minutes: Int, val day: Long) {
    val key get() = "${med.id}|$day|$slot"
    fun timeText() = "%02d:%02d".format(minutes / 60, minutes % 60)
    fun millis(): Long =
        LocalDate.ofEpochDay(day)
            .atTime(LocalTime.of(minutes / 60, minutes % 60))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/**
 * Расчёт расписания.
 * Завтрак = подъём + 30 мин; ужин = за 2,5 ч до сна; обед — между ними.
 * «До еды» = за 30 мин до приёма пищи, «после еды» = через 30 мин.
 */
object Schedule {

    fun foodText(f: Food) = when (f) {
        Food.BEFORE -> "за 30 мин до еды"
        Food.DURING -> "во время еды"
        Food.AFTER -> "через 30 мин после еды"
        Food.NONE -> "независимо от еды"
    }

    /** Времена приёмов (минуты от начала суток), уже с поправкой на еду. */
    fun doseTimes(perDay: Int, food: Food, wake: Int, sleep: Int): List<Int> {
        val breakfast = wake + 30
        var dinner = sleep - 150
        if (dinner < breakfast + 120) dinner = breakfast + 120

        val base: List<Int> = when (perDay) {
            1 -> listOf(breakfast)
            2 -> listOf(breakfast, dinner)
            3 -> listOf(breakfast, (breakfast + dinner) / 2, dinner)
            else -> {
                val step = (dinner - breakfast) / 3
                listOf(breakfast, breakfast + step, breakfast + 2 * step, dinner)
            }
        }
        val offset = when (food) {
            Food.BEFORE -> -30
            Food.AFTER -> 30
            else -> 0
        }
        return base.map { (it + offset).coerceIn(0, 1439) }
    }

    /** Все приёмы за конкретный день, по возрастанию времени. */
    fun dosesFor(context: Context, day: Long): List<Dose> {
        val wake = Store.wake(context)
        val sleep = Store.sleep(context)
        return Store.meds(context)
            .filter { it.activeOn(day) }
            .flatMap { med ->
                doseTimes(med.perDay, med.food, wake, sleep)
                    .mapIndexed { slot, min -> Dose(med, slot, min, day) }
            }
            .sortedBy { it.minutes }
    }
}
