━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 HANDOVER DOCUMENT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📍 Đang làm: Universal Dynamic Multi-Model API Rotation & Quota Management
🔢 Đến bước: Hoàn tất 100% Phase 4 (Coding + Testing + Device Deployment)

✅ ĐÃ XONG:
   - Phase 01: Setup okhttp-mockwebserver dependency ✓
   - Phase 02: Core rotation logic in ModelQuotaManager & model restoration ✓
   - Phase 03: Fault-tolerant integration & error states in GeminiApiClient ✓
   - Phase 04: Robust integration testing (DynamicModelRotationIntegrationTest.kt) ✓
   - Device Deployment: Build debug APK, install on connected physical device (OP5D3BL1) and launch it successfully ✓

⏳ CÒN LẠI:
   - Monitor and profile memory usage of GeminiApiClient under high concurrency (Low priority)
   - Evaluate long-term performance and behavior of model fallbacks in practical use

🔧 QUYẾT ĐỊNH QUAN TRỌNG:
   - Sử dụng lambda timeProvider trong ModelQuotaManager thay vì MockK static mocking để tránh JVM stack overflows.
   - Thêm dynamic baseUrl vào GeminiApiClient để hướng API request về MockWebServer trong quá trình test.
   - 429 block combo (Model + API Key) kéo dài 30 tiếng và được ghi lưu liên tục qua SharedPreferences.
   - 503 cooldown combo kéo dài 5 phút và được lưu trữ tạm thời trong RAM.

⚠️ LƯU Ý CHO SESSION SAU:
   - Đã kiểm tra và vượt qua toàn bộ 4 kịch bản tích hợp và khả năng tự chữa lành (Self-healing).
   - Ứng dụng debug đã được cài đặt và đang chạy trực tiếp trên thiết bị Android của user.
   - Các files cấu hình test nằm tại DynamicModelRotationIntegrationTest.kt.

📁 FILES QUAN TRỌNG:
   - `ModelQuotaManager.kt`: Quản lý quota và cooldown.
   - `GeminiApiClient.kt`: Xử lý HTTP request và xoay vòng model khi lỗi xảy ra.
   - `DynamicModelRotationIntegrationTest.kt`: Test suite chứa các kịch bản lỗi giả định.
   - `.brain/brain.json`: Dữ liệu tĩnh bộ nhớ dự án.
   - `.brain/session.json`: Tiến độ phiên hiện tại.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📍 Đã lưu! Để tiếp tục: Gõ /recap
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
