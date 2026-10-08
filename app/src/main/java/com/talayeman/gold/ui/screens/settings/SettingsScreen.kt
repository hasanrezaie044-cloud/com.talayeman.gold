package com.talayeman.gold.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.talayeman.gold.domain.model.Currency
import com.talayeman.gold.domain.model.ThemeMode
import com.talayeman.gold.ui.AppViewModel
import com.talayeman.gold.util.BackupManager
import com.talayeman.gold.util.BiometricHelper
import com.talayeman.gold.util.PinManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: AppViewModel = viewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val tmp = File(context.cacheDir, "backup.zip")
                val result = BackupManager(context).createBackup(tmp)
                if (result.isSuccess) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        tmp.inputStream().use { it.copyTo(out) }
                    }
                    message = "پشتیبان‌گیری با موفقیت انجام شد"
                } else {
                    message = "خطا در پشتیبان‌گیری: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val tmp = File(context.cacheDir, "restore.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
                val result = BackupManager(context).restoreBackup(tmp)
                message = if (result.isSuccess) "بازیابی با موفقیت انجام شد. برنامه را مجدداً باز کنید."
                else "خطا در بازیابی: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("تنظیمات", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ظاهر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    ThemeMode.LIGHT -> "روشن"
                    ThemeMode.DARK -> "تاریک"
                    ThemeMode.SYSTEM -> "سیستم"
                }
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(selected = themeMode == mode, onClick = { viewModel.setTheme(mode) })
                    Text(label, Modifier.padding(start = 8.dp, top = 12.dp))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("واحد پول", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "تمام مبالغ به تومان نمایش داده می‌شوند.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("امنیت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("قفل اثر انگشت", Modifier.padding(top = 12.dp))
                Switch(
                    checked = biometricEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !BiometricHelper.canAuthenticate(context)) {
                            message = "احراز هویت زیست‌سنجی در این دستگاه در دسترس نیست"
                        } else {
                            viewModel.setBiometric(enabled)
                        }
                    }
                )
            }

            var showPinDialog by remember { mutableStateOf(false) }
            var pin1 by remember { mutableStateOf("") }
            var pin2 by remember { mutableStateOf("") }
            val pinManager = remember { PinManager(context) }
            Text(
                if (pinManager.hasPin()) "PIN تنظیم شده است" else "PIN تنظیم نشده",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = { showPinDialog = true }, Modifier.fillMaxWidth()) {
                Text(if (pinManager.hasPin()) "تغییر PIN چهار رقمی" else "تنظیم PIN چهار رقمی")
            }
            if (pinManager.hasPin()) {
                TextButton(onClick = {
                    pinManager.clearPin()
                    message = "PIN حذف شد"
                }) { Text("حذف PIN") }
            }
            if (showPinDialog) {
                AlertDialog(
                    onDismissRequest = { showPinDialog = false },
                    title = { Text("PIN چهار رقمی") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = pin1,
                                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin1 = it },
                                label = { Text("PIN جدید") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                            )
                            OutlinedTextField(
                                value = pin2,
                                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin2 = it },
                                label = { Text("تکرار PIN") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            when {
                                pin1.length != 4 -> message = "PIN باید ۴ رقم باشد"
                                pin1 != pin2 -> message = "تکرار PIN مطابقت ندارد"
                                else -> {
                                    pinManager.setPin(pin1)
                                    message = "PIN ذخیره شد"
                                    showPinDialog = false
                                    pin1 = ""
                                    pin2 = ""
                                }
                            }
                        }) { Text("ذخیره") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPinDialog = false }) { Text("لغو") }
                    }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("اعلان‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { /* Navigate to detailed notification settings if expanded */ }) {
                Text("تنظیمات اعلان‌ها")
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("داده", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(onClick = { backupLauncher.launch("gold-backup-${System.currentTimeMillis()}.zip") }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Backup, null)
                Spacer(Modifier.width(8.dp))
                Text("پشتیبان‌گیری")
            }
            OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/zip", "*/*")) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Restore, null)
                Spacer(Modifier.width(8.dp))
                Text("بازیابی")
            }
            TextButton(onClick = { navController.navigate("invoices") }) {
                Text("فاکتورها")
            }
            TextButton(onClick = { navController.navigate("reports") }) {
                Text("گزارش‌ها")
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("درباره", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("طلای من — نسخه ۱.۱.۰")
            Text("com.talayeman.gold")
            Text("برنامه شخصی و آفلاین‌محور برای مدیریت دارایی‌های طلا و سکه در ایران.")
            Text("قیمت‌های بازار تخمینی هستند و ممکن است با قیمت واقعی معامله متفاوت باشند.")

            message?.let {
                LaunchedEffect(it) {
                    // Show snackbar-like message
                }
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
