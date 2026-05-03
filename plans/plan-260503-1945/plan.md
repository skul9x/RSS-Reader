# Plan: Tùy chọn Model Gemini cho Tóm tắt

## Tổng quan
Hiện tại ứng dụng đang tự động xoay vòng qua 4 model (2.5 Flash Lite, 3 Flash Preview, 3.1 Flash Lite Preview, 2.5 Flash) khi gặp lỗi quota. Tính năng này sẽ cho phép người dùng chủ động chọn 1 model ưu tiên từ danh sách này trong Settings.

## Danh sách Model tích hợp (từ GeminiApiClient.kt)
1. Gemini 2.5 Flash Lite (`models/gemini-2.5-flash-lite`)
2. Gemini 3 Flash Preview (`models/gemini-3-flash-preview`)
3. Gemini 3.1 Flash Lite Preview (`models/gemini-3.1-flash-lite-preview`)
4. Gemini 2.5 Flash (`models/gemini-2.5-flash`)

## Các giai đoạn (Phases)
| Phase | Tên nhiệm vụ | Trạng thái |
|------|--------------|------------|
| 01 | Cập nhật Preferences & Enum | Chờ thực hiện |
| 02 | Xây dựng UI Component | Chờ thực hiện |
| 03 | Tích hợp Logic vào Client | Chờ thực hiện |