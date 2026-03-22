# Plan: Architecture Refactor & Bug Fixes
Created: 26-03-2026 08:05
Status: 🟡 In Progress

## Overview
Lên kế hoạch vá các lỗ hổng kiến trúc và tối ưu hoá luồng bóc tách nội dung (Content Extraction Flow) dựa trên tài liệu `Bugs.txt` và `bug1.txt`. Mục tiêu là khắc phục lỗi font chữ (Charset Encoding), tránh tràn RAM (OOM), tối ưu CPU khi gặp redirect, sửa lỗi rớt chữ và sửa luồng fallback/custom selector.

## Tech Stack
- Frontend: Android (Kotlin)
- Thư viện parse: Jsoup
- Thư viện mạng: OkHttp

## Phases

| Phase | Name | Status | Progress |
|-------|------|--------|----------|
| 01 | Core Fetch & Redirect | ✅ Complete | 100% |
| 02 | Memory & Fallback Logic | ⬜ Pending | 0% |
| 03 | Generic Extractor Enhance | ⬜ Pending | 0% |
| 04 | Testing | ⬜ Pending | 0% |

## Quick Commands
- Start Phase 1: `/code phase-01`
- Check progress: `/next`
- Save context: `/save-brain`
