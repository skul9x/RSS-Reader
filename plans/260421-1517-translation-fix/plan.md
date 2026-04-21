# Plan: Fix Translation Flow (Gemini API)
Created: 2026-04-21T15:17
Status: 🟡 In Progress

## Overview
Sửa 3 lỗi nghiêm trọng trong luồng dịch tiêu đề tin tức bằng Gemini API:
- Kết quả JSON không ổn định (dùng Regex thay vì JSON Mode)
- Retry mù quáng khi mất mạng (lãng phí tài nguyên)
- Batch thất bại thì mất hết (không có fallback)

## Context
- **Báo cáo gốc:** [trans_report.md](../../trans_report.md)
- **File chính cần sửa:**
  - `GeminiResponseHelper.kt` — Build request body
  - `GeminiApiClient.kt` — Translate logic + retry loop
  - `GeminiModels.kt` — ApiResult sealed class
  - `GeminiPrompts.kt` — Prompt templates
  - `MainViewModel.kt` — UI integration (batch-to-single fallback)

## Tech Stack
- Language: Kotlin
- API: Gemini REST API v1beta
- HTTP Client: OkHttp
- JSON: kotlinx.serialization

## Phases

| Phase | Name | Status | Files Affected |
|-------|------|--------|----------------|
| 01 | JSON Mode chuẩn | ✅ Completed | `GeminiResponseHelper.kt`, `GeminiApiClient.kt` |
| 02 | Fail-Fast Network Errors | ⬜ Pending | `GeminiModels.kt`, `GeminiApiClient.kt` |
| 03 | Batch-to-Single Fallback | ⬜ Pending | `GeminiApiClient.kt`, `MainViewModel.kt` |

## Quick Commands
- Start Phase 1: `/code phase-01`
- Check progress: `/next`
- Save context: `/save-brain`
