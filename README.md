# 📱 RSS Reader - Ứng dụng Theo dõi Tin tức Thông minh

[![Build & Release APK](https://github.com/skul9x/RSS-Reader/actions/workflows/build.yml/badge.svg)](https://github.com/skul9x/RSS-Reader/actions/workflows/build.yml)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-blue.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-orange.svg)](https://developer.android.com/jetpack/compose)

**RSS Reader** là một ứng dụng di động mạnh mẽ và tinh tế trên nền tảng Android, giúp bạn quản lý và đọc tin tức từ hàng ngàn nguồn RSS feed khác nhau. Với sự hỗ trợ của trí tuệ nhân tạo, ứng dụng không chỉ đơn thuần là một trình đọc tin mà còn là một trợ lý thông tin thông minh.

---

## ✨ Tính năng nổi bật

- 🚀 **Tóm tắt bằng AI**: Sử dụng Google Gemini (Flash 2.5/3.0 Lite) để tóm tắt nội dung các bài báo dài, giúp bạn nắm bắt ý chính chỉ trong vài giây.
- 🌐 **Dịch thuật thông minh**: Tự động dịch các bài báo quốc tế sang tiếng Việt với độ chính xác cao.
- 📶 **Đọc Offline**: Tự động cache nội dung bài viết để bạn có thể đọc mọi lúc, mọi nơi ngay cả khi không có mạng.
- 📖 **Trải nghiệm đọc liền mạch**: Chế độ Continuous Reading giúp bạn đọc tin liên tục mà không bị gián đoạn.
- 🛠️ **Bóc tách nội dung thông minh**: Tích hợp Readability4J và Jsoup để xử lý các trang web phức tạp, loại bỏ quảng cáo và các thành phần thừa.
- 🔄 **Xoay vòng API Key**: Hệ thống Model-First Rotation giúp tối ưu hóa việc sử dụng quota API Gemini.

---

## 🛠️ Công nghệ sử dụng

Ứng dụng được xây dựng trên những công nghệ hiện đại nhất:

- **Ngôn ngữ**: [Kotlin](https://kotlinlang.org/) - Hiện đại, an toàn và hiệu quả.
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3) - Giao diện hiện đại, mượt mà.
- **Database**: [Room Persistence](https://developer.android.com/training/data-storage/room) - Lưu trữ dữ liệu cục bộ mạnh mẽ.
- **Backend/Services**: [Firebase](https://firebase.google.com/) (Auth, Firestore) & WorkManager.
- **AI Engine**: [Google Gemini API](https://ai.google.dev/) - Trí tuệ nhân tạo hàng đầu.
- **Networking**: [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/).

---

## 🚀 Hướng dẫn cài đặt

Để bắt đầu phát triển hoặc build ứng dụng, hãy làm theo các bước sau:

1. **Clone Repository**:
   ```bash
   git clone https://github.com/skul9x/RSS-Reader.git
   cd RSS-Reader
   ```

2. **Cấu hình Firebase**:
   - Tải file `google-services.json` từ dự án Firebase của bạn.
   - Đặt file này vào thư mục `app/`.

3. **Mở dự án**:
   - Sử dụng **Android Studio (Iguana hoặc mới hơn)** để mở thư mục dự án.
   - Đợi Gradle đồng bộ hóa các dependency.

4. **Chạy ứng dụng**:
   - Kết nối thiết bị Android hoặc khởi động Emulator.
   - Nhấn **Run** (Shift + F10).

---

## 📖 Cách sử dụng

1. **Thêm nguồn tin**: Vào mục quản lý Feed để thêm các đường dẫn RSS bạn yêu thích.
2. **Cấu hình AI**: Vào **Settings** -> nhập API Key Gemini của bạn để kích hoạt tính năng tóm tắt và dịch thuật.
3. **Đọc tin**: Chọn bất kỳ bài viết nào, sử dụng biểu tượng 🤖 để tóm tắt hoặc biểu tượng 🌐 để dịch.

---

## 📂 Cấu trúc thư mục

```text
RSS-Reader/
├── app/                  # Module chính của ứng dụng
│   ├── src/main/java/... # Mã nguồn Kotlin
│   │   ├── data/         # Repository, DAO, Network, Extractors
│   │   ├── ui/           # Screens, Components, Theme (Compose)
│   │   ├── service/      # Background Services (WorkManager)
│   │   └── utils/        # Tiện ích (Logger, Cleaners)
│   └── build.gradle.kts  # Cấu hình build module app
├── .github/workflows/    # Cấu hình CI/CD (GitHub Actions)
├── docs/                 # Tài liệu hướng dẫn và thiết kế
└── gradle/               # Gradle wrapper và script
```

---

## 📝 Thông tin bổ sung

Chúng tôi luôn hoan nghênh các đóng góp từ cộng đồng. Nếu bạn phát hiện lỗi hoặc có ý tưởng mới, vui lòng:
- Tạo một **Issue** để thảo luận.
- Gửi **Pull Request** với những thay đổi của bạn.

---

## ⚖️ Bản quyền

Copyright 2026 Nguyễn Duy Trường

---
*Phát triển bởi [Nguyễn Duy Trường](https://github.com/skul9x) với sự hỗ trợ từ Antigravity AI.*
