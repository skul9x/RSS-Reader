# Phase 01: Security Hardening
Status: ✅ Complete

## Objective
Ngăn chặn rò rỉ API Keys và dữ liệu nhạy cảm khi user thực hiện backup (Google Drive backup hoặc ADB backup).

## Requirements
### Functional
- [x] Cấu hình `backup_rules.xml` để loại trừ file chứa API Key (`api_keys_secure.xml`).
- [x] Cập nhật `AndroidManifest.xml` để trỏ đúng vào file rules này.
- [x] Đảm bảo `allowBackup` vẫn là `true` (để user tiện backup setting khác) nhưng **an toàn**.

## Implementation Steps
1.  [x] **Modify** `app/src/main/res/xml/backup_rules.xml`: Thêm rule `<exclude domain="sharedpref" path="api_keys_secure.xml" />`.
2.  [x] **Modify** `app/src/main/res/xml/data_extraction_rules.xml`: Thêm rule tương tự cho Android 12+.
3.  [x] **Verify** `AndroidManifest.xml`: Đảm bảo `android:fullBackupContent` và `android:dataExtractionRules` đã trỏ đúng file.

## Files to Modify
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml` (Nếu cần)
- `app/src/main/AndroidManifest.xml`

## Test Criteria
- [x] Review code: File `backup_rules.xml` phải có dòng exclude `api_keys_secure.xml`.
- [x] (Manual) Build apk release, cài vào máy, thử backup/restore xem key có bị mất không (Expected: Key mất => An toàn, user phải nhập lại).
