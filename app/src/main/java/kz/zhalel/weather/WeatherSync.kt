package kz.zhalel.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/**
 * Единая логика обновления: определить местоположение → узнать город →
 * скачать погоду → сохранить в кэш → перерисовать виджет.
 * Используется и приложением, и фоновым WorkManager.
 */
object WeatherSync {

    suspend fun sync(context: Context): JSONObject? = withContext(Dispatchers.IO) {
        try {
            // 1. Пытаемся получить свежие координаты; если нельзя — берём сохранённые
            val fresh = getLocation(context)
            val (lat, lon, city) = if (fresh != null) {
                val cityName = resolveCity(context, fresh)
                WeatherRepository.saveLocation(context, fresh.latitude, fresh.longitude, cityName)
                Triple(fresh.latitude, fresh.longitude, cityName)
            } else {
                WeatherRepository.loadLocation(context) ?: return@withContext null
            }

            // 2. Загружаем и кэшируем
            val json = WeatherRepository.fetch(lat, lon, city)
            WeatherRepository.save(context, json)

            // 3. Обновляем виджет
            WeatherWidgetProvider.render(context)
            json
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocation(context: Context): Location? {
        val hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        return try {
            client.lastLocation.await()
                ?: client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                ).await()
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveCity(context: Context, loc: Location): String = try {
        @Suppress("DEPRECATION")
        val list = Geocoder(context, Locale("ru")).getFromLocation(loc.latitude, loc.longitude, 1)
        val a = list?.firstOrNull()
        a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: ""
    } catch (_: Exception) {
        ""
    }
}
