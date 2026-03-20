# RSS Reader - Trình Đọc Tin Chuyên Sâu

RSS Reader là một ứng dụng Android hiện đại được thiết kế để mang lại trải nghiệm đọc tin tức tối ưu nhất. Thay vì chỉ hiển thị các đoạn tóm tắt ngắn từ RSS, ứng dụng này có khả năng trích xuất nội dung đầy đủ một cách thông minh từ các trang báo điện tử hàng đầu Việt Nam.

## 🚀 Tính Năng Nổi Bật

- **Trích xuất nội dung thông minh**: Tích hợp các bộ lọc (extractors) riêng biệt cho từng trang báo lớn như VnEconomy, VnExpress, Dân Trí, Tuổi Trẻ, Thanh Niên, VietnamNet, VTC News, GenK...
- **Giao diện sạch sẽ**: Loại bỏ hoàn toàn quảng cáo, menu rườm rà, các thành phần gây xao nhãng để người dùng tập trung vào nội dung bài báo.
- **Tích hợp AI (Gemini)**: Sử dụng trí tuệ nhân tạo để tóm tắt nội dung, phân tích tin tức và cung cấp các góc nhìn đa chiều.
- **Lưu trữ ngoại tuyến**: Tự động lưu cache bài viết và hình ảnh để người dùng có thể đọc lại ngay cả khi không có kết nối mạng.
- **Tùy biến cao**: Cho phép thêm các nguồn RSS tùy ý, quản lý danh mục tin tức và theo dõi lịch sử đọc bài.

## 📂 Cấu Trúc Dự Án

- `/app/src/main/java/com/skul9x/rssreader/data`: Chứa logic xử lý dữ liệu, bao gồm cơ sở dữ liệu nội bộ (Room/SQLite) và các bộ extractor nội dung bằng Regex/Jsoup.
- `/app/src/main/java/com/skul9x/rssreader/ui`: Hệ thống các màn hình Compose UI, ViewModels quản lý trạng thái ứng dụng.
- `/app/src/main/java/com/skul9x/rssreader/utils`: Các công cụ tiện ích xử lý HTML, làm sạch văn bản và định dạng ngày tháng.
- `.brain`: Thư mục lưu trữ ngữ cảnh hoạt động và kiến thức được tích lũy bởi hệ thống AI hỗ trợ (Antigravity).

## 🛠️ Hướng Dẫn Cài Đặt

### Điều kiện cần
- Android Studio Ladybug (2024.2.1) hoặc phiên bản mới hơn.
- JDK 17+.

### Các bước thực hiện
1. Clone dự án về máy tính:
   ```bash
   git clone https://github.com/skul9x/RSS-Reader.git
   ```
2. Mở dự án trong Android Studio.
3. Chỉnh sửa file `local.properties` để cấu hình đường dẫn Android SDK nếu được yêu cầu.
4. Chạy Gradle sync và Build dự án.
5. Cài đặt trực tiếp lên thiết bị Android hoặc máy ảo.

## 📝 Cách Sử Dụng

1. Tại màn hình chính, nhấn vào biểu tượng thêm và nhập URL RSS mà bạn muốn theo dõi.
2. Ứng dụng sẽ tự động tải danh sách tin bài mới nhất.
3. Chạm vào một bài viết để bắt đầu đọc nội dung đã được làm sạch hoàn toàn.
4. Sử dụng tính năng AI (nếu đã cấu hình API Key) để tóm tắt các bài viết dài.

## ⚖️ Bản quyền

Copyright 2026 Nguyễn Duy Trường
