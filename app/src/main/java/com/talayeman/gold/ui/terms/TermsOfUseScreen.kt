package com.talayeman.gold.ui.terms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.talayeman.gold.ui.components.AppCard
import com.talayeman.gold.ui.components.IconBadge

/** Title of the terms (shown exactly as provided). */
const val TERMS_TITLE = "سلب مسئولیت و شرایط استفاده"

/** The terms text, paragraph by paragraph (shown exactly as provided). */
val TERMS_PARAGRAPHS = listOf(
    "اپلیکیشن «طلای من» صرفاً یک ابزار شخصی برای مدیریت و پیگیری دارایی‌های طلا و سکه شماست و قیمت‌های نمایش‌داده‌شده از منابع عمومی و شخص ثالث دریافت می‌شوند.",
    "قیمت‌های بازار (طلا، سکه و ...) صرفاً جنبه اطلاع‌رسانی و راهنمایی دارند و قیمت رسمی یا قطعی محسوب نمی‌شوند.",
    "این اپلیکیشن هیچ‌گونه مشاوره مالی، سرمایه‌گذاری یا توصیه خرید/فروش ارائه نمی‌دهد.",
    "دقت، کامل بودن و به‌روز بودن لحظه‌ای قیمت‌ها تضمین نمی‌شود. ممکن است به‌دلیل تأخیر در به‌روزرسانی منابع، مشکلات شبکه یا تغییرات ناگهانی بازار، اختلاف وجود داشته باشد.",
    "مسئولیت هرگونه تصمیم‌گیری مالی (خرید، فروش یا نگهداری) کاملاً بر عهده کاربر است. قبل از هر معامله حتماً قیمت را از منابع معتبر (اتحادیه طلا و جواهر، طلافروشی معتبر یا سایت‌های رسمی) تأیید کنید.",
    "توسعه‌دهنده این اپلیکیشن هیچ مسئولیتی در قبال ضرر، زیان یا اختلاف ناشی از اتکا به اطلاعات نمایش‌داده‌شده، تأخیر در به‌روزرسانی یا خطای داده‌ها ندارد.",
    "با استفاده از این اپلیکیشن، شما این شرایط را پذیرفته‌اید."
)

/**
 * Gate shown before anything else until the user accepts. Declining (or pressing Back) closes the
 * app, so nothing of the app can be used without acceptance.
 */
@Composable
fun TermsOfUseScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    var checked by rememberSaveable { mutableStateOf(false) }

    // Back must not bypass the gate.
    BackHandler { onDecline() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Default.Gavel, size = 44)
                Text(TERMS_TITLE, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            AppCard(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TERMS_PARAGRAPHS.forEach {
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = { checked = it })
                Text(
                    "شرایط استفاده و سلب مسئولیت را خوانده‌ام و می‌پذیرم",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = onAccept,
                enabled = checked,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("پذیرش و ادامه", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onDecline,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("عدم پذیرش و خروج")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
