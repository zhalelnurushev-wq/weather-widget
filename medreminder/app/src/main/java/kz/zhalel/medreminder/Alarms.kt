package kz.zhalel.medreminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Один "цепной" будильник: всегда ставим ближайшее событие (приём или повтор).
 * setAlarmClock срабатывает точно даже в режиме энергосбережения (Doze)
 * и не требует специальных разрешений.
 */
object Alarms {
    const val CHANNEL = "meds"
    const val MAX_REPEATS = 3
    const val REPEAT_MS = 15 * 60 * 1000L
    private const val DUE_WINDOW_MS = 95 * 60 * 1000L

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Напоминания о лекарствах", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun reschedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = alarmPi(c)
        am.cancel(pi)
        val next = nextEventMillis(c) ?: return
        am.setAlarmClock(AlarmManager.AlarmClockInfo(next, contentPi(c)), pi)
    }

    private fun nextEventMillis(c: Context): Long? {
        val now = System.currentTimeMillis()
        val candidates = mutableListOf<Long>()
        val today = Store.today()

        for (day in longArrayOf(today, today + 1)) {
            Schedule.dosesFor(c, day).forEach { d ->
                if (!Store.isTaken(c, d.key) && d.millis() > now) candidates += d.millis()
            }
        }
        Store.pendingRepeats(c).forEach { (key, v) ->
            val (n, at) = v
            if (n <= MAX_REPEATS && at > now && !Store.isTaken(c, key)) candidates += at
        }
        return candidates.minOrNull()
    }

    /** Срабатывание будильника: показать уведомления по всем "должным" приёмам. */
    fun onAlarm(c: Context) {
        ensureChannel(c)
        val now = System.currentTimeMillis()

        Schedule.dosesFor(c, Store.today()).forEach { d ->
            val overdue = now - d.millis()
            if (overdue >= -30_000 && overdue <= DUE_WINDOW_MS && !Store.isTaken(c, d.key)) {
                val n = Store.repeatCount(c, d.key)
                notify(c, d, n)
                if (n < MAX_REPEATS) Store.setRepeat(c, d.key, n + 1, now + REPEAT_MS)
                else Store.clearRepeat(c, d.key)
            }
        }
        reschedule(c)
        MedWidgetProvider.render(c)
    }

    private fun notify(c: Context, d: Dose, repeat: Int) {
        val takenIntent = Intent(c, TakenReceiver::class.java)
            .putExtra("key", d.key)
            .putExtra("nid", d.key.hashCode())
        val takenPi = PendingIntent.getBroadcast(
            c, d.key.hashCode(), takenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (repeat == 0) "Пора принять лекарство"
        else "Напоминание ($repeat): приём не отмечен"

        val nb = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title)
            .setContentText("${d.med.name} — ${d.timeText()}, ${Schedule.foodText(d.med.food)}")
            .setContentIntent(contentPi(c))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Принял ✓", takenPi)

        try {
            NotificationManagerCompat.from(c).notify(d.key.hashCode(), nb.build())
        } catch (_: SecurityException) {
            // нет разрешения на уведомления — попросим при следующем открытии приложения
        }
    }

    private fun alarmPi(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 1, Intent(c, AlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun contentPi(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 2, Intent(c, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Alarms.onAlarm(context)
}

/** Кнопка «Принял ✓» в уведомлении. */
class TakenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        Store.setTaken(context, key, true)
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra("nid", 0))
        Alarms.reschedule(context)
        MedWidgetProvider.render(context)
    }
}

/** Восстановление будильников после перезагрузки телефона. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Alarms.reschedule(context)
    }
}
