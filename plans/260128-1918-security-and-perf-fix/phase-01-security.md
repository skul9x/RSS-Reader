# Phase 01: Security Hardening
Status: ✅ Complete

## Objective
Securely store Gemini API Keys using `EncryptedSharedPreferences` instead of plain `SharedPreferences`.

## Requirements
- [x] Add `androidx.security:security-crypto` dependency.
- [x] Refactor `ApiKeyManager` to use `EncryptedSharedPreferences`.
- [x] Ensure non-encrypted keys are cleared or migrated (simplest: clear and ask user to re-enter, or just switch storage).

## Implementation Steps
1. [x] Edit `app/build.gradle.kts`:
   - Add `implementation("androidx.security:security-crypto:1.1.0-alpha06")` (or stable if available).
2. [x] Edit `app/src/main/java/com/skul9x/rssreader/data/local/ApiKeyManager.kt`:
   - Initialize `EncryptedSharedPreferences`.
   - Use `MasterKey` for encryption.
   - **Note**: `EncryptedSharedPreferences` constructor requires context.

## Files to Modify
- `app/build.gradle.kts`
- `app/src/main/java/com/skul9x/rssreader/data/local/ApiKeyManager.kt`

---
Next Phase: [SQL Optimization](./phase-02-sql-optimization.md)
