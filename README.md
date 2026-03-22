# 📰 RSS Reader - Trình Đọc Tin Chuyên Sâu Tích Hợp AI

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/android)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg)](https://developer.android.com/jetpack/compose)
[![Gemini](https://img.shields.io/badge/AI-Gemini-orange.svg)](https://deepmind.google/technologies/gemini/)

**RSS Reader** là một ứng dụng Android hiện đại, được thiết kế để mang lại trải nghiệm đọc tin tức tối ưu, nhanh chóng và giàu tính tương tác. Thay vì chỉ hiển thị tiêu đề và tóm tắt ngắn từ RSS thông thường, ứng dụng này sử dụng các thuật toán trích xuất nội dung thông minh (Extractors) để mang về toàn bộ nội dung bài báo, trình bày một cách sạch sẽ và chuyên sâu nhất.

---

## ✨ Tính Năng Nổi Bật

🚀 **Trích Xuất Nội Dung Chuyên Sâu:**
- Tích hợp các bộ lọc (Extractors) tối ưu hóa cho từng trang báo điện tử lớn tại Việt Nam: **VnExpress, Dân Trí, Tuổi Trẻ, Thanh Niên, VnEconomy, VietnamNet, VTC News, GenK, Voz.vn (Support redirects)...**
- Tự động vượt qua các rào cản anti-bot và xử lý chuyển hướng URL phức tạp.

🛡️ **Trình Đọc Sạch Sẽ & Tập Trung:**
- Loại bỏ hoàn toàn quảng cáo, menu rườm rà và các thành phần gây nhiễu.
- Chế độ đọc liên tục (Continuous Reading) giúp chuyển bài mượt mà.

🧠 **Trí Tuệ Nhân Tạo Gemini AI:**
- Tóm tắt nội dung bài báo dài trong vài giây.
- Phân tích và cung cấp góc nhìn tóm lược giúp tiết kiệm thời gian.

📴 **Offline-First & Hiệu Năng Cao:**
- Tự động cache tin tức vào cơ sở dữ liệu **Room (SQLite)**.
- Xử lý bóc tách nội dung bằng **Regex-first Strategy** để tối ưu CPU và RAM (không gây leak bộ nhớ như các parser truyền thống).

🔄 **Đồng Bộ Hoá Qua Firebase:**
- Đồng bộ trạng thái đã đọc giữa các thiết bị thông qua tài khoản Google.

---

## 🛠️ Công Nghệ Sử Dụng

- **Ngôn ngữ:** Kotlin 1.9+
- **Giao diện:** Jetpack Compose (Material 3)
- **Database:** Room Persistence Library
- **Networking:** OkHttp & Jsoup for HTML parsing
- **Background Tasks:** WorkManager for auto-sync
- **Authentication:** Firebase Auth & Google Sign-In
- **AI Integration:** Google Gemini SDK

---

## 📂 Cấu Trúc Thư Mục

```text
RSS-Reader-main/
├── app/
│   ├── src/main/java/com/skul9x/rssreader/
│   │   ├── data/             # Repository, DAO, Models, Extractors logic
│   │   ├── ui/               # Compose Screens, Themes, ViewModels
│   │   ├── utils/            # HtmlCleaner, RedirectResolver, Formatters
│   │   └── MainApplication.kt
├── .brain/                   # Kiến thức & Ngữ cảnh hoạt động của trợ lý AI (Antigravity Historian)
├── docs/                     # Tài liệu kỹ thuật & Specs
├── plans/                    # Kế hoạch phát triển từng giai đoạn
└── structure.md              # Mô tả chi tiết cấu trúc project
```

---

## 🚀 Hướng Dẫn Cài Đặt

### 📋 Điều kiện cần
- **Android Studio Ladybug (2024.2.1)** hoặc mới hơn.
- **JDK 17** trở lên.
- Android Device/Emulator chạy **Android 8.0 (API 26)** trở lên.

### ⚙️ Các bước thực hiện
1. **Clone project:**
   ```bash
   git clone https://github.com/skul9x/RSS-Reader.git
   ```
2. **Mở dự án:** Khởi động Android Studio và chọn folder project.
3. **Gradle Sync:** Đợi Android Studio tải các dependencies cần thiết.
4. **Cấu hình Firebase (Tùy chọn):** Thêm file `google-services.json` vào thư mục `app/` nếu muốn sử dụng tính năng đồng bộ.
5. **Build & Run:** Nhấn nút **Play** ▶️ để cài đặt lên máy ảo hoặc thiết bị thật.

---

## ⚖️ Bản Quyền

Copyright 2026 Nguyễn Duy Trường

---

*Project được tối ưu và phát triển với sự hỗ trợ từ **Antigravity AI (Antigravity Historian)**.*
