# RSS Reader (Refactored) 📰🤖

Ứng dụng đọc tin tức RSS hiện đại dành cho Android, tích hợp AI để tóm tắt nội dung và công nghệ Text-to-Speech (TTS) để đọc bản tin tự động. 

Thiết kế tối ưu cho trải nghiệm rảnh tay, đặc biệt hữu ích khi đang lái xe hoặc làm việc bận rộn.

---

## 🚀 Giới thiệu
**RSS Reader** là phiên bản đã được tái cấu trúc (refactoring) toàn diện, tập trung vào hiệu năng, trải nghiệm người dùng rảnh tay và tích hợp trí tuệ nhân tạo. Ứng dụng tự động lấy tin từ các nguồn RSS tiếng Việt phổ biến, sử dụng AI (Google Gemini) để tóm tắt nội dung cốt lõi và phát âm to rõ giúp bạn cập nhật thế giới mà không cần chạm vào màn hình.

## ✨ Tính năng nổi bật
- **📡 Lấy tin tự động (Auto-fetch):** Hỗ trợ nhiều nguồn RSS lớn tại Việt Nam (VnExpress, Tuổi Trẻ, Thanh Niên, Voz.vn...).
- **🤖 Tóm tắt bằng AI:** Tự động cô đọng các bài báo dài thành các bản tóm tắt ngắn gọn, súc tích chỉ sau vài giây.
- **🗣️ Đọc bản tin (TTS):** Sử dụng bộ máy Text-to-Speech rành mạch, hỗ trợ điều chỉnh tốc độ đọc phù hợp.
- **🔄 Chế độ đọc liên tục (Continuous Reading):** Tự động đọc danh sách tin theo trình tự mà không cần thao tác tay — lý tưởng cho việc lái xe.
- **📱 Giao diện Động (Responsive UI):** Chuyển đổi linh hoạt giữa chế độ dọc (Portrait) và ngang (Landscape) cho xe ô tô với bảng điều khiển tối giản.
- **⏯️ Gợi ý nghe tiếp (Resume State):** Tự động ghi nhớ bài tin đang nghe dở để gợi ý tiếp tục trong lần mở app sau.
- **⚡ Tối ưu Cache Offline:** Lưu trữ tin tức cục bộ bằng Room Database, giúp xem tin nhanh chóng mà không cần chờ tải mạng.

## 🛠️ Công nghệ sử dụng
- **Language:** Kotlin 100%
- **UI Framework:** Jetpack Compose (Material Design 3)
- **Architecture:** MVVM + Clean Architecture principles
- **Asynchronous:** Kotlin Coroutines & Flow
- **Local DB:** Room Database
- **Networking:** OkHttp & Retrofit
- **AI Integration:** Google Gemini API
- **Background Service:** Foreground Service cho việc đọc tin khi tắt màn hình.

## 📁 Cấu trúc thư mục chính
```text
app/src/main/java/com/skul9x/rssreader/
├── auth/           # Quản lý xác thực & API Keys
├── data/           # Lớp dữ liệu (Local DB, Network, Models, Repositories)
├── service/        # NewsReaderService xử lý TTS & Background play
├── tts/            # Quản lý Text-to-Speech engine
├── ui/             # Giao diện người dùng (Compose Screens, ViewModels)
└── utils/          # Các công cụ hỗ trợ (Logger, Network check, Translators)
```

## 📥 Hướng dẫn cài đặt & Sử dụng
### Yêu cầu
- Android 8.0 (API level 26) trở lên.
- API Key của Google Gemini (để sử dụng tính năng tóm tắt AI).

### Cài đặt nhanh
1. Clone repository này:
   ```bash
   git clone https://github.com/skul9x/RSS-Reader.git
   ```
2. Mở dự án trong Android Studio.
3. Build dự án và cài đặt lên thiết bị:
   ```bash
   ./gradlew assembleDebug
   ```

### Cách dùng
1. Chọn nguồn tin từ danh sách hoặc thêm nguồn mới.
2. Nhấn vào một bài tin để xem tóm tắt hoặc nghe đọc.
3. **Mẹo:** Nhấn giữ nút "Đọc 5 tin" để kích hoạt chế độ đọc liên tục (Continuous Mode).

---

### Bản quyền
Copyright 2026 Nguyễn Duy Trường

*Phát triển bởi [skul9x](https://github.com/skul9x)*
