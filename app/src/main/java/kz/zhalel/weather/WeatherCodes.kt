package kz.zhalel.weather

import androidx.compose.ui.graphics.Color

/**
 * Коды погоды WMO (Open-Meteo) → иконка, описание на русском, градиент фона приложения.
 */
object WeatherCodes {

    fun iconRes(code: Int, isDay: Boolean): Int = when {
        code == 0 -> if (isDay) R.drawable.ic_sun else R.drawable.ic_moon
        code in 1..2 -> if (isDay) R.drawable.ic_partly else R.drawable.ic_cloud
        code == 3 -> R.drawable.ic_cloud
        code in 45..48 -> R.drawable.ic_fog
        code in 51..57 -> R.drawable.ic_drizzle
        code in 61..67 -> R.drawable.ic_rain
        code in 71..77 -> R.drawable.ic_snow
        code in 80..82 -> R.drawable.ic_rain
        code in 85..86 -> R.drawable.ic_snow
        code >= 95 -> R.drawable.ic_thunder
        else -> R.drawable.ic_cloud
    }

    fun textRu(code: Int): String = when {
        code == 0 -> "Ясно"
        code == 1 -> "Малооблачно"
        code == 2 -> "Переменная облачность"
        code == 3 -> "Пасмурно"
        code in 45..48 -> "Туман"
        code in 51..57 -> "Морось"
        code in 61..63 -> "Дождь"
        code in 65..67 -> "Сильный дождь"
        code in 71..73 -> "Снег"
        code in 75..77 -> "Сильный снег"
        code in 80..82 -> "Ливень"
        code in 85..86 -> "Снегопад"
        code >= 95 -> "Гроза"
        else -> "Облачно"
    }

    /** Градиент фона приложения под текущую погоду (как в Samsung Weather). */
    fun gradient(code: Int, isDay: Boolean): List<Color> = when {
        !isDay -> listOf(Color(0xFF0B1026), Color(0xFF1B2A4A), Color(0xFF2C3E66))          // ночь
        code == 0 -> listOf(Color(0xFF1E88E5), Color(0xFF42A5F5), Color(0xFF90CAF9))       // ясно
        code in 1..2 -> listOf(Color(0xFF3C6FB0), Color(0xFF6C9BD1), Color(0xFFA3C1E5))    // облачка
        code == 3 -> listOf(Color(0xFF4E5D6C), Color(0xFF7C8B9A), Color(0xFFA5B1BC))       // пасмурно
        code in 45..48 -> listOf(Color(0xFF6B7A88), Color(0xFF95A3AF), Color(0xFFC2CBD3))  // туман
        code in 51..67 || code in 80..82 -> listOf(Color(0xFF25384D), Color(0xFF3E5468), Color(0xFF5C7186)) // дождь
        code in 71..77 || code in 85..86 -> listOf(Color(0xFF7C97B8), Color(0xFFA5BBD4), Color(0xFFD4E1EE)) // снег
        code >= 95 -> listOf(Color(0xFF1A1A2E), Color(0xFF33334D), Color(0xFF4D4D70))      // гроза
        else -> listOf(Color(0xFF3C6FB0), Color(0xFF6C9BD1))
    }
}
