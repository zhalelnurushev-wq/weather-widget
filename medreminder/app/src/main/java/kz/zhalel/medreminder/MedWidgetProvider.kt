package kz.zhalel.medreminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class MedWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context)
    }

    override fun onEnabled(context: Context) {
        Alarms.reschedule(context)
    }

    companion object {
        fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MedWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val rv = RemoteViews(context.packageName, R.layout.widget_meds)
            val doses = Schedule.dosesFor(context, Store.today())
            val text = if (doses.isEmpty()) "На сегодня приёмов нет"
            else doses.joinToString("\n") { d ->
                val mark = if (Store.isTaken(context, d.key)) "✓" else "○"
                "${d.timeText()}  $mark  ${d.med.name}"
            }
            rv.setTextViewText(R.id.tv_list, text)

            val pi = PendingIntent.getActivity(
                context, 3, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.widget_root, pi)

            ids.forEach { manager.updateAppWidget(it, rv) }
        }
    }
}
