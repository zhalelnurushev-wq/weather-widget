package kz.zhalel.weather

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Загрузка погоды с Open-Meteo (бесплатно, без API-ключа) и кэш в SharedPreferences.
 * Кэш нужен, чтобы виджет мог отрисоваться мгновенно, без сети.
 */
object WeatherRepository {

    private const val PREFS = "weather_prefs"
    private const val KEY_DATA = "data"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_CITY = "city"

    fun fetch(lat: Double, lon: Double, city: String): JSONObject {
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
                "&hourly=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=6&wind_speed_unit=ms"

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        return JSONObject(text).apply {
            put("city", city)
            put("fetched_at", System.currentTimeMillis())
        }
    }

    fun save(context: Context, json: JSONObject) {
        prefs(context).edit().putString(KEY_DATA, json.toString()).apply()
    }

    fun load(context: Context): JSONObject? =
        prefs(context).getString(KEY_DATA, null)?.let {
            try { JSONObject(it) } catch (_: Exception) { null }
        }

    fun saveLocation(context: Context, lat: Double, lon: Double, city: String) {
        prefs(context).edit()
            .putFloat(KEY_LAT, lat.toFloat())
            .putFloat(KEY_LON, lon.toFloat())
            .putString(KEY_CITY, city)
            .apply()
    }

    /** Последняя известная точка (на случай, когда фоновый доступ к геолокации недоступен). */
    fun loadLocation(context: Context): Triple<Double, Double, String>? {
        val p = prefs(context)
        if (!p.contains(KEY_LAT)) return null
        return Triple(
            p.getFloat(KEY_LAT, 0f).toDouble(),
            p.getFloat(KEY_LON, 0f).toDouble(),
            p.getString(KEY_CITY, "") ?: ""
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
