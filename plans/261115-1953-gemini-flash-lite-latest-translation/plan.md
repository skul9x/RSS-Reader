# Plan: Title Translation with Gemini Flash-Lite Latest & Google AI Best Practices
Created: 2026-11-15 19:53
Status: ✅ Completed

## Overview
Optimize news title translation from English to Vietnamese across the RSS Reader Android application by switching the underlying translation model from a hardcoded single version to Google's dynamic alias `models/gemini-flash-lite-latest` with a robust fallback tier. Simultaneously implement Google AI translation best practices: dedicated `system_instruction`, deterministic low temperature (`0.1`), targeted output token caps (`128` for single, `384` for batches of 15), minimal thinking latency overhead, and dynamic model discovery in settings.

## Tech Stack & Architecture
- Language: Kotlin 1.9 / 2.0
- Framework: Android SDK (Jetpack Compose, Coroutines, Flow)
- Network: OkHttp 4.x + Kotlinx Serialization
- Storage: Room Database + SharedPreferences (`AppPreferences`, `ModelManager`)
- AI Platform: Google Generative Language API (Gemini REST `v1beta`)

## Implementation Phases

| Phase | Name | Status | Progress | Key Target |
|-------|------|--------|----------|------------|
| 01 | Request Builder & Best Practices Payload | ✅ Completed | 100% | `GeminiResponseHelper.kt` (system_instruction, low temp, capped tokens) |
| 02 | Client Engine & Fallback Pipeline | ✅ Completed | 100% | `GeminiApiClient.kt` (wire gemini-flash-lite-latest with fallback sequence) |
| 03 | Model Registration & Catalog Management | ✅ Completed | 100% | `ModelManager.kt`, `GeminiModels.kt`, settings validation |

## Quick Commands
- Start Phase 1: `/code phase-01`
- Check progress: `/next`
- Save context: `/save-brain`

