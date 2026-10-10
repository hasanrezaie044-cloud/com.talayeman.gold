package com.talayeman.gold.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.talayeman.gold.R
import com.talayeman.gold.util.BiometricHelper
import com.talayeman.gold.util.JalaliCalendar
import com.talayeman.gold.util.MoneyUtils
import com.talayeman.gold.util.PinManager
import kotlinx.coroutines.delay

private val Navy0 = Color(0xFF020A18)
private val Navy1 = Color(0xFF061428)
private val Navy2 = Color(0xFF0E1A2E)
private val GoldLight = Color(0xFFF6D77A)
private val GoldMid = Color(0xFFE2B33F)
private val GoldDeep = Color(0xFFB98A1F)
private val Ink = Color(0xFF1A1305)
private val ErrorRed = Color(0xFFFF8A80)

/**
 * Login screen: 4-digit password (salted hash, rate limited) with optional fingerprint.
 * Calls [onUnlocked] only after a verified password / biometric / device-lock recovery.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LockScreen(activity: FragmentActivity, onUnlocked: () -> Unit) {
    val pin = remember { PinManager.get(activity) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockLeft by remember { mutableLongStateOf(pin.lockSecondsLeft()) }
    var showForgot by remember { mutableStateOf(false) }
    val biometricReady = remember { pin.isBiometricEnabled() && BiometricHelper.canAuthenticate(activity) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    // Dark status bar for this dark screen; restored when the lock screen goes away.
    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)
        val prevLight = controller.isAppearanceLightStatusBars
        val prevColor = window.statusBarColor
        controller.isAppearanceLightStatusBars = false
        window.statusBarColor = Navy0.toArgb()
        onDispose {
            controller.isAppearanceLightStatusBars = prevLight
            window.statusBarColor = prevColor
        }
    }

    fun focusInput() {
        runCatching {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    fun startBiometric() {
        if (!biometricReady) return
        keyboard?.hide()
        BiometricHelper.authenticate(
            activity = activity,
            onSuccess = onUnlocked,
            onError = { e ->
                when (e) {
                    is BiometricHelper.AuthError.Cancelled -> focusInput()
                    is BiometricHelper.AuthError.LockedOut -> {
                        error = "اثر انگشت موقتاً مسدود شد؛ با رمز عبور وارد شوید."
                        focusInput()
                    }
                    is BiometricHelper.AuthError.Other -> {
                        error = e.message
                        focusInput()
                    }
                }
            }
        )
    }

    fun submit(code: String) {
        if (code.length != PinManager.PIN_LENGTH) return
        when (val r = pin.verifyPin(code)) {
            is PinManager.VerifyResult.Success -> onUnlocked()
            is PinManager.VerifyResult.Wrong -> {
                error = "رمز عبور نادرست است. " +
                    JalaliCalendar.toPersianDigits("${r.attemptsLeftInRound}") + " تلاش دیگر باقی مانده است."
                input = ""
            }
            is PinManager.VerifyResult.Locked -> {
                lockLeft = r.secondsLeft
                error = null
                input = ""
            }
            is PinManager.VerifyResult.Invalid -> {
                error = "رمز عبور معتبر نیست."
                input = ""
            }
        }
    }

    // Countdown while the screen is blocked after too many wrong attempts.
    LaunchedEffect(lockLeft > 0) {
        while (lockLeft > 0) {
            delay(1000)
            lockLeft = pin.lockSecondsLeft()
        }
    }

    LaunchedEffect(Unit) {
        if (biometricReady && lockLeft == 0L) startBiometric() else focusInput()
    }

    val locked = lockLeft > 0

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Navy0, Navy1, Navy0)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.login_emblem),
                contentDescription = null,
                modifier = Modifier.size(150.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "طلای من",
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(GoldLight, GoldDeep)),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "ورود امن به دارایی‌های شما",
                color = Color(0xFFE9EEF7),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            // Hidden input; the four circles below mirror it.
            BasicTextField(
                value = input,
                onValueChange = { raw ->
                    val digits = MoneyUtils.normalizeDigits(raw).filter { it in '0'..'9' }
                        .take(PinManager.PIN_LENGTH)
                    if (!locked) {
                        input = digits
                        error = null
                        if (digits.length == PinManager.PIN_LENGTH) submit(digits)
                    }
                },
                enabled = !locked,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit(input) }),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .focusRequester(focusRequester)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { if (!locked) focusInput() }
            ) {
                repeat(PinManager.PIN_LENGTH) { i ->
                    val filled = i < input.length
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Navy2)
                            .border(BorderStroke(1.4.dp, if (filled) GoldLight else GoldDeep), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (filled) {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(GoldLight))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            val message = when {
                locked -> "تلاش‌های ناموفق زیاد بود. " +
                    JalaliCalendar.toPersianDigits("$lockLeft") + " ثانیه دیگر دوباره تلاش کنید."
                else -> error
            }
            if (message != null) {
                Text(message, color = ErrorRed, fontSize = 13.sp, textAlign = TextAlign.Center)
            } else {
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(14.dp))

            val canSubmit = input.length == PinManager.PIN_LENGTH && !locked
            val shape = RoundedCornerShape(16.dp)
            Button(
                onClick = { submit(input) },
                enabled = canSubmit,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Ink,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = Ink.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(shape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(GoldLight, GoldMid, GoldLight).map { it.copy(alpha = if (canSubmit) 1f else 0.45f) }
                        )
                    )
            ) {
                Text("ورود", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            if (biometricReady) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f).height(1.dp).background(GoldDeep.copy(alpha = 0.6f)))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = !locked) { startBiometric() }
                            .padding(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .border(BorderStroke(1.dp, GoldMid), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = "ورود با اثر انگشت", tint = GoldLight, modifier = Modifier.size(30.dp))
                        }
                        Text("اثر انگشت", color = GoldLight, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(Modifier.weight(1f).height(1.dp).background(GoldDeep.copy(alpha = 0.6f)))
                }
            }

            Spacer(Modifier.height(18.dp))
            TextButton(onClick = { showForgot = true }) {
                Text("رمز عبور را فراموش کرده‌ام", color = Color(0xFFB8C4D9), fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.VerifiedUser, null, tint = GoldMid, modifier = Modifier.size(22.dp))
                Text(
                    if (biometricReady) "محافظت با رمز و اثر انگشت" else "محافظت با رمز عبور",
                    color = GoldLight.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
            }
        }
    }

    if (showForgot) {
        ForgotPasswordDialog(
            activity = activity,
            pin = pin,
            onDismiss = { showForgot = false },
            onReset = {
                showForgot = false
                onUnlocked()
            }
        )
    }
}

@Composable
private fun ForgotPasswordDialog(
    activity: FragmentActivity,
    pin: PinManager,
    onDismiss: () -> Unit,
    onReset: () -> Unit
) {
    var info by remember { mutableStateOf<String?>(null) }
    val deviceLockOk = remember { BiometricHelper.canUseDeviceCredential(activity) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بازیابی رمز عبور") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (deviceLockOk)
                        "برای ادامه، هویت خود را با قفل صفحه‌ی گوشی (الگو، پین یا اثر انگشت گوشی) تأیید کنید. " +
                            "سپس رمز عبور فعلی برنامه حذف می‌شود و می‌توانید در تنظیمات رمز جدیدی بسازید. " +
                            "اطلاعات دارایی‌های شما پاک نمی‌شود."
                    else
                        "برای بازیابی باید روی گوشی قفل صفحه (الگو، پین یا رمز) فعال باشد. " +
                            "در این گوشی قفل صفحه‌ای پیدا نشد، بنابراین امکان بازنشانی رمز وجود ندارد."
                )
                info?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (deviceLockOk) {
                TextButton(onClick = {
                    BiometricHelper.authenticateWithDeviceLock(
                        activity = activity,
                        title = "تأیید هویت",
                        subtitle = "برای بازنشانی رمز برنامه، قفل گوشی را وارد کنید",
                        onSuccess = {
                            pin.clearPin()
                            onReset()
                        },
                        onError = { e ->
                            if (e is BiometricHelper.AuthError.Other) info = e.message
                        }
                    )
                }) { Text("تأیید با قفل گوشی") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}
