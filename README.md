# 📱 RSS Reader - Ứng dụng Theo dõi Tin tức & Tóm tắt AI Thông minh

[![Build & Release APK](https://github.com/skul9x/RSS-Reader/actions/workflows/build.yml/badge.svg)](https://github.com/skul9x/RSS-Reader/actions/workflows/build.yml)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-blue.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-orange.svg)](https://developer.android.com/jetpack/compose)

**RSS Reader** là một ứng dụng di động mạnh mẽ, hiện đại và tinh tế trên nền tảng Android, giúp người dùng quản lý và theo dõi tin tức từ hàng ngàn nguồn RSS feed khác nhau. Điểm đặc biệt của ứng dụng là việc tích hợp trí tuệ nhân tạo (AI) Gemini để tóm tắt, trích xuất nội dung một cách tự động và thông minh, đem lại trải nghiệm đọc tin tối ưu và tiết kiệm thời gian nhất.

---

## ✨ Tính năng nổi bật

- 🚀 **Tóm tắt bằng AI thông minh**: Tích hợp Google Gemini (Flash 2.5/3.0 Lite) để tóm tắt các bài báo dài thành các ý chính ngắn gọn, súc tích chỉ trong vài giây.
- 🌐 **Dịch thuật tự động**: Dịch nhanh chóng các bài báo tiếng nước ngoài sang tiếng Việt với độ chính xác cao.
- 📶 **Hỗ trợ Đọc Offline**: Tự động tải và cache nội dung bài viết vào cơ sở dữ liệu cục bộ để người dùng có thể đọc mọi lúc, mọi nơi ngay cả khi không có kết nối Internet.
- 📖 **Trải nghiệm đọc liền mạch (Continuous Reading)**: Chế độ đọc tin tức liên tục giúp tối ưu hóa luồng trải nghiệm người dùng, hỗ trợ chuyển bài mượt mà.
- ⚙️ **Cơ chế Xoay vòng Model & Khóa API thông minh (Dynamic Model Rotation)**:
  - **Model-First Rotation**: Thử nghiệm cùng một mô hình trên toàn bộ các khóa API khả dụng trước khi chuyển đổi sang mô hình nặng hơn/mới hơn để tối ưu hóa hạn ngạch.
  - **Khống chế lỗi 429 (Quota Exceeded)**: Tự động khóa sự kết hợp giữa Model + API Key trong **30 tiếng** và lưu trữ bền vững qua `SharedPreferences`.
  - **Khống chế lỗi 503 (Server Overloaded)**: Đưa sự kết hợp Model + API Key vào chế độ cooldown tạm thời trong **5 phút** trên RAM.
  - **Cơ chế Tự phục hồi (Self-Healing)**: Tự động nạp lại danh sách các mô hình mặc định nếu cấu hình bị rỗng hoặc lỗi.
- 🚗 **Hỗ trợ nút Media trên xe hơi**: Tích hợp điều khiển thông qua phím bấm vô lăng xe hơi (Media Button Support) với cơ chế gom lô làm mới tự động (auto-refresh batching).
- 🛠️ **Bóc tách nội dung chuyên sâu**: Tích hợp pipeline trích xuất nội dung tiên tiến `Readability4J` kết hợp với parser `Jsoup` để xử lý các trang tin phức tạp, loại bỏ quảng cáo và thành phần thừa một cách triệt để.

---

## 🛠️ Công nghệ sử dụng

Ứng dụng được phát triển dựa trên các công nghệ và thư viện Android hiện đại nhất hiện nay:

- **Ngôn ngữ**: [Kotlin](https://kotlinlang.org/) (phiên bản 1.9+) - An toàn, ngắn gọn và tối ưu hiệu suất.
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) với Material Design 3 - Đem lại giao diện mượt mà, sống động và đậm chất thẩm mỹ.
- **Database**: [Room Persistence](https://developer.android.com/training/data-storage/room) (SQLite) - Lưu trữ dữ liệu offline ổn định và an toàn, hỗ trợ cơ chế Flow để cập nhật UI phản ứng thời gian thực.
- **Background Sync**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) - Lập lịch đồng bộ tin tức ngầm hiệu quả mà không hao tổn tài nguyên pin.
- **Network**: [OkHttp](https://square.github.io/okhttp/) & [Retrofit](https://square.github.io/retrofit/) - Kết nối API ổn định, hiệu năng cao.
- **Local Testing**: [MockWebServer](https://github.com/square/okhttp/tree/master/mockwebserver) - Mô phỏng hoàn hảo các lỗi mạng (429, 503, outages) phục vụ kiểm thử tích hợp (Integration Tests).
- **Authentication & Sync**: Firebase (Auth, Firestore) - Đồng bộ hóa trạng thái tin tức đã đọc giữa các thiết bị.

---

## 🚀 Hướng dẫn cài đặt

Để bắt đầu chạy thử hoặc phát triển dự án trên môi trường cục bộ:

### Yêu cầu hệ thống:
* **JDK 17** (Cần thiết cho tiến trình build Gradle 8.11.1).
* **Android SDK** (compileSdk 35, minSdk 26).
* **Android Studio** (Koala, Ladybug hoặc mới hơn).

### Các bước thực hiện:

1. **Clone Repository**:
   ```bash
   git clone https://github.com/skul9x/RSS-Reader.git
   cd RSS-Reader
   ```

2. **Cấu hình dịch vụ Firebase**:
   - Truy cập Firebase Console và tạo một dự án Android với package name là `com.skul9x.rssreader`.
   - Tải file `google-services.json` từ dự án Firebase về và đặt trực tiếp vào thư mục `/app/`.

3. **Mở dự án trên Android Studio**:
   - Chọn **File** -> **Open** -> Trỏ tới thư mục chứa dự án `RSS-Reader`.
   - Đợi Android Studio và Gradle đồng bộ hóa các dependencies (Sync Gradle).

4. **Chạy ứng dụng**:
   - Kết nối một thiết bị Android thật (đã bật chế độ Debug qua USB) hoặc khởi chạy thiết bị giả lập (Emulator).
   - Nhấn nút **Run** (Shift + F10) trên thanh công cụ.

---

## 📖 Hướng dẫn sử dụng

1. **Thêm nguồn tin mới**: Truy cập vào menu quản lý nguồn và thêm các đường dẫn RSS feed (ví dụ: VnExpress, Tuổi Trẻ, Voz, v.v.).
2. **Thiết lập API AI**: Vào phần **Cài đặt (Settings)** -> nhập API Key Gemini của bạn để kích hoạt chức năng dịch và tóm tắt AI.
3. **Tóm tắt bài viết**: Khi đọc một bài viết bất kỳ, nhấn biểu tượng 🤖 để kích hoạt AI Gemini tóm tắt nhanh nội dung chính của bài báo.
4. **Xoay vòng & Cấu hình Model**: Ứng dụng tự động quản lý độ ưu tiên của các dòng model (từ Flash Lite đến các bản Flash cao cấp). Bạn có thể tùy chỉnh danh sách mô hình này trong cài đặt.

---

## 📂 Cấu trúc thư mục chính

```text
RSS-Reader/
├── app/                         # Module ứng dụng chính
│   ├── src/main/java/com/.../
│   │   ├── data/                # Tầng dữ liệu (Room, Repository, SharedPreferences)
│   │   │   ├── local/           # Room Database, ModelManager, AppPreferences
│   │   │   └── network/         # GeminiApiClient, ModelQuotaManager, API config
│   │   ├── ui/                  # Tầng giao diện người dùng (Jetpack Compose UI)
│   │   │   ├── components/      # UI Components dùng chung (Buttons, Cards)
│   │   │   ├── screens/         # Các màn hình chính (Home, Read, Settings, Feed)
│   │   │   └── theme/           # Cấu hình màu sắc, typography Material 3
│   │   └── service/             # WorkManager background services
│   ├── src/test/java/com/.../   # Kiểm thử tự động (Unit Test, Integration Test)
│   └── google-services.json     # Cấu hình kết nối Firebase
├── .github/workflows/           # Cấu hình CI/CD (GitHub Actions)
│   └── build.yml                # Tự động build APK và tạo Github Release
├── plans/                       # Kế hoạch phát triển và tài liệu thiết kế tính năng
└── build.gradle.kts             # File cấu hình Gradle cấp root
```

---

## 🔒 Kiểm thử & Đảm bảo chất lượng

Dự án sở hữu bộ kiểm thử tự động toàn diện nhằm kiểm thử hiệu năng, độ tin cậy và khả năng tự chữa lành lỗi của client kết nối Gemini:
- **Unit Tests**: Kiểm thử logic CRUD trên `ModelManager` và logic khóa quota cục bộ.
- **Integration Tests**: Chạy thử nghiệm giả lập lỗi 429, lỗi 503 và khôi phục hoạt động tự động của client bằng `MockWebServer` tại file [DynamicModelRotationIntegrationTest.kt](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/app/src/test/java/com/skul9x/rssreader/data/network/DynamicModelRotationIntegrationTest.kt).

Để chạy toàn bộ kiểm thử trong terminal:
```bash
./gradlew test
```

---

## ⚖️ Bản quyền

Copyright 2026 Nguyễn Duy Trường

---
*Phát triển và duy trì bởi [Nguyễn Duy Trường](https://github.com/skul9x) với sự hỗ trợ đắc lực từ Antigravity AI.*
