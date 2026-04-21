# Phase 01: Dependency & ProGuard Setup
**Status:** ✅ Completed
**Dependencies:** Không có (phase đầu tiên)
**Ước tính:** ~15 phút

---

## Objective

Thêm thư viện Readability4J vào project và đảm bảo R8/ProGuard không strip class khi build release.

## Requirements

### Functional
- [x] Readability4J có thể import được trong source code
- [x] Build debug thành công
- [x] Build release thành công (ProGuard không strip)

### Non-Functional
- [ ] APK size tăng không quá 100KB
- [ ] Không conflict với Jsoup version hiện tại (1.18.1)

## Implementation Steps

### 1. Thêm version vào `gradle/libs.versions.toml`

```toml
[versions]
readability4j = "1.0.8"

[libraries]
readability4j = { group = "net.dankito.readability4j", name = "readability4j", version.ref = "readability4j" }
```

> **Lý do dùng Version Catalog:** Project đã dùng `libs.xxx` cho tất cả dependencies (Room, Compose, Jsoup...). Giữ nhất quán.

### 2. Thêm dependency vào `app/build.gradle.kts`

```kotlin
// HTML Parsing
implementation(libs.jsoup)
implementation(libs.readability4j) // Readability4J - Mozilla algorithm
```

### 3. Thêm ProGuard keep rule vào `app/proguard-rules.pro`

```proguard
# Readability4J - Keep all classes (thuật toán reflection-based scoring)
-keep class net.dankito.readability4j.** { *; }
-dontwarn net.dankito.readability4j.**
```

> **Lý do:** Readability4J dùng internal reflection để scoring DOM nodes. R8 có thể strip các class không được tham chiếu trực tiếp → crash khi chạy release.

### 4. Sync & Verify

```bash
# Sync gradle
./gradlew --refresh-dependencies

# Verify build debug
./gradlew assembleDebug

# Verify dependency tree (kiểm tra không conflict)
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep readability
```

## Files to Create/Modify

| File | Action | Mô tả |
|------|--------|-------|
| `gradle/libs.versions.toml` | **Sửa** | Thêm version + library entry |
| `app/build.gradle.kts` | **Sửa** | Thêm 1 dòng `implementation` |
| `app/proguard-rules.pro` | **Sửa** | Thêm 2 dòng keep rule |

## Kiểm tra trước khi qua Phase 02

- [x] `./gradlew assembleDebug` thành công
- [x] Trong Android Studio, import `net.dankito.readability4j.Readability4J` không báo lỗi
- [x] Kiểm tra dependency tree: Readability4J dùng Jsoup version nào? Có conflict không?

## Rủi ro & Xử lý

| Rủi ro | Xác suất | Xử lý |
|--------|---------|-------|
| Jsoup conflict (Readability4J phụ thuộc Jsoup cũ hơn) | 🟡 Trung bình | Gradle tự resolve lên bản cao nhất (1.18.1). Nếu lỗi → exclude và force version |
| ProGuard strip quá nhiều | 🟢 Thấp | Thêm `-keep` rộng hơn nếu cần |

## Notes

- Readability4J v1.0.8 phụ thuộc **Jsoup 1.14.x** nhưng Gradle sẽ tự resolve lên **1.18.1** (bản hiện tại của project). Đây là upgrade tương thích vì Jsoup giữ backward compatibility rất tốt.
- **KHÔNG** cần thêm repository mới (Maven Central đã có sẵn trong project).

---
**Next Phase:** → [Phase 02: ReadabilityExtractor Wrapper](./phase-02-readability-extractor.md)
