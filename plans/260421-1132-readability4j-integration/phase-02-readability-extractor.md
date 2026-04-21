# Phase 02: ReadabilityExtractor Wrapper
**Status:** ✅ Completed
**Dependencies:** Phase 01 (Dependency phải build thành công)
**Ước tính:** ~30 phút

---

## Objective

Tạo class `ReadabilityExtractor` — một wrapper mỏng quanh Readability4J, implement interface `SiteContentExtractor` để tương thích với pipeline hiện tại.

## Thiết kế Class

```
ReadabilityExtractor
├── implements SiteContentExtractor (interface hiện tại)
├── supports(url) → true (xử lý mọi URL, dùng khi không có site-specific)
├── extractByReadability(doc, html, url) → String? (phương thức chính MỚI)
├── extractByRegex(html) → null (không dùng regex)
└── extractByJsoup(doc, url) → null (không dùng jsoup riêng, delegate cho Readability4J)
```

> **Quyết định thiết kế:** ReadabilityExtractor **KHÔNG** kế thừa `SiteContentExtractor.extractByRegex()`/`extractByJsoup()` truyền thống. Nó có phương thức riêng `extractByReadability()` vì Readability4J cần cả `url` + `html` (khác với interface hiện tại chia Regex vs Jsoup).

## Requirements

### Functional
- [x] Gọi `Readability4J(url, html).parse()` để bóc tách
- [x] Trả về `article.textContent` (plain text) — phù hợp cho Gemini API
- [x] Fallback sang `article.content` (clean HTML) nếu textContent quá ngắn
- [x] Dọn dẹp output: trim, loại bỏ whitespace thừa, newlines liên tục
- [x] Trả `null` nếu content < 200 ký tự (ngưỡng chất lượng hiện tại)

### Non-Functional
- [x] Thread-safe: Readability4J tạo instance mới mỗi lần → OK
- [x] Memory-safe: Không giữ reference đến DOM sau khi parse xong
- [x] Exception-safe: Wrap trong try-catch, trả null nếu Readability4J crash

## Implementation Steps

### 1. Tạo `ReadabilityExtractor.kt`

**Path:** `app/src/main/java/com/skul9x/rssreader/data/network/extractors/ReadabilityExtractor.kt`

```kotlin
package com.skul9x.rssreader.data.network.extractors

import com.skul9x.rssreader.utils.HtmlCleaner
import net.dankito.readability4j.Readability4J
import org.jsoup.nodes.Document

/**
 * Content extractor powered by Mozilla's Readability algorithm (via Readability4J).
 * 
 * Used as the "smart fallback" between site-specific extractors and generic regex/jsoup.
 * Readability4J analyzes the DOM structure to identify the main article content,
 * similar to Firefox's Reader View.
 */
class ReadabilityExtractor(private val htmlCleaner: HtmlCleaner) {

    companion object {
        private const val MIN_CONTENT_LENGTH = 200
        private const val TAG = "ReadabilityExtractor"
    }

    /**
     * Extract article content using Readability4J algorithm.
     * 
     * @param html Raw HTML string of the page
     * @param url  URL of the page (used by Readability4J for resolving relative links)
     * @return Cleaned text content, or null if extraction fails/content too short
     */
    fun extract(html: String, url: String): String? {
        return try {
            val readability = Readability4J(url, html)
            val article = readability.parse()
            
            // Strategy 1: Plain text (best for Gemini API - fewer tokens)
            val textContent = article.textContent?.trim()
            if (textContent != null && textContent.length > MIN_CONTENT_LENGTH) {
                return cleanupText(textContent)
            }
            
            // Strategy 2: Clean HTML → strip tags → plain text
            val htmlContent = article.content
            if (htmlContent != null) {
                val cleaned = htmlCleaner.clean(htmlContent)
                if (cleaned.length > MIN_CONTENT_LENGTH) {
                    return cleaned
                }
            }
            
            null // Readability4J couldn't extract meaningful content
        } catch (e: Exception) {
            null // Swallow exception, let caller fallback to GenericExtractor
        }
    }
    
    /**
     * Clean up extracted text: normalize whitespace, remove excessive newlines.
     */
    private fun cleanupText(text: String): String {
        return text
            .replace(Regex("\\n{3,}"), "\n\n")    // Max 2 consecutive newlines
            .replace(Regex("[ \\t]{2,}"), " ")      // Collapse spaces/tabs
            .trim()
    }
}
```

