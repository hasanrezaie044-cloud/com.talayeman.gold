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
