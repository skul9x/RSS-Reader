# RSS Reader dành cho Android

Ứng dụng RSS Reader với thiết kế mượt mà, hỗ trợ cập nhật tin tức từ các nguồn RSS phổ biến tại Việt Nam và quốc tế. Dự án sử dụng cấu trúc Clean Architecture cơ bản, kết hợp Room Database để caching offline, hỗ trợ đọc báo không cần mạng mọi lúc mọi nơi. Nổi bật với khả năng đồng bộ (sync) vị trí và danh sách đọc giữa các thiết bị thông qua Firebase Firestore.

## Tóm tắt tính năng chính
- Tự động lấy danh sách tin tức từ file XML/RSS feed.
- Lưu trữ ngoại tuyến (Offline reading) bằng dữ liệu cache (Room DB).
- Đánh dấu tin đã đọc và tự động đồng bộ (Google Authentication & Firestore).
- Bộ đệm ram cục bộ tự giải phóng thông minh chống Memory Leak.
- Giải quyết tình trạng partial sync bằng chia chunk trực tiếp tại Coordinator.

## Yêu cầu hệ thống
- Android Studio Iguana / Jellyfish trở lên.
- Gradle phiên bản v8+.
- JDK 17.

## Hướng dẫn cài đặt và thiết lập
1. **Clone dự án về máy:**
   ```bash
   git clone https://github.com/skul9x/RSS-Reader.git
   ```
2. **Mở dự án:**
   Mở thư mục `RSS-Reader` thông qua Android Studio. Gradle sẽ tự động tải các gói phụ thuộc.
3. **Cấu hình Local Properties:**
   Khai báo đường dẫn Android SDK của bạn trong file `local.properties`.
4. **Cấu hình Firebase (Bắt buộc nếu muốn test tính năng Sync):**
   Thay thế hoặc tải file `google-services.json` cho dự án của bạn và đặt vào thư mục `app/`.

## Cấu trúc dự án
- `app/src/main/java/com/skul9x/rssreader/data/local/` - Các thành phần giao tiếp cơ sở dữ liệu local (Room DB - DOAs).
- `app/src/main/java/com/skul9x/rssreader/data/network/` - Chứa parser lấy data RSS từ internet qua network.
- `app/src/main/java/com/skul9x/rssreader/data/remote/` - Nơi chứa các repository làm việc với API Backend & Firestore (như `FirestoreSyncRepository.kt`).
- `app/src/main/java/com/skul9x/rssreader/data/sync/` - Chứa logic phối hợp (Coordinator) giữa Room và Firestore.
- `app/src/main/java/com/skul9x/rssreader/data/model/` - Model objects.
- `app/src/test/` - Hệ thống unit tests.

## Bản quyền
Copyright 2026 Nguyễn Duy Trường