### 2. Quyết định quan trọng: `textContent` vs `content`

| Property | Output | Dùng cho |
|----------|--------|----------|
| `article.textContent` | Plain text thuần (không HTML tag) | ✅ **Gemini API** (tiết kiệm token, không rác HTML) |
| `article.content` | HTML đã dọn dẹp (`<div>`, `<p>`, `<h2>`...) | Hiển thị bài viết trong WebView |
| `article.title` | Tiêu đề bài viết | Đã có `TitleExtractor` riêng |
| `article.excerpt` | Mô tả ngắn | Không cần (đã lấy từ RSS feed) |

**→ Chọn `textContent` làm ưu tiên** vì mục đích chính là gửi cho Gemini tóm tắt.

### 3. Xử lý edge cases

```kotlin
// Edge case 1: HTML quá ngắn (< 500 chars) → skip Readability4J
// Lý do: Readability4J cần đủ DOM nodes để scoring. HTML ngắn → kết quả rác
if (html.length < 500) return null

// Edge case 2: Readability4J trả về navigation text
// Lý do: Một số trang sidebar-heavy khiến Readability4J chọn nhầm container
// Giải pháp: Kiểm tra tỉ lệ link text / total text
val linkDensity = countLinks(article) / totalLength
if (linkDensity > 0.5) return null  // Quá nhiều links → navigation, không phải article

// Edge case 3: Trang web tiếng Việt có dấu → encoding
// Readability4J dùng Jsoup bên trong → charset detection tự động → OK
```

### 4. Viết unit test cơ bản

**Path:** `app/src/test/java/com/skul9x/rssreader/data/network/extractors/ReadabilityExtractorTest.kt`

Tests cần viết:
- [x] `testExtractFromValidArticle`: HTML có `<article>` → trả về text
- [x] `testExtractFromEmptyHtml`: HTML trống → trả `null`
- [x] `testExtractFromShortHtml`: HTML < 500 chars → trả `null`
- [x] `testExtractFromNavigationPage`: HTML toàn links → trả `null`
- [x] `testVietnameseEncoding`: HTML tiếng Việt có dấu → bóc tách đúng

## Files to Create/Modify

| File | Action | Mô tả |
|------|--------|-------|
| `extractors/ReadabilityExtractor.kt` | **Tạo mới** | Wrapper class chính |
| `extractors/ReadabilityExtractorTest.kt` | **Tạo mới** | Unit tests |

## Kiểm tra trước khi qua Phase 03

- [x] `ReadabilityExtractor` compile thành công
- [x] Unit tests pass (ít nhất 4/5 test cases)
- [x] Thử gọi thủ công với HTML mẫu từ 1 trang web bất kỳ → trả content hợp lệ

## Notes

- ReadabilityExtractor **KHÔNG** implement `SiteContentExtractor` interface vì interface đó chia thành `extractByRegex()` và `extractByJsoup()` — không phù hợp với Readability4J (cần cả url + html). Thay vào đó, nó được gọi trực tiếp từ `ContentExtractorRegistry` như một tầng riêng.
- Nếu sau này cần `article.content` (HTML) cho WebView reader mode → dễ dàng thêm method `extractHtml()`.

---
**Previous Phase:** ← [Phase 01: Dependency & ProGuard](./phase-01-dependency-setup.md)
**Next Phase:** → [Phase 03: Pipeline Integration](./phase-03-pipeline-integration.md)
