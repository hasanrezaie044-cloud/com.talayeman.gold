# طلای من (Gold & Coin Asset Management)

برنامه اندروید بومی و آفلاین‌محور برای مدیریت دارایی‌های فیزیکی طلا و سکه در ایران.

**Application ID:** `com.talayeman.gold`
**Version:** 1.1.0

---

## هدف

کاربر می‌تواند تمام طلا و سکه‌های فیزیکی خود را ثبت کند، وزن و عیار را با واحدهای سوت / میلی‌گرم / گرم / مثقال وارد کند، فاکتور و عکس ضمیمه کند، ارزش فعلی پورتفوی را بر اساس قیمت بازار ایران محاسبه کند، سود/ضرر ببیند و اعلان قیمت و هشدار دریافت کند — همه بدون نیاز به حساب کاربری یا سرور ابری.

اینترنت فقط برای به‌روزرسانی قیمت زنده بازار طلای ایران لازم است. بقیه قابلیت‌ها کاملاً آفلاین کار می‌کنند.

---

## ویژگی‌ها

- **مدیریت دارایی:** طلای ۱۸/۲۴ عیار، جواهرات، آب‌شده، شمش، سکه امامی، بهار آزادی، نیم، ربع، گرمی، پارسیان، سفارشی
- **سیستم وزن:** سوت ↔ میلی‌گرم ↔ گرم ↔ مثقال (تبدیل دقیق با نمایش همزمان)
- **قیمت خرید:** قیمت، اجرت ساخت، مالیات، سایر هزینه‌ها، فروشنده، تاریخ
- **عکس و فاکتور:** چند عکس و چند صفحه فاکتور به ازای هر دارایی
- **قیمت بازار ایران:** ۱۸ عیار، ۲۴ عیار، مثقال، سکه‌ها — با کش محلی
- **ماشین حساب:** تبدیل وزن، ارزش طلا، قدرت خرید، سود/ضرر
- **پورتفوی:** ارزش کل، سود/ضرر، وزن طلا، تعداد سکه
- **گزارش‌ها:** خلاصه و توزیع دارایی
- **اعلان‌ها:** قیمت روزانه، سود/ضرر، هشدار قیمت و پورتفوی
- **قفل اثر انگشت / بیومتریک**
- **پشتیبان‌گیری و بازیابی محلی (ZIP قابل انتقال)**
- **تم روشن / تاریک / سیستم**
- **رابط کاملاً فارسی و RTL**

---

## معماری

```
UI (Compose)
  ↓
ViewModel (StateFlow)
  ↓
Repository
  ↓
Room DB  |  MarketPriceService → API
```

- **Kotlin + Jetpack Compose + Material 3**
- **Room** برای ذخیره‌سازی آفلاین
- **WorkManager** برای به‌روزرسانی پس‌زمینه و اعلان‌ها
- **BiometricPrompt** برای قفل برنامه
- **BigDecimal** برای محاسبات مالی دقیق
- وزن canonical داخلی: میلی‌گرم (Long)

---

## ساختار پروژه

```
gold-management/
├── app/
│   ├── src/main/
│   │   ├── java/com/talayeman/gold/
│   │   │   ├── data/          # Room, DAO, Repository, Remote
│   │   │   ├── domain/        # Models, Use cases
│   │   │   ├── ui/            # Compose screens, theme, navigation
│   │   │   ├── worker/        # WorkManager
│   │   │   ├── service/       # Notifications
│   │   │   ├── util/          # Weight, Money, Backup, Biometric
│   │   │   └── MainActivity.kt
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── .github/workflows/android-release.yml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---

## ساخت محلی

### پیش‌نیاز
- JDK 17
- Android SDK (API 35)
- Android Studio Hedgehog یا جدیدتر (اختیاری)

```bash
# کلون
git clone <repo-url>
cd gold-management

# ساخت debug
./gradlew assembleDebug

