# Plan: Universal Gemini API Key Parser & Summarization Latency Quick Wins
Created: 2026-11-15 19:05
Status: ✅ Completed

## Overview
Implement two core architectural improvements to the RSS Reader's AI pipeline:

1. **Phase 01: Universal Gemini API Key Parser & Validator (`AQ.` & `AIza`)**
   Support the newly rolled-out Google AI Studio Authentication Keys (`AQ.` prefix, ~55 characters) alongside legacy Traffic Keys (`AIza` prefix, 39 characters). Centralize smart token extraction and validation into `ApiKeyManager` to reliably handle dirty multi-line user inputs (quotes, commas, `.env` definitions) and update the Settings UI components.

2. **Phase 02: Quick Wins Summarization Latency Optimization**
   Slash end-to-end summarization latency by ~65% (from 6–8s down to ~1.5–2.5s) by:
   - Disabling reasoning overhead on Gemini 2.5 Flash/Lite models via `"thinkingConfig": { "thinkingBudget": 0 }`.
   - Constraining summarization input context window to a focused 4,000 characters (eliminating Time-to-First-Token bloat from 100,000 characters).
   - Right-sizing output token ceiling to 512 tokens (tailored for concise audio bullet points).

## Tech Stack & Architecture Context
- Language: Kotlin 1.9+
- UI: Jetpack Compose (Material 3)
- Storage: EncryptedSharedPreferences (`ApiKeyManager`), Room SQLite (`NewsSummaryDao`)
- Networking: OkHttp 4.12.0, Retrofit/JSON Serialization
- AI Service: Google Gemini REST API v1beta (`generativelanguage.googleapis.com`)
- Testing: JUnit 4, MockK, Kotlin Coroutines Test

## Phases

| Phase | Name | Status | Progress |
|-------|------|--------|----------|
| 01 | Universal Gemini API Key Parser & Validator | ✅ Completed | 100% |
| 02 | Summarization Latency Quick Wins (Thinking & Payload Tuning) | ✅ Completed | 100% |

## Quick Commands
- Start Phase 1: `/code phase-01`
- Start Phase 2: `/code phase-02`
- Check progress: `/next`
- Save context: `/save-brain`

