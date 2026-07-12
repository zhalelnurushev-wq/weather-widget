package kz.zhalel.medreminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Alarms.ensureChannel(this)
        setContent { App() }
    }
}

private fun dateTitle(day: Long): String {
    val millis = LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date(millis))
        .replaceFirstChar { it.uppercase() }
}

@Composable
fun App() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        Alarms.reschedule(context)
        MedWidgetProvider.render(context)
    }

    fun changed() {
        version++
        Alarms.reschedule(context)
        MedWidgetProvider.render(context)
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.Check, null) }, label = { Text("Сегодня") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.DateRange, null) }, label = { Text("История") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Настройки") })
                }
            },
            floatingActionButton = {
                if (tab == 0) FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Добавить лекарство")
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                key(version) {
                    when (tab) {
                        0 -> TodayScreen(onChanged = ::changed)
                        1 -> HistoryScreen()
                        else -> SettingsScreen(onChanged = ::changed)
                    }
                }
            }
        }

        if (showAdd) AddMedDialog(
            onDismiss = { showAdd = false },
            onAdded = { showAdd = false; changed() }
        )
    }
}

@Composable
private fun TodayScreen(onChanged: () -> Unit) {
    val context = LocalContext.current
    val today = Store.today()
    val doses = remember { Schedule.dosesFor(context, today) }
    val meds = remember { Store.meds(context) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text(dateTitle(today), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (doses.isEmpty()) {
            Text(
                "На сегодня приёмов нет.\nДобавь лекарство кнопкой + внизу.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        doses.forEach { d ->
            val taken = Store.isTaken(context, d.key)
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${d.timeText()} · ${d.med.name}",
                            fontSize = 17.sp, fontWeight = FontWeight.Medium,
                            textDecoration = if (taken) TextDecoration.LineThrough else null
                        )
                        Text(
                            "${Schedule.foodText(d.med.food)} · день ${d.med.dayNumber(today)} из ${d.med.days}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Checkbox(
                        checked = taken,
                        onCheckedChange = {
                            Store.setTaken(context, d.key, it)
                            onChanged()
                        }
                    )
                }
            }
        }

        if (meds.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Курсы", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            meds.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, fontSize = 16.sp)
                        val status =
                            if (m.activeOn(today)) "день ${m.dayNumber(today)} из ${m.days}"
                            else if (today < m.startDay) "начнётся завтра"
                            else "курс завершён"
                        Text(
                            "${m.perDay} р/день · ${Schedule.foodText(m.food)} · $status",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { Store.removeMed(context, m.id); onChanged() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                    }
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun HistoryScreen() {
    val context = LocalContext.current
    val today = Store.today()
    val now = System.currentTimeMillis()
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("История приёмов", style = MaterialTheme.typography.headlineSmall)
        var shown = 0
        for (offset in 0..13) {
            val day = today - offset
            val doses = Schedule.dosesFor(context, day)
            if (doses.isEmpty()) continue
            shown++
            Spacer(Modifier.height(16.dp))
            Text(dateTitle(day), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            doses.forEach { d ->
                val taken = Store.isTaken(context, d.key)
                val (status, color) = when {
                    taken -> "✓ в ${timeFmt.format(Date(Store.takenAt(context, d.key)))}" to Color(0xFF66BB6A)
                    d.millis() > now -> "предстоит" to MaterialTheme.colorScheme.onSurfaceVariant
                    else -> "✗ пропущено" to Color(0xFFEF5350)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("${d.timeText()}  ${d.med.name}", Modifier.weight(1f), fontSize = 14.sp)
                    Text(status, color = color, fontSize = 14.sp)
                }
            }
        }
        if (shown == 0) {
            Spacer(Modifier.height(12.dp))
            Text("Пока пусто", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SettingsScreen(onChanged: () -> Unit) {
    val context = LocalContext.current
    var wake by remember { mutableIntStateOf(Store.wake(context)) }
    var sleep by remember { mutableIntStateOf(Store.sleep(context)) }
    fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Режим дня", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "По этим значениям считаются приёмы пищи: завтрак = подъём + 30 мин, " +
                "ужин = за 2,5 ч до сна, обед — между ними. " +
                "«До еды» = за 30 мин, «после еды» = через 30 мин.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        TimeRow("Подъём", fmt(wake),
            onMinus = {
                wake = (wake - 30).coerceAtLeast(4 * 60)
                Store.setWake(context, wake); onChanged()
            },
            onPlus = {
                wake = (wake + 30).coerceAtMost(12 * 60)
                Store.setWake(context, wake); onChanged()
            })

        TimeRow("Отбой", fmt(sleep),
            onMinus = {
                sleep = (sleep - 30).coerceAtLeast(19 * 60)
                Store.setSleep(context, sleep); onChanged()
            },
            onPlus = {
                sleep = (sleep + 30).coerceAtMost(24 * 60 - 30)
                Store.setSleep(context, sleep); onChanged()
            })

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))
        Text("Напоминания", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Уведомление приходит точно в рассчитанное время. Если приём не отмечен, " +
                "напоминание повторяется каждые 15 минут (до 3 раз). " +
                "Отметить приём можно кнопкой «Принял ✓» прямо в уведомлении. " +
                "Будильники восстанавливаются после перезагрузки телефона.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimeRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        OutlinedButton(onClick = onMinus) { Text("−30") }
        Text(
            value, Modifier.padding(horizontal = 14.dp),
            fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
        OutlinedButton(onClick = onPlus) { Text("+30") }
    }
}

@Composable
private fun AddMedDialog(onDismiss: () -> Unit, onAdded: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var perDay by remember { mutableIntStateOf(3) }
    var days by remember { mutableStateOf("7") }
    var food by remember { mutableStateOf(Food.AFTER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое лекарство") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") }, singleLine = true
                )
                Spacer(Modifier.height(14.dp))
                Text("Сколько раз в день", fontSize = 14.sp)
                Row {
                    (1..4).forEach { n ->
                        FilterChip(
                            selected = perDay == n,
                            onClick = { perDay = n },
                            label = { Text("$n") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = days,
                    onValueChange = { s -> days = s.filter { it.isDigit() }.take(3) },
                    label = { Text("Курс, дней") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(14.dp))
                Text("Относительно еды", fontSize = 14.sp)
                Row {
                    FoodChip("До еды", Food.BEFORE, food) { food = it }
                    FoodChip("Во время", Food.DURING, food) { food = it }
                }
                Row {
                    FoodChip("После еды", Food.AFTER, food) { food = it }
                    FoodChip("Не важно", Food.NONE, food) { food = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (days.toIntOrNull() ?: 0) > 0,
                onClick = {
                    val d = days.toInt()
                    val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
                    val hasDoseAhead = Schedule
                        .doseTimes(perDay, food, Store.wake(context), Store.sleep(context))
                        .any { it > nowMin }
                    val startDay = if (hasDoseAhead) Store.today() else Store.today() + 1
                    Store.addMed(
                        context,
                        Med(UUID.randomUUID().toString(), name.trim(), perDay, d, food, startDay)
                    )
                    onAdded()
                }
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun FoodChip(label: String, value: Food, current: Food, onSelect: (Food) -> Unit) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
    )
}
