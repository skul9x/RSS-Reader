# Android RSS Reader (Car Edition) 🚗📰

Ứng dụng đọc tin tức RSS được tối ưu hóa cho màn hình Android Box trên ô tô, tích hợp trí tuệ nhân tạo Gemini để tóm tắt tin tức và đọc bằng giọng nói tiếng Việt.

## ✨ Tính năng nổi bật

- **Giao diện tối ưu cho ô tô**: Thiết kế landscape, font chữ lớn, độ tương phản cao, dễ dàng thao tác khi đang lái xe.
- **Tóm tắt bằng AI (Gemini)**: Tự động tóm tắt nội dung bài báo thành các ý chính ngắn gọn, dễ hiểu.
- **Đọc tin bằng giọng nói (TTS)**: Hỗ trợ giọng nói tiếng Việt tự nhiên, tự động chia nhỏ văn bản dài để đọc mượt mà.
- **Lấy nội dung đầy đủ**: Tự động truy cập link bài báo (VnExpress, Tuổi Trẻ,...) để lấy nội dung đầy đủ nếu RSS chỉ có mô tả ngắn.
- **Quản lý nguồn tin**: Dễ dàng thêm/sửa/xóa các nguồn RSS yêu thích.
- **Hệ thống Failover**: Tự động chuyển đổi giữa nhiều API Key và Model Gemini nếu gặp lỗi giới hạn (Quota).
- **Debug Logs**: Màn hình theo dõi log chi tiết quá trình gọi API và xử lý văn bản.

## 🛠 Công nghệ sử dụng

- **Ngôn ngữ**: Kotlin
- **UI Framework**: Jetpack Compose
- **Database**: Room Persistence Library
- **Networking**: OkHttp, Kotlin Serialization
- **AI**: Google Gemini API (Generative AI)
- **TTS**: Android TextToSpeech API
- **Kiến trúc**: MVVM (Model-View-ViewModel)

## 🚀 Cài đặt & Sử dụng

1. Mở project bằng Android Studio (Koala hoặc mới hơn).
2. Build và cài đặt file APK lên thiết bị Android Box hoặc máy ảo.
3. Thêm các nguồn RSS trong phần **Cài đặt**.
4. Chọn một tin tức trên màn hình chính để AI tóm tắt và đọc.

## 🐛 Debugging

Nếu gặp lỗi về tóm tắt hoặc TTS, bạn có thể vào **Settings -> Debug Logs** để xem chi tiết phản hồi từ API và copy log gửi cho nhà phát triển.

---
*Phát triển bởi Skul9x*