# ساخت release (نیاز به keystore)
export KEYSTORE_PATH=/path/to/keystore.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
./gradlew assembleRelease
```

خروجی APK:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

---

## ساخت با GitHub Actions

Workflow: `.github/workflows/android-release.yml`

- Trigger: push به `main` یا `workflow_dispatch`
- خروجی: APK امضاشده (نه AAB)
- Artifact name: `gold-management-release-<version>`

### Secrets مورد نیاز

| Secret | توضیح |
|--------|--------|
| `KEYSTORE_BASE64` | فایل keystore به صورت Base64 |
| `KEYSTORE_PASSWORD` | رمز keystore |
| `KEY_ALIAS` | نام alias |
| `KEY_PASSWORD` | رمز key |

اگر secrets ناقص باشند، build با پیام واضح **fail** می‌شود.
از debug signing یا keystore موقت استفاده نمی‌شود.

ساخت Base64 keystore:
```bash
base64 -w 0 your-release.keystore > keystore.b64
```

---

## پیکربندی API قیمت بازار

کلاس: `data/remote/MarketPriceService.kt`

1. `baseUrl` را به ارائه‌دهنده معتبر قیمت طلای ایران تغییر دهید.
2. مدل `GoldPriceResponse` را با ساختار JSON واقعی تطبیق دهید.
3. در صورت نیاز یک fallback ثانویه در `fetchFromFallback()` پیاده‌سازی کنید.

معماری طوری است که UI مستقیماً به API وصل نیست و می‌توان provider را عوض کرد بدون تغییر UI.

قیمت‌های موفق در Room کش می‌شوند. در حالت آفلاین از آخرین قیمت ذخیره‌شده استفاده می‌شود و صریحاً «ذخیره‌شده» نمایش داده می‌شود.

---

## پشتیبان‌گیری

- از تنظیمات → پشتیبان‌گیری
- فایل ZIP شامل دیتابیس + فایل‌های پیوست
- قابل انتقال به گوشی دیگر
- بازیابی از همان بخش Settings

---

## اعلان‌ها

- کانال‌های جدا: قیمت، هشدار، پورتفوی
- WorkManager برای به‌روزرسانی دوره‌ای (بدون سرویس مداوم)
- گزینه نمایش/عدم نمایش جزئیات مالی در اعلان

---

## قفل بیومتریک

- تنظیمات → امنیت → قفل اثر انگشت
- از `BiometricPrompt` با fallback به قفل دستگاه
- هیچ داده اثر انگشتی ذخیره نمی‌شود

---

## واحد وزن

| واحد | معادل داخلی |
|------|-------------|
| ۱ سوت | ۱ میلی‌گرم |
| ۱ گرم | ۱۰۰۰ میلی‌گرم |
| ۱ مثقال | ۴۶۰۸ میلی‌گرم (استاندارد بازار ایران) |

---

## واحد پول

- تومان / ریال (۱ تومان = ۱۰ ریال)
- هرگز به‌صورت خاموش مخلوط نمی‌شوند

---

## حریم خصوصی

- داده فقط محلی
- بدون حساب کاربری
- بدون تبلیغات
- بدون analytics اجباری
- بدون GPS / SMS / Contacts

---

## مجوزها

- `INTERNET` — فقط قیمت بازار
- `POST_NOTIFICATIONS` — اعلان‌ها
- `USE_BIOMETRIC` — قفل برنامه

---

## نسخه

- `versionName`: 1.1.0
- `versionCode`: 1
- در `app/build.gradle.kts` متمرکز است

---

## نکات مهم

- ارزش پورتفوی **تخمینی** است و ممکن است با قیمت واقعی معامله متفاوت باشد.
- در صورت قطع اینترنت، از آخرین قیمت کش‌شده استفاده می‌شود.
- برای production، API قیمت را با ارائه‌دهنده دارای مجوز جایگزین کنید.

---

## تغییرات اخیر
- اصلاح چیدمان فیلد «وزن» و انتخاب واحد در فرم افزودن دارایی
- محاسبه خودکار مبلغ سکه بر اساس قیمت روز موجود در برنامه (قابل ویرایش دستی و بازگشت به خودکار)
- جداکننده هزارگان در تمام فیلدهای مبلغ (ورود اعداد فارسی هم پشتیبانی می‌شود)
- ضمیمه عکس و فاکتور با دوربین یا Photo Picker، پیش‌نمایش، حذف و ذخیره در حافظه داخلی برنامه (سازگار با پشتیبان‌گیری)

جزئیات: `CHANGES.md`
