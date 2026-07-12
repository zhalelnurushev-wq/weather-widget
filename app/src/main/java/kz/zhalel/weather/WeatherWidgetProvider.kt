package kz.zhalel.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context)                 // мгновенно рисуем из кэша
        UpdateWorker.runOnce(context)   // и запускаем обновление данных
    }

    override fun onEnabled(context: Context) {
        UpdateWorker.schedule(context)  // периодическое обновление каждые 30 минут
    }

    override fun onDisabled(context: Context) {
        UpdateWorker.cancel(context)
    }

    companion object {

        private val dayIds = intArrayOf(R.id.tv_day0, R.id.tv_day1, R.id.tv_day2, R.id.tv_day3, R.id.tv_day4)
        private val iconIds = intArrayOf(R.id.iv_day0, R.id.iv_day1, R.id.iv_day2, R.id.iv_day3, R.id.iv_day4)
        private val maxIds = intArrayOf(R.id.tv_max0, R.id.tv_max1, R.id.tv_max2, R.id.tv_max3, R.id.tv_max4)
        private val minIds = intArrayOf(R.id.tv_min0, R.id.tv_min1, R.id.tv_min2, R.id.tv_min3, R.id.tv_min4)

        fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_weather)
            val data = WeatherRepository.load(context)

            if (data != null) {
                try {
                    val cur = data.getJSONObject("current")
                    val code = cur.getInt("weather_code")
                    val isDay = cur.optInt("is_day", 1) == 1

                    val city = data.optString("city", "")
                    rv.setTextViewText(R.id.tv_city, if (city.isNotBlank()) city else "Моё местоположение")
                    rv.setTextViewText(R.id.tv_temp, "${cur.getDouble("temperature_2m").roundToInt()}°")
                    rv.setTextViewText(R.id.tv_desc, WeatherCodes.textRu(code))
                    rv.setImageViewResource(R.id.iv_icon, WeatherCodes.iconRes(code, isDay))

                    // Прогноз на 5 дней, начиная с сегодняшнего
                    val daily = data.getJSONObject("daily")
                    val dates = daily.getJSONArray("time")
                    val codes = daily.getJSONArray("weather_code")
                    val tmax = daily.getJSONArray("temperature_2m_max")
                    val tmin = daily.getJSONArray("temperature_2m_min")

                    val parse = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val dayFmt = SimpleDateFormat("EE", Locale("ru"))

                    for (i in 0 until 5) {
                        if (i >= dates.length()) break
                        val label = if (i == 0) "Сег."
                        else dayFmt.format(parse.parse(dates.getString(i))!!)
                            .replaceFirstChar { it.uppercase() }
                        rv.setTextViewText(dayIds[i], label)
                        rv.setImageViewResource(iconIds[i], WeatherCodes.iconRes(codes.getInt(i), true))
                        rv.setTextViewText(maxIds[i], "${tmax.getDouble(i).roundToInt()}°")
                        rv.setTextViewText(minIds[i], "${tmin.getDouble(i).roundToInt()}°")
                    }
                } catch (_: Exception) { /* оставляем плейсхолдеры */ }
            }

            // Нажатие на виджет открывает приложение
            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            return rv
        }
    }
}
