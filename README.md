# 🚗 RSS Reader (Automotiveized) | Smart News for Cabin
[![Build Status](https://img.shields.io/badge/Build-Success-brightgreen)](https://github.com/skul9x/RSS-Reader-main)
[![Tech Stack](https://img.shields.io/badge/Tech-Kotlin_%7C_Compose_%7C_Gemini-blue)](https://kotlinlang.org/)

**RSS Reader** là ứng dụng đọc tin tức tiên tiến được thiết kế riêng cho màn hình Android Box trên ô tô. Ứng dụng kết hợp giữa trí tuệ nhân tạo **Google Gemini AI** và hệ thống **Text-to-Speech (TTS)** để mang lại trải nghiệm cập nhật tin tức rảnh tay, an toàn tuyệt đối khi lái xe.

---

## ✨ Điểm Nổi Bật (Executive Features)

### 🏎️ Automotive-First Design
*   **Giao diện Landscape tối ưu**: Chữ lớn, độ tương phản cao, thao tác "một chạm".
*   **Điều khiển từ vô lăng**: Hỗ trợ Media Button (Next/Prev/Play/Pause) qua 2 lớp bảo vệ (MediaSession & Broadcast Receiver), tương thích cả với các dòng xe cũ.
*   **Quản lý luồng âm thanh**: Không gây gián đoạn app dẫn đường (Google Maps, Navitel).

### 🤖 Gemini AI Powered
*   **Smart Summarization**: Tự động "bẻ khóa" nội dung bài báo và tóm tắt thành các ý chính ngắn gọn.
*   **Multi-Model Failover**: Tự động luân chuyển API Key và chuyển đổi giữa Gemini 1.5 Flash & 2.0 Flash để đảm bảo dịch vụ không bị gián đoạn.
*   **Regex-First Extraction**: Tốc độ xử lý cực nhanh nhờ bộ lọc Regex tối ưu cho các đầu báo lớn (VnExpress, Tuổi Trẻ, Genk...).

### 🎙️ Hand-free Experience
*   **Auto-Resume**: Tự động tiếp tục đọc từ câu vừa dừng sau khi có cuộc gọi điện thoại hoặc thông báo khẩn cấp.
*   **Tiếng Việt tự nhiên**: Tích hợp các công cụ TTS hàng đầu cho giọng đọc mượt mà.

---

## 🛠 Kiến Trúc Kỹ Thuật (Architecture)

Ứng dụng được xây dựng trên nền tảng **Clean Architecture** và các Design Patterns hiện đại:

*   **Facade Pattern**: Lớp `ArticleContentFetcher` đóng vai trò trung gian, cung cấp API ổn định cho UI trong khi ẩn đi sự phức tạp bên dưới.
*   **Strategy Pattern**: Tách biệt logic trích xuất nội dung cho từng website riêng biệt (Extractors), giúp dễ dàng mở rộng thêm các nguồn tin mới.
*   **MVVM**: Tách biệt hoàn toàn Logic và Giao diện qua Jetpack Compose.
*   **Foreground Service**: Đảm bảo app luôn sống khỏe khi chạy ngầm, quản lý qua MediaStyle Notification.

### Danh mục công nghệ:
*   **UI**: Jetpack Compose (Modern UI).
*   **Database**: Room DB (Caching & Persistence).
*   **Networking**: OkHttp & Kotlin Serialization.
*   **DI**: Tối ưu hóa thủ công & Clean Repository.

---

## 🚀 Cài đặt & Sử dụng

1.  **Clone & Open**: Mở project bằng **Android Studio Koala (2024.1.1)** hoặc mới hơn.
2.  **Config AI**: Truy cập Cài đặt trong App để nhập **Gemini API Key**.
3.  **Add Feed**: Nhập link RSS của các đầu báo yêu thích.
4.  **Enjoy**: Click **"Đọc tin"** và tập trung lái xe. 

---

## 🔧 Debug & Diagnostics
Dành cho người dùng chuyên sâu và kỹ thuật viên:
*   **Real-time Logs**: Theo dõi tín hiệu Media Button và phản hồi API AI ngay trên màn hình Debug.
*   **Quota Tracker**: Theo dõi mức độ sử dụng Gemini API để quản lý tài nguyên hiệu quả.

---
*Phát triển bởi **Skul9x** - Đưa tin tức lên cabin xe của bạn một cách thông minh và an toàn.*
