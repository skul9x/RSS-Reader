# Phase 02: Regex Priority Strategy
Status: ⬜ Pending
Dependencies: Phase 01

## Objective
Implement a "Fast Lane" for known supported sites (Genk, VnExpress, Tuoi Tre, etc.) where lightweight Regex extraction is attempted *before* heavy DOM parsing/extraction.

## Requirements
### Functional
### Functional
- [x] Identify if URL belongs to a "Regex Preferred" domain.
- [x] For these domains, run their specific Regex extractor first.
- [x] If Regex fails (returns null), fallback to Jsoup extraction (using the pre-parsed Doc from Phase 1).
- [x] For unknown domains, keep Jsoup as primary.

## Implementation Steps
## Implementation Steps
1. [x] Create a `isRegexPreferred(url: String): Boolean` helper.
    - True for: genk.vn, vnexpress.net, tuoitre.vn, thanhnien.vn, dantri.com.vn, nguoiquansat.vn, vietnamnet.vn, vtcnews.vn.
2. [x] Update `extractContent` logic:
    ```kotlin
    if (isRegexPreferred(url)) {
       // Try Regex Strategy specific to domain
       val regexResult = extractByRegex(url, html)
       if (regexResult != null) return regexResult
    }
    // Fallback or Standard: Jsoup
    return extractWithJsoup(doc, url)
    ```
3. [x] Verification: Ensure Regex extractors (which use raw HTML string) are robust.

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/data/network/ArticleContentFetcher.kt` - Logic update.

## Test Criteria
- [ ] Genk.vn loads via Regex (check logs/performance or debugging breakpoints).
- [ ] Fallback works: If Regex fails (e.g. site layout change), Jsoup still gets data.

---
Next Phase: Verification
