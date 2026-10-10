@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.talayeman.gold.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talayeman.gold.BuildConfig
import com.talayeman.gold.data.repository.SettingsRepository
import com.talayeman.gold.domain.model.Currency
import com.talayeman.gold.domain.model.ThemeMode
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.ui.components.AppCard
import com.talayeman.gold.ui.components.IconBadge
import com.talayeman.gold.util.BackupManager
import com.talayeman.gold.util.BackupShare
import com.talayeman.gold.util.BiometricHelper
import com.talayeman.gold.util.JalaliCalendar
import com.talayeman.gold.util.MoneyUtils
import com.talayeman.gold.util.PinManager
import kotlinx.coroutines.launch
import java.io.File

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private enum class PinDialogMode { NONE, SET, DISABLE, CHANGE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: AppViewModel = viewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val notifPriceUpdate by viewModel.notifPriceUpdate.collectAsState()
    val notifProfit by viewModel.notifProfit.collectAsState()
    val notifLoss by viewModel.notifLoss.collectAsState()
    val notifDaily by viewModel.notifDaily.collectAsState()
    val notifShowFinance by viewModel.notifShowFinance.collectAsState()

    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    fun say(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    // ---- security state (read from the secure store, which is the single source of truth) ----
    val pin = remember { PinManager.get(context) }
    var lockEnabled by remember { mutableStateOf(pin.hasPin()) }
    var biometricOn by remember { mutableStateOf(pin.isBiometricEnabled()) }
    var pinDialog by remember { mutableStateOf(PinDialogMode.NONE) }

    var busy by remember { mutableStateOf(false) }
    var restoreDone by remember { mutableStateOf(false) }

    // ---- notification permission (Android 13+), requested only when a notification is enabled ----
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) say("اجازه‌ی اعلان داده نشد. برای دریافت اعلان‌ها آن را در تنظیمات گوشی فعال کنید.")
    }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun setNotif(key: String, value: Boolean) {
        if (value) ensureNotificationPermission()
        viewModel.setNotifSetting(key, value)
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val tmp = File(context.cacheDir, "backup.zip")
                val result = BackupManager(context).createBackup(tmp)
                if (result.isSuccess) {
                    val ok = runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            tmp.inputStream().use { it.copyTo(out) }
                        } != null
                    }.getOrDefault(false)
                    tmp.delete()
                    say(if (ok) "پشتیبان‌گیری با موفقیت انجام شد" else "ذخیره فایل پشتیبان ممکن نشد")
                } else {
                    say("خطا در پشتیبان‌گیری: ${result.exceptionOrNull()?.message}")
                }
                busy = false
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val tmp = File(context.cacheDir, "restore.zip")
                val copied = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    } != null
                }.getOrDefault(false)
                if (!copied) {
                    say("خواندن فایل پشتیبان ممکن نشد")
                } else {
                    val result = BackupManager(context).restoreBackup(tmp)
                    tmp.delete()
                    if (result.isSuccess) restoreDone = true
                    else say("خطا در بازیابی: ${result.exceptionOrNull()?.message}")
                }
                busy = false
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("تنظیمات", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------------- Appearance ----------------
            SettingsGroup("ظاهر برنامه", Icons.Default.Palette) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.LIGHT -> "روشن"
                            ThemeMode.DARK -> "تاریک"
                            ThemeMode.SYSTEM -> "سیستم"
                        }
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { viewModel.setTheme(mode) },
                            label = { Text(label) }
                        )
                    }
                }
                Text("واحد پول", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Currency.entries.forEach { c ->
                        FilterChip(
                            selected = currency == c,
                            onClick = { viewModel.setCurrency(c) },
                            label = { Text(c.persianName) }
                        )
                    }
                }
            }

            // ---------------- Security ----------------
            SettingsGroup("امنیت", Icons.Default.Lock) {
                SwitchRow(
                    title = "قفل برنامه با رمز عبور",
                    subtitle = if (lockEnabled) "برنامه هنگام ورود رمز چهار رقمی می‌خواهد"
                    else "با فعال‌سازی، ورود به برنامه نیازمند رمز عبور می‌شود",
                    checked = lockEnabled,
                    onCheckedChange = { on ->
                        pinDialog = if (on) PinDialogMode.SET else PinDialogMode.DISABLE
                    }
                )
                SwitchRow(
                    title = "ورود با اثر انگشت",
                    subtitle = when {
                        !lockEnabled -> "ابتدا قفل برنامه را فعال کنید"
                        !BiometricHelper.canAuthenticate(context) -> BiometricHelper.unavailableReason(context)
                        else -> "رمز عبور همیشه به‌عنوان جایگزین در دسترس است"
                    },
                    checked = biometricOn,
                    enabled = lockEnabled,
                    onCheckedChange = { on ->
                        if (!on) {
                            pin.setBiometricEnabled(false)
                            biometricOn = false
                        } else if (!BiometricHelper.canAuthenticate(context)) {
                            say(BiometricHelper.unavailableReason(context))
                        } else if (activity == null) {
                            say("نمایش اثر انگشت ممکن نشد")
                        } else {
                            // Verify once so the user knows it really works before relying on it.
                            BiometricHelper.authenticate(
                                activity = activity,
                                title = "فعال‌سازی ورود با اثر انگشت",
                                subtitle = "اثر انگشت خود را تأیید کنید",
                                negativeText = "لغو",
                                onSuccess = {
                                    pin.setBiometricEnabled(true)
                                    biometricOn = true
                                    say("ورود با اثر انگشت فعال شد")
                                },
                                onError = { e ->
                                    if (e is BiometricHelper.AuthError.Other) say(e.message)
                                }
                            )
                        }
                    }
                )
                if (lockEnabled) {
                    ActionRow(Icons.Default.Password, "تغییر رمز عبور", null) { pinDialog = PinDialogMode.CHANGE }
                }
            }

            // ---------------- Notifications ----------------
            SettingsGroup("اعلان‌ها", Icons.Default.Notifications) {
                SwitchRow(
                    "اعلان به‌روزرسانی قیمت",
                    "وقتی قیمت طلا یا سکه تغییر کند",
                    notifPriceUpdate
                ) { setNotif(SettingsRepository.KEY_NOTIF_PRICE_UPDATE, it) }
                SwitchRow(
                    "اعلان سود",
                    "وقتی پورتفوی شما در سود باشد (فقط با تغییر وضعیت یا تغییر قابل‌توجه)",
                    notifProfit
                ) { setNotif(SettingsRepository.KEY_NOTIF_PROFIT, it) }
                SwitchRow(
                    "اعلان زیان",
                    "وقتی پورتفوی شما در زیان باشد (فقط با تغییر وضعیت یا تغییر قابل‌توجه)",
                    notifLoss
                ) { setNotif(SettingsRepository.KEY_NOTIF_LOSS, it) }
                SwitchRow(
                    "اعلان روزانه قیمت",
                    "هر روز ساعت ۹ صبح",
                    notifDaily
                ) { setNotif(SettingsRepository.KEY_DAILY_PRICE_NOTIF, it) }
                SwitchRow(
                    "نمایش مبالغ در اعلان",
                    "اگر خاموش باشد، اعلان‌ها فقط پیام کلی نشان می‌دهند",
                    notifShowFinance
                ) { viewModel.setNotifSetting(SettingsRepository.KEY_SHOW_FINANCE_IN_NOTIF, it) }
                Text(
                    "قیمت‌ها هر ۳ ساعت در پس‌زمینه به‌روز می‌شوند (در صورت اجازه‌ی اندروید). " +
                        "دارایی‌های فروخته یا هدیه‌شده در محاسبه سود و زیان نیستند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------------- Data ----------------
            SettingsGroup("داده‌ها و پشتیبان", Icons.Default.Storage) {
                ActionRow(
                    Icons.Default.CloudUpload,
                    "پشتیبان‌گیری در Google Drive",
                    "فایل پشتیبان ساخته و به Google Drive شما ارسال می‌شود (حساب و پوشه را خودتان انتخاب می‌کنید)",
                    enabled = !busy
                ) {
                    scope.launch {
                        busy = true
                        when (val r = BackupShare.shareToDrive(context)) {
                            is BackupShare.Outcome.OpenedDrive -> say("برای تکمیل، در Google Drive حساب و پوشه را انتخاب و ذخیره کنید")
                            is BackupShare.Outcome.OpenedChooser -> say("Google Drive روی این دستگاه نصب نیست؛ برنامه‌ی دیگری را انتخاب کنید")
                            is BackupShare.Outcome.Failed -> say("پشتیبان‌گیری ناموفق بود: ${r.message}")
                        }
                        busy = false
                    }
                }
                ActionRow(
                    Icons.Default.Backup,
                    "پشتیبان‌گیری روی گوشی",
                    "ذخیره فایل پشتیبان در حافظه‌ی گوشی",
                    enabled = !busy
                ) { backupLauncher.launch("gold-backup-${System.currentTimeMillis()}.zip") }
                ActionRow(
                    Icons.Default.Restore,
                    "بازیابی از پشتیبان",
                    "جایگزین کردن اطلاعات فعلی با فایل پشتیبان",
                    enabled = !busy
                ) { restoreLauncher.launch(arrayOf("application/zip", "*/*")) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                ActionRow(Icons.Default.Receipt, "فاکتورها", null) { navController.navigate("invoices") }
                ActionRow(Icons.Default.BarChart, "گزارش‌ها", null) { navController.navigate("reports") }
            }

            // ---------------- About (version + developer only) ----------------
            SettingsGroup("درباره", Icons.Default.Info) {
                InfoRow("نسخه برنامه", JalaliCalendar.toPersianDigits(BuildConfig.VERSION_NAME))
                InfoRow("توسعه‌دهنده", "حسن رضائی (Hassan Rezaei)")
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    // ---------------- dialogs ----------------
    when (pinDialog) {
        PinDialogMode.SET -> PasswordDialog(
            title = "ایجاد رمز عبور",
            hint = "یک رمز چهار رقمی انتخاب کنید و آن را فراموش نکنید.",
            labels = listOf("رمز جدید", "تکرار رمز"),
            confirmText = "فعال‌سازی",
            onDismiss = { pinDialog = PinDialogMode.NONE }
        ) { v ->
            when {
                !pin.isValidPin(v[0]) -> "رمز عبور باید دقیقاً ۴ رقم باشد"
                v[0] != v[1] -> "تکرار رمز با رمز جدید یکسان نیست"
                !pin.setPin(v[0]) -> "ذخیره‌ی رمز ممکن نشد"
                else -> {
                    lockEnabled = true
                    biometricOn = false
                    pinDialog = PinDialogMode.NONE
                    say(
                        if (BiometricHelper.canAuthenticate(context)) "قفل برنامه فعال شد. می‌توانید ورود با اثر انگشت را هم روشن کنید."
                        else "قفل برنامه فعال شد"
                    )
                    null
                }
            }
        }
        PinDialogMode.DISABLE -> PasswordDialog(
            title = "غیرفعال کردن قفل",
            hint = "برای خاموش کردن قفل، رمز عبور فعلی را وارد کنید.",
            labels = listOf("رمز فعلی"),
            confirmText = "غیرفعال کن",
            onDismiss = { pinDialog = PinDialogMode.NONE }
        ) { v ->
            verifyMessage(pin.verifyPin(v[0])) ?: run {
                pin.clearPin()
                lockEnabled = false
                biometricOn = false
                pinDialog = PinDialogMode.NONE
                say("قفل برنامه غیرفعال شد")
                null
            }
        }
        PinDialogMode.CHANGE -> PasswordDialog(
            title = "تغییر رمز عبور",
            hint = null,
            labels = listOf("رمز فعلی", "رمز جدید", "تکرار رمز جدید"),
            confirmText = "ذخیره",
            onDismiss = { pinDialog = PinDialogMode.NONE }
        ) { v ->
            when {
                !pin.isValidPin(v[1]) -> "رمز جدید باید دقیقاً ۴ رقم باشد"
                v[1] != v[2] -> "تکرار رمز جدید یکسان نیست"
                else -> verifyMessage(pin.verifyPin(v[0])) ?: run {
                    val keepBiometric = pin.isBiometricEnabled()
                    pin.setPin(v[1])
                    pin.setBiometricEnabled(keepBiometric)
                    pinDialog = PinDialogMode.NONE
                    say("رمز عبور تغییر کرد")
                    null
                }
            }
        }
        PinDialogMode.NONE -> Unit
    }

    if (restoreDone) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(24.dp),
            title = { Text("بازیابی انجام شد") },
            text = { Text("اطلاعات با موفقیت بازیابی شد. برنامه اکنون بسته می‌شود؛ دوباره آن را باز کنید.") },
            confirmButton = {
                TextButton(onClick = {
                    activity?.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }) { Text("بستن برنامه") }
            }
        )
    }
}

/** null = password correct, otherwise the message to show. */
private fun verifyMessage(r: PinManager.VerifyResult): String? = when (r) {
    is PinManager.VerifyResult.Success -> null
    is PinManager.VerifyResult.Wrong ->
        "رمز عبور نادرست است (" + JalaliCalendar.toPersianDigits("${r.attemptsLeftInRound}") + " تلاش دیگر)"
    is PinManager.VerifyResult.Locked ->
        "تلاش‌های ناموفق زیاد بود؛ " + JalaliCalendar.toPersianDigits("${r.secondsLeft}") + " ثانیه دیگر دوباره تلاش کنید"
    is PinManager.VerifyResult.Invalid -> "رمز عبور باید ۴ رقم باشد"
}

@Composable
private fun PasswordDialog(
    title: String,
    hint: String?,
    labels: List<String>,
    confirmText: String,
    onDismiss: () -> Unit,
    /** Returns an error message to keep the dialog open, or null when handled. */
    onSubmit: (List<String>) -> String?
) {
    val values = remember { mutableStateListOf(*Array(labels.size) { "" }) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium)
                labels.forEachIndexed { i, label ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { raw ->
                            values[i] = MoneyUtils.normalizeDigits(raw).filter { it in '0'..'9' }
                                .take(PinManager.PIN_LENGTH)
                            error = null
                        },
                        label = { Text(label) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { error = onSubmit(values.toList()) }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }
    )
}

@Composable
private fun SettingsGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(icon, size = 36)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
