# Talaye Man – v1.2.0 (auth fix, Drive backup, sold/gifted, notifications, Jalali, new UI)

## 1. Authentication (was completely broken)
Root causes found: (a) `MainActivity` computed `unlocked = !biometricEnabled` from an *asynchronously loaded*
setting whose initial value is `false`, so the lock screen never appeared; (b) the lock screen unlocked
automatically whenever no PIN existed, and (c) Settings never had any UI to create a PIN.
- `util/PinManager.kt` rewritten: salted PBKDF2-HMAC-SHA256 (120 000 iterations) in EncryptedSharedPreferences,
  brute-force delay (after every 5 wrong attempts: 30 s, 60 s, 2 min … max 15 min), fingerprint flag stored with it.
  "Lock enabled" == a password exists; read synchronously, so no protected screen is ever composed before login.
- `util/BiometricHelper.kt` rewritten: fingerprint prompt with "use password" button, lockout/cancel handling,
  device-lock prompt for forgotten-password recovery.
- NEW `ui/auth/LockScreen.kt` (login screen: 4-digit password, auto-submit, error/lockout messages, fingerprint button).
- `MainActivity.kt`: lock gate + re-lock after 30 s in background.
- `SettingsScreen.kt`: create / change / disable password, fingerprint switch (verified once when enabled).
Password is a 4-digit code (matches the login design). Existing installs had no usable PIN, so nothing to migrate.

## 2. Google Drive backup
NEW `util/BackupShare.kt`: builds the normal backup .zip and hands it to the Google Drive app (account and folder are
chosen in Drive; no OAuth client / API key / Play-Services sign-in required). Falls back to the Android share sheet.
`BackupManager`: no longer closes the live database for a backup (WAL checkpoint instead), zip-slip protection and
stale WAL/SHM removal on restore, restore fails clearly if the DB is missing.

## 3. Optional "save photo to gallery"
NEW `util/GallerySaver.kt` (MediaStore on Android 10+, no permission). Small save button on camera-captured thumbnails and a
button in the full-screen preview. Never automatic. `WRITE_EXTERNAL_STORAGE` (maxSdk 28) is requested only on Android 8-9
at the moment the user taps save.

## 4. Sold / Gifted assets
- DB version **2** with a real `Migration(1, 2)` (adds status, statusDate, soldPrice, statusNote; existing rows = ACTIVE).
  `fallbackToDestructiveMigration()` was removed so a schema change can never silently wipe user data.
- `PortfolioCalculator` counts ACTIVE assets only (value, cost, weight, coin count, profit/loss, notifications, reports).
- Asset detail: "ثبت فروش / ثبت هدیه" (Jalali date, optional sale price, buyer/recipient note), history record with
  realised profit/loss for sales, "بازگرداندن به دارایی فعال".
- Assets screen: "فعال" and "تاریخچه فروش و هدیه" tabs. Editing an asset preserves its status.

## 5. Notifications
`NotificationHelper.handlePriceUpdate()` after each successful background refresh: price-updated (only if a price changed),
profit and loss (on profit↔loss flip or ≥1-point change; no spam). Each has its own switch in Settings; amounts can be hidden;
the lock-screen version is always generic. Background refresh interval is now 3 h (new unique work name `price_update_periodic_v2`).

## 6. Jalali calendar
NEW `util/JalaliCalendar.kt` (+ unit tests) and `ui/components/JalaliDatePicker.kt`. Dashboard shows today's Persian date,
asset form shows the registration date and uses a Persian-calendar picker for the purchase date, all dates in the app are Jalali.

## 7-10. UI
Calculator: the four mode buttons sit in one bordered frame. About: version + developer only. New navy/gold palette, shapes,
typography without letter-spacing (breaks Persian joining), bottom-nav labels, hero card, rounded cards, new login screen and app icon.
Optional Vazirmatn font (assets/fonts, downloaded by scripts/fetch-fonts.sh in CI).

## Other
`allowBackup="false"` (financial data must not go to Google auto-backup; the password is Keystore-bound and cannot be restored anyway).
Version 1.2.0 (code 3). New tests: JalaliCalendarTest, AssetStatusPortfolioTest.

---

# Talaye Man – v1.1.0 fixes (asset form, coin pricing, money formatting, photos)

## Files changed
- ui/screens/assets/AssetEditScreen.kt – weight row layout fix, money fields, coin auto-value, manual override, photo/invoice attachments
- ui/screens/assets/AssetDetailScreen.kt – shows saved photos/invoices (thumbnail + full-screen preview)
- ui/screens/calculator/CalculatorScreen.kt – money inputs use thousands separators
- ui/AppViewModel.kt – saveAssetWithAttachments(); deleteAsset also removes image files
- domain/usecase/PortfolioCalculator.kt – shared coin mapping + autoCoinValue() (same rules as portfolio)
- data/repository/AssetRepository.kt – getAttachmentById / getAttachmentsOnce
- util/MoneyUtils.kt – Persian/Arabic digit normalization in parse(), toInputString()
- AndroidManifest.xml – FileProvider for camera capture (no new permissions)
- NEW: util/MoneyInputFormatter.kt, util/AttachmentStorage.kt, ui/components/MoneyTextField.kt,
  ui/components/AttachmentComponents.kt, res/xml/file_paths.xml
- NEW: app/src/test/... unit tests, .github/workflows/android-debug.yml

## Database
No schema change, DB version stays 1, no migration needed. Existing records untouched.
Editing an asset now keeps its original purchaseDate/createdAt (previously reset on every edit).
