# Plan: Tích hợp Readability4J vào Content Extraction Pipeline

**Created:** 2026-04-21T11:32:00+07:00
**Status:** ✅ Completed
**Decision:** Dùng `net.dankito.readability4j:readability4j:1.0.8` (Maven Central, giống ReadYou)

---

## Overview

Nâng cấp hệ thống bóc tách nội dung (Content Extraction) của RSS Reader bằng cách thêm **Readability4J** — một port Kotlin của thuật toán Mozilla Readability.js — vào giữa pipeline hiện tại, giữa lớp Site-Specific Extractors và Generic Extractor.

### Mục tiêu
- Cải thiện chất lượng bóc tách cho **trang web "lạ"** (không phải báo VN quen thuộc)
- Giữ nguyên hiệu suất cho các trang đã có extractor chuyên biệt
- Output sạch cho Gemini API tóm tắt (plain text hoặc clean HTML)
- Không ảnh hưởng đến coroutine/UI thread

### Pipeline mới

```
┌─────────────────────────────────────────────────┐
│ 0. Custom Selector (user-defined)               │ Giữ nguyên
├─────────────────────────────────────────────────┤
│ 1. Site-Specific Extractor (Regex → Jsoup)      │ Giữ nguyên
│    VnExpress, Genk, TuoiTre, DanTri...          │
├─────────────────────────────────────────────────┤
│ 2. ✨ Readability4J (MỚI)                       │ THÊM MỚI
│    Mozilla algorithm → article.textContent      │
├─────────────────────────────────────────────────┤
│ 3. Generic Regex + Jsoup (fallback cũ)          │ HẠ PRIORITY
│    GenericExtractor giữ nguyên code              │
└─────────────────────────────────────────────────┘
```

## Tech Stack
- **Library:** `net.dankito.readability4j:readability4j:1.0.8`
- **Existing:** Jsoup 1.18.1, OkHttp, Kotlin Coroutines
- **Platform:** Android (minSdk 26, targetSdk 34)
- **Build:** Gradle Kotlin DSL

## Phases

| Phase | Name | Status | Progress | Tasks |
|-------|------|--------|----------|-------|
| 01 | Dependency & ProGuard | ✅ Done | 100% | 4 |
| 02 | ReadabilityExtractor Wrapper | ✅ Done | 100% | 5 |
| 03 | Pipeline Integration | ✅ Done | 100% | 6 |
| 04 | Testing & Validation | ✅ Done | 100% | 7 |

**Tổng:** 22 tasks | Ước tính: 2-3 sessions

## Files Impact

### Tạo mới:
- `app/src/main/java/com/skul9x/rssreader/data/network/extractors/ReadabilityExtractor.kt`
- `app/src/test/java/.../ReadabilityExtractorTest.kt`
- `app/src/test/java/.../ContentExtractorRegistryTest.kt`

### Sửa đổi:
- `app/build.gradle.kts` — Thêm dependency
- `app/proguard-rules.pro` — Thêm keep rule
- `app/src/main/java/.../ContentExtractorRegistry.kt` — Chèn tầng Readability
- `gradle/libs.versions.toml` — Thêm version catalog entry

### KHÔNG thay đổi:
- `GenericExtractor.kt` — Giữ nguyên làm fallback
- `ArticleContentFetcher.kt` — Interface không đổi
- Tất cả Site-Specific Extractors — Không động vào

## Quick Commands
- Start Phase 1: `/code phase-01`
- Check progress: `/next`
- Save context: `/save-brain`
