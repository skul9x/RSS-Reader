# Phase 04: Testing & Validation
**Status:** ✅ Completed
**Dependencies:** Phase 03 (Pipeline phải hoàn chỉnh)
**Ước tính:** ~45 phút

---

## Objective

Kiểm thử toàn diện pipeline mới, đảm bảo:
1. Các trang VN quen thuộc vẫn dùng site-specific extractor (không regression)
2. Trang web "lạ" (quốc tế, blog cá nhân) được Readability4J xử lý tốt hơn Generic
3. Fallback chain hoạt động đúng thứ tự
4. Build release thành công (ProGuard OK)

## Requirements

### Functional
- [x] Unit test: `ReadabilityExtractor` xử lý đúng các edge case
- [x] Unit test: `ContentExtractorRegistry` gọi đúng thứ tự priority
- [x] Integration test: Pipeline hoạt động end-to-end với HTML thật
- [x] Regression test: Các site-specific extractor không bị ảnh hưởng
- [x] Build release: `./gradlew assembleRelease` thành công

### Non-Functional
- [x] Performance: Parse 1 bài viết dài (10KB HTML) < 500ms
- [x] Memory: Không memory leak (ReadabilityExtractor không giữ DOM reference)

## Implementation Steps

### 1. Unit Test cho `ReadabilityExtractor`

**Path:** `app/src/test/java/com/skul9x/rssreader/data/network/extractors/ReadabilityExtractorTest.kt`

```kotlin
class ReadabilityExtractorTest {
    
    private val htmlCleaner = AndroidHtmlCleaner() // hoặc mock
    private val extractor = ReadabilityExtractor(htmlCleaner)
    
    @Test
    fun `extract from valid article returns content`() {
        val html = """
            <html><head><title>Test Article</title></head>
            <body>
                <nav>Menu items here</nav>
                <article>
                    <h1>Test Article Title</h1>
                    <p>This is a substantial paragraph with enough text to pass the minimum
                    content length threshold. It contains meaningful article content that
                    Readability4J should be able to identify as the main content of the page.
                    The algorithm scores this based on paragraph density and text-to-link ratio.</p>
                    <p>Second paragraph with more detailed information about the topic.
                    Readability4J uses a scoring algorithm similar to Mozilla's Reader View
                    to determine which part of the page contains the actual article content.</p>
                    <p>Third paragraph ensures we have enough content length...</p>
                </article>
                <aside>Sidebar content</aside>
                <footer>Footer here</footer>
            </body></html>
        """.trimIndent()
        
        val result = extractor.extract(html, "https://example.com/article")
        
        assertNotNull(result)
        assertTrue(result!!.length > 200)
        assertTrue(result.contains("substantial paragraph"))
        assertFalse(result.contains("Menu items"))
        assertFalse(result.contains("Sidebar"))
    }
    
    @Test
    fun `extract from empty HTML returns null`() {
        val result = extractor.extract("", "https://example.com")
        assertNull(result)
    }
    
    @Test
    fun `extract from short HTML returns null`() {
        val html = "<html><body><p>Short</p></body></html>"
        val result = extractor.extract(html, "https://example.com")
        assertNull(result)
    }
    
    @Test
    fun `extract from navigation-heavy page returns null or short`() {
        val html = """
            <html><body>
                <nav>
                    <a href="/page1">Link 1</a>
                    <a href="/page2">Link 2</a>
                    <a href="/page3">Link 3</a>
                    <!-- ... many more links ... -->
                </nav>
            </body></html>
        """.trimIndent()
        
        val result = extractor.extract(html, "https://example.com")
        // Should return null (not enough article content)
        assertNull(result)
    }
    
    @Test
    fun `extract preserves Vietnamese diacritics`() {
        val html = """
            <html><head><meta charset="UTF-8"></head>
            <body>
                <article>
                    <p>Đây là một bài viết tiếng Việt với nhiều ký tự đặc biệt.
                    Nội dung bóc tách phải giữ nguyên dấu: ắ, ằ, ẵ, ặ, ố, ồ, ỗ, ộ.
                    Readability4J cần xử lý đúng encoding UTF-8 cho tiếng Việt.</p>
                    <p>Đoạn văn thứ hai cũng có dấu tiếng Việt để kiểm tra kỹ hơn.
                    Hệ thống bóc tách nội dung cần hoạt động chính xác với mọi ngôn ngữ.</p>
                    <p>Đoạn văn thứ ba giúp đảm bảo đủ độ dài nội dung tối thiểu...</p>
                </article>
            </body></html>
        """.trimIndent()
        
        val result = extractor.extract(html, "https://example.vn/article")
        
        assertNotNull(result)
        assertTrue(result!!.contains("tiếng Việt"))
        assertTrue(result.contains("ắ"))
        assertTrue(result.contains("ố"))
    }
}
```

### 2. Unit Test cho `ContentExtractorRegistry` (Priority Chain)

**Path:** `app/src/test/java/com/skul9x/rssreader/data/network/ContentExtractorRegistryTest.kt`

