package ir.naderii.kidstime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.naderii.kidstime.data.LocalStore
import ir.naderii.kidstime.data.normalizeDomain
import ir.naderii.kidstime.ui.KidsTimeTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private var lockSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalStore(applicationContext)
        setContent {
            KidsTimeTheme {
                KidsTimeApp(store = store, lockSignal = lockSignal)
            }
        }
    }

    // Parent panel auto-locks whenever the app leaves the foreground.
    override fun onStop() {
        super.onStop()
        lockSignal++
    }
}

private enum class Screen { HOME, PARENT, SCHEDULE, WEBSITES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KidsTimeApp(store: LocalStore, lockSignal: Int) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var refresh by remember { mutableIntStateOf(0) }
    var pinDialog by rememberSaveable { mutableStateOf(false) }
    var pin by rememberSaveable { mutableStateOf("") }
    var pinError by rememberSaveable { mutableStateOf(false) }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(store.hasPin()) }
    var lockMsg by remember { mutableStateOf("") }

    LaunchedEffect(lockSignal) {
        if (lockSignal > 0) {
            unlocked = false; screen = Screen.HOME
            pinDialog = false; pin = ""; pinError = false
        }
    }

    run {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("KidsTime", fontWeight = FontWeight.Bold) },
                    actions = {
                        TextButton(onClick = { pinDialog = true }) { Text("والدین") }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == Screen.HOME,
                        onClick = { screen = Screen.HOME },
                        icon = { Text("⌂") },
                        label = { Text("خانه") }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.SCHEDULE,
                        onClick = { if (unlocked) screen = Screen.SCHEDULE else pinDialog = true },
                        icon = { Text("◷") },
                        label = { Text("برنامه زمانی") }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.WEBSITES,
                        onClick = { if (unlocked) screen = Screen.WEBSITES else pinDialog = true },
                        icon = { Text("◎") },
                        label = { Text("سایت‌ها") }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (screen) {
                    Screen.HOME -> HomeScreen(store, refresh)
                    Screen.PARENT -> ParentScreen(store) { refresh++ }
                    Screen.SCHEDULE -> ScheduleScreen(store) { refresh++ }
                    Screen.WEBSITES -> WebsitesScreen(store) { refresh++ }
                }
                if (screen == Screen.HOME) {
                    Button(
                        onClick = { if (unlocked) screen = Screen.PARENT else pinDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تنظیمات والدین") }
                }
                Text(
                    "نسخه آزمایشی 0.1 — محدودیت واقعی گوشی و فیلتر شبکه هنوز فعال نیست.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (pinDialog) {
        AlertDialog(
            onDismissRequest = { pinDialog = false; pin = ""; pinError = false },
            title = { Text(if (hasPin) "ورود والدین" else "ساخت رمز والدین") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!hasPin) Text("برای اولین استفاده یک رمز عددی ۴ تا ۱۲ رقمی بسازید.")
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(12); pinError = false },
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("رمز والدین") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    if (pinError) Text(
                        if (lockMsg.isNotBlank()) lockMsg
                        else if (!hasPin) "رمز باید ۴ تا ۱۲ رقم باشد." else "رمز واردشده درست نیست.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    lockMsg = ""
                    if (!hasPin) {
                        if (store.isValidPinFormat(pin)) {
                            store.setPin(pin); hasPin = true
                            unlocked = true; screen = Screen.PARENT
                            pinDialog = false; pin = ""
                        } else pinError = true
                    } else {
                        val wait = store.lockRemainingMs()
                        if (wait > 0) {
                            lockMsg = "تلاش‌های زیاد. ${(wait + 999) / 1000} ثانیه بعد دوباره امتحان کنید."
                            pinError = true
                        } else if (store.checkPin(pin)) {
                            unlocked = true; screen = Screen.PARENT
                            pinDialog = false; pin = ""
                        } else {
                            val w = store.lockRemainingMs()
                            if (w > 0) lockMsg = "تلاش‌های زیاد. ${(w + 999) / 1000} ثانیه صبر کنید."
                            pinError = true
                        }
                    }
                }) { Text("ورود") }
            },
            dismissButton = {
                TextButton(onClick = { pinDialog = false; pin = ""; pinError = false }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun HomeScreen(store: LocalStore, refresh: Int) {
    val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val limit = store.getDailyLimit(day)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("سلام قهرمان کوچولو! 💛", style = MaterialTheme.typography.headlineSmall)
            Text("زمان بازی امروز")
            Text("$limit دقیقه", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth())
            Text("در این نسخه هنوز زمان مصرف واقعی گوشی محاسبه نمی‌شود.")
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("برنامه امروز", fontWeight = FontWeight.Bold)
            Text("سقف مجاز: $limit دقیقه")
            Text("وضعیت: تنظیمات محلی آماده است؛ موتور پایش در مرحله بعد اضافه می‌شود.")
        }
    }
}

@Composable
private fun ParentScreen(store: LocalStore, onSaved: () -> Unit) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("پنل والدین", style = MaterialTheme.typography.headlineSmall)
            Text("تنظیمات در همین گوشی ذخیره می‌شوند و به اینترنت نیاز ندارند.")
            OutlinedTextField(
                value = currentPin, onValueChange = { currentPin = it.filter(Char::isDigit).take(12) },
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("رمز فعلی") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(12) },
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("رمز جدید (۴ تا ۱۲ رقم)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                message = when {
                    !store.checkPin(currentPin) -> "رمز فعلی صحیح نیست."
                    !store.isValidPinFormat(newPin) -> "رمز جدید باید ۴ تا ۱۲ رقم باشد."
                    else -> {
                        store.setPin(newPin)
                        currentPin = ""; newPin = ""
                        "رمز والدین تغییر کرد."
                    }
                }
                onSaved()
            }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره رمز") }
            if (message.isNotBlank()) Text(message)
        }
    }
}

