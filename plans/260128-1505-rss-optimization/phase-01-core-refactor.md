# Phase 01: Core Refactor (Single Parse)
Status: ⬜ Pending
Dependencies: None

## Objective
Refactor `ArticleContentFetcher` to parse HTML into a Jsoup `Document` only once per request, passing this object to helper methods instead of raw HTML strings. This eliminates redundant parsing ops (currently 3-4x per Voz article).

## Requirements
### Functional
- [x] `fetchArticleWithTitle` parses HTML once.
- [x] Helper methods (`extractVozOriginalLink`, `extractVozQuoteAsArticle`, `extractTitle`, `extractContent`) accept `Document` where appropriate.
- [x] `extractWithJsoup` uses the passed `Document`.
- [x] `fetchRawHtml` remains available for debugging.

### Non-Functional
- [x] Reduce CPU cycles for content extraction.
- [x] Maintain fallback safety (try-catch blocks around Jsoup ops).

## Implementation Steps
1. [x] Modify `fetchArticleWithTitle` to create `val doc = Jsoup.parse(html, url)`.
2. [x] Update `extractVozOriginalLink(doc: Document)` signature & logic.
3. [x] Update `extractVozQuoteAsArticle(doc: Document)` signature & logic.
4. [x] Update `extractTitle(doc: Document)` signature & logic.
5. [x] Update `extractContent` to take both `html: String` (for Regex) and `doc: Document` (for Jsoup).

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/data/network/ArticleContentFetcher.kt` - Refactor main logic.

## Test Criteria
- [ ] Voz.vn articles still correctly detect "unfurl" links.
- [ ] Voz.vn "Diem Bao" quotes are still extracted.
- [ ] General articles still extract title and content correctly.
- [ ] No regression in redirect handling.

---
Next Phase: [Phase 02: Regex Priority Strategy](phase-02-regex-priority.md)