```kotlin
class ContentExtractorRegistryTest {
    
    // Test 1: Site-specific URL → KHÔNG gọi Readability4J
    @Test
    fun `vnexpress URL uses site-specific extractor not readability`() {
        // Arrange: HTML có nội dung VnExpress chuẩn
        // Act: extractContent(doc, html, "https://vnexpress.net/bai-viet-123.html")
        // Assert: Result từ VnExpressExtractor, Readability4J KHÔNG được gọi
    }
    
    // Test 2: Unknown URL → Readability4J được gọi
    @Test
    fun `unknown URL tries readability before generic`() {
        // Arrange: HTML có <article> rõ ràng
        // Act: extractContent(doc, html, "https://unknown-blog.com/post")
        // Assert: Result từ Readability4J (if successful)
    }
    
    // Test 3: Readability4J fail → fallback xuống Generic
    @Test
    fun `readability failure falls back to generic extractor`() {
        // Arrange: HTML tệ, Readability4J trả null
        // Act: extractContent()
        // Assert: Result từ GenericExtractor
    }
    
    // Test 4: Full chain test
    @Test
    fun `full priority chain works correctly`() {
        // Custom > Site-Specific > Readability > Generic
    }
}
```

### 3. Integration Test với HTML thật (Manual)

Chạy app và test với các URL thực tế:

**Nhóm A: Site-Specific (phải dùng extractor cũ, KHÔNG chạy Readability4J)**

| URL | Expected Extractor | Check Log |
|-----|--------------------|-----------|
| `vnexpress.net/bai-viet.html` | VnExpressExtractor | "Regex extraction success" |
| `genk.vn/bai-viet.html` | GenkExtractor | "Jsoup extraction success" |
| `tuoitre.vn/bai-viet.html` | TuoiTreExtractor | Site-specific log |
| `dantri.com.vn/bai-viet.html` | DanTriExtractor | Site-specific log |

**Nhóm B: Unknown URLs (phải thử Readability4J trước)**

| URL | Expected Flow | Check Log |
|-----|--------------|-----------|
| `medium.com/@user/post` | Readability4J → success | "Readability4J extraction success" |
| `techcrunch.com/article` | Readability4J → success | "Readability4J extraction success" |
| `substack.com/p/article` | Readability4J → success | "Readability4J extraction success" |
| `reddit.com/r/sub/post` | Readability4J → fail → Generic | "Readability4J failed, falling back" |

**Nhóm C: Edge Cases**

| URL | Expected | Notes |
|-----|----------|-------|
| Single-page app (JS only) | Readability4J fail → Generic fail → null | Cả hai đều fail |
| PDF link | null | HTML invalid |
| Login-gated page | Short content → null | Content behind paywall |

### 4. Performance Test

```kotlin
@Test
fun `readability extraction completes within 500ms`() {
    val largeHtml = loadTestHtml("large_article_10kb.html") // ~10KB HTML
    
    val startTime = System.currentTimeMillis()
    val result = readabilityExtractor.extract(largeHtml, "https://example.com")
    val elapsed = System.currentTimeMillis() - startTime
    
    assertTrue("Parse took ${elapsed}ms, expected < 500ms", elapsed < 500)
}
```

### 5. Regression Test: Compare Output Quality

```
So sánh output của cùng 1 URL khi:
A) Chỉ dùng GenericExtractor (pipeline cũ)
B) Dùng ReadabilityExtractor (pipeline mới)

Đánh giá:
- Độ dài: B >= A? (Readability thường lấy nhiều content hơn)
- Chất lượng: B có ít "rác" hơn A? (Ít quảng cáo, ít sidebar text)
- Chính xác: B có đúng bài viết chính không?
```

### 6. Build Release

```bash
# Build release APK (kiểm tra ProGuard không strip Readability4J)
./gradlew assembleRelease

# Verify APK size
ls -la app/build/outputs/apk/release/
```

### 7. Checklist cuối cùng

- [x] Tất cả unit tests pass
- [x] Build debug thành công
- [x] Build release thành công
- [x] Test thủ công với ≥ 3 trang VN quen thuộc → vẫn dùng Site-Specific
- [x] Test thủ công với ≥ 3 trang lạ → Readability4J hoạt động
- [x] Không memory leak (check Android Profiler nếu cần)
- [x] Log output đủ rõ để debug khi có issue

## Files to Create/Modify

| File | Action | Mô tả |
|------|--------|-------|
| `ReadabilityExtractorTest.kt` | **Tạo mới** | Unit tests cho wrapper |
| `ContentExtractorRegistryTest.kt` | **Tạo mới** | Integration test cho pipeline |

## Output cuối cùng

Khi hoàn thành Phase 04:
- ✅ Pipeline 5 tầng hoạt động ổn định
- ✅ Trang VN: dùng extractor chuyên biệt (chính xác 100%)
- ✅ Trang lạ: Readability4J bóc tách thông minh
- ✅ Worst case: Generic fallback vẫn hoạt động
- ✅ Release build OK, ProGuard OK

## Notes

- Nếu phát hiện Readability4J cho kết quả xấu với một số trang web cụ thể → xem xét thêm domain vào blacklist (skip Readability4J cho domain đó, dùng Generic luôn).
- Nếu muốn nâng cấp xa hơn: có thể cache kết quả Readability4J theo URL để tránh parse lại khi user mở lại cùng bài viết.

---
**Previous Phase:** ← [Phase 03: Pipeline Integration](./phase-03-pipeline-integration.md)
