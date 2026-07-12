package kz.zhalel.weather

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WeatherApp() }
    }
}

@Composable
fun WeatherApp() {
    val context = LocalContext.current
    var data by remember { mutableStateOf(WeatherRepository.load(context)) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            WeatherSync.sync(context)?.let { data = it }
            loading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) refresh()
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) refresh()
        else permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    val current = data?.optJSONObject("current")
    val code = current?.optInt("weather_code") ?: 1
    val isDay = (current?.optInt("is_day") ?: 1) == 1

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(WeatherCodes.gradient(code, isDay)))
    ) {
        val d = data
        if (d == null) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Text(
                        "Нет данных о погоде.\nРазрешите доступ к геолокации и обновите.",
                        color = Color.White, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { refresh() }) { Text("Обновить") }
                }
            }
        } else {
            WeatherContent(d, loading, onRefresh = { refresh() })
        }
    }
}

@Composable
private fun WeatherContent(data: JSONObject, loading: Boolean, onRefresh: () -> Unit) {
    val current = data.getJSONObject("current")
    val code = current.getInt("weather_code")
    val isDay = current.optInt("is_day", 1) == 1
    val city = data.optString("city", "").ifBlank { "Моё местоположение" }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        // Шапка: город + кнопка обновления
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(city, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                val fetched = data.optLong("fetched_at", 0L)
                if (fetched > 0) {
                    Text(
                        "Обновлено: " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(fetched)),
                        color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp
                    )
                }
            }
            if (loading) {
                CircularProgressIndicator(
                    color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Обновить", tint = Color.White)
                }
            }
        }

        // Текущая погода
        Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painterResource(WeatherCodes.iconRes(code, isDay)),
                contentDescription = null,
                modifier = Modifier.size(84.dp)
            )
            Text(
                "${current.getDouble("temperature_2m").roundToInt()}°",
                color = Color.White, fontSize = 84.sp, fontWeight = FontWeight.Light
            )
            Text(WeatherCodes.textRu(code), color = Color.White, fontSize = 20.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ощущается как ${current.getDouble("apparent_temperature").roundToInt()}°",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp
                )
                Spacer(Modifier.width(14.dp))
                Image(
                    painterResource(R.drawable.ic_wind), contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${current.getDouble("wind_speed_10m").roundToInt()} м/с · ${current.getInt("relative_humidity_2m")}%",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp
                )
            }
        }

        // Почасовой прогноз (24 часа)
        GlassCard(title = "Почасовой прогноз") {
            val hourly = data.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val codes = hourly.getJSONArray("weather_code")
            val days = hourly.optJSONArray("is_day")

            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.US).format(Date())
            var start = 0
            for (i in 0 until times.length()) {
                if (times.getString(i) >= nowIso) { start = i; break }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(count = minOf(24, times.length() - start)) { idx ->
                    val i = start + idx
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (idx == 0) "Сейчас" else times.getString(i).substring(11, 16),
                            color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Image(
                            painterResource(
                                WeatherCodes.iconRes(
                                    codes.getInt(i),
                                    days?.optInt(i, 1) != 0
                                )
                            ),
                            contentDescription = null, modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${temps.getDouble(i).roundToInt()}°",
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Прогноз на 5 дней
        GlassCard(title = "Прогноз на 5 дней") {
            val daily = data.getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            val codesD = daily.getJSONArray("weather_code")
            val tmax = daily.getJSONArray("temperature_2m_max")
            val tmin = daily.getJSONArray("temperature_2m_min")

            val parse = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dayFmt = SimpleDateFormat("EEEE, d MMM", Locale("ru"))

            Column {
                for (i in 0 until minOf(5, dates.length())) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (i == 0) "Сегодня"
                            else dayFmt.format(parse.parse(dates.getString(i))!!)
                                .replaceFirstChar { it.uppercase() },
                            color = Color.White, fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Image(
                            painterResource(WeatherCodes.iconRes(codesD.getInt(i), true)),
                            contentDescription = null, modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "${tmin.getDouble(i).roundToInt()}°",
                            color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp
                        )
                        Text(
                            " / ${tmax.getDouble(i).roundToInt()}°",
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Данные: Open-Meteo",
            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GlassCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Text(
            title.uppercase(),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}
