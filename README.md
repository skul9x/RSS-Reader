# Android RSS Reader (Automotiveized) 🚗📰

Ứng dụng đọc tin tức RSS tiên tiến, được thiết kế đặc biệt cho màn hình Android Box trên ô tô. App kết hợp sức mạnh của **Google Gemini AI** để tóm tắt nội dung và hệ thống **Text-to-Speech (TTS)** để mang lại trải nghiệm cập nhật tin tức rảnh tay, an toàn khi lái xe.

## ✨ Tính năng chính

- **🚗 Tối ưu cho Ô tô**: Giao diện Landscape hiện đại, chữ lớn, độ tương phản cao, hỗ trợ điều khiển bằng phím cứng vô lăng/media button.
- **🤖 Gemini AI Summarization**: Tự động tóm tắt các bài báo dài thành các ý chính ngắn gọn (Bullet points), giúp bạn nắm bắt thông tin nhanh nhất.
- **🎙️ Trải nghiệm Rảnh tay**: Hệ thống đọc tin tức tự động bóc tách nội dung từ link gốc (VnExpress, Tuổi Trẻ,...) và đọc bằng giọng nói tiếng Việt tự nhiên.
- **🎧 Quản lý luồng âm thanh**: Cho phép chọn luồng phát (Media, Thông báo, Báo thức, Dẫn đường) để không làm gián đoạn bản đồ hoặc các app khác.
- **🔄 Hệ thống Failover thông minh**: Tự động chuyển đổi giữa nhiều API Key và các model Gemini (1.5 Flash, 2.0 Flash) để tránh lỗi giới hạn quota.
- **📦 Caching thông minh**: Lưu trữ bản tóm tắt bài báo cục bộ (Room DB) để tiết kiệm tài nguyên và đọc lại tức thì.
- **🛠️ Debug Log Chuyên sâu**: Hệ thống logging thời gian thực giúp theo dõi tín hiệu phím media và phản hồi từ AI ngay trên màn hình cài đặt.

## 🛠 Công nghệ cốt lõi

- **UI**: Jetpack Compose (Modern Declarative UI).
- **Service**: Foreground Service với MediaStyle Notification (giúp app không bị kill khi chạy ngầm).
- **AI Integration**: Google Generative AI SDK (Gemini).
- **Architecture**: MVVM + Clean Architecture + Repository Pattern.
- **Storage**: Room Database (Persistent storage) + SharedPreferences.
- **Networking**: OkHttp + Kotlin Serialization + HTTP Content Scraping.

## 🚀 Cài đặt nhanh

1. Clone project và mở bằng **Android Studio Koala** hoặc mới hơn.
2. Cấu hình **Gemini API Key** trong phần Settings của App.
3. Thêm các link RSS yêu thích (VnExpress, BBC, v.v.).
4. Nhấn **"Đọc 5 tin"** và tập trung lái xe, App sẽ lo phần còn lại!

## 🔧 Phím Media & Điều khiển xe
App hỗ trợ bắt các tín hiệu từ vô lăng qua 2 lớp bảo vệ:
- **Plan A**: MediaSession chuẩn Android.
- **Plan B**: Broadcast Receiver và Key Event Dispatch giúp tương thích với các dòng xe Android Box cũ/đặc thù.

---
*Phát triển bởi Skul9x - Đưa tin tức lên cabin xe của bạn.*