@Composable
private fun ScheduleScreen(store: LocalStore, onSaved: () -> Unit) {
    val days = listOf(
        Calendar.SATURDAY to "شنبه",
        Calendar.SUNDAY to "یکشنبه",
        Calendar.MONDAY to "دوشنبه",
        Calendar.TUESDAY to "سه‌شنبه",
        Calendar.WEDNESDAY to "چهارشنبه",
        Calendar.THURSDAY to "پنجشنبه",
        Calendar.FRIDAY to "جمعه"
    )
    var values by remember { mutableStateOf(days.associate { it.first to store.getDailyLimit(it.first).toString() }) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("سقف استفاده هفتگی", style = MaterialTheme.typography.headlineSmall)
        Text("زمان‌ها برحسب دقیقه هستند. این تنظیمات هنوز محدودیت سیستم‌عامل را اعمال نمی‌کنند.")
        days.forEach { (day, label) ->
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = values[day].orEmpty(),
                            onValueChange = { raw ->
                                val digits = raw.filter(Char::isDigit).take(4)
                                val n = digits.toIntOrNull()
                                if (digits.isEmpty() || (n != null && n in 0..1440)) values = values + (day to digits)
                            },
                            modifier = Modifier.width(110.dp),
                            label = { Text("دقیقه") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
            }
        }
        Button(onClick = {
            values.forEach { (day, minutes) -> store.setDailyLimit(day, minutes.toIntOrNull() ?: 0) }
            onSaved()
        }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره برنامه هفتگی") }
    }
}

@Composable
private fun WebsitesScreen(store: LocalStore, onSaved: () -> Unit) {
    var domain by remember { mutableStateOf("") }
    var domains by remember { mutableStateOf(store.getAllowedDomains()) }
    var message by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("فهرست دامنه‌های مجاز", style = MaterialTheme.typography.headlineSmall)
        Text("دامنه وارد کنید؛ مثال: example.com. این فهرست فعلاً ذخیره می‌شود اما ترافیک را فیلتر نمی‌کند.")
        OutlinedTextField(
            value = domain, onValueChange = { domain = it.trim() },
            label = { Text("دامنه (مثلاً example.com)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            val normalized = normalizeDomain(domain)
            if (normalized == null) {
                message = "یک دامنه معتبر وارد کنید."
            } else {
                domains = (domains + normalized).distinct().sorted()
                store.setAllowedDomains(domains)
                domain = ""
                message = "دامنه ذخیره شد."
                onSaved()
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("افزودن دامنه") }
        if (message.isNotBlank()) Text(message)
        if (domains.isEmpty()) Text("هنوز دامنه‌ای اضافه نشده است.")
        domains.forEach { item ->
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        domains = domains - item
                        store.setAllowedDomains(domains)
                        message = "دامنه حذف شد."
                        onSaved()
                    }) { Text("حذف") }
                }
            }
        }
    }
}
