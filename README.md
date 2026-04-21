# RSS Reader

## Mô tả dự án
RSS Reader là một ứng dụng đọc báo và theo dõi tin tức trực tuyến tiện lợi trên nền tảng Android. Ứng dụng hỗ trợ gom nhóm và tự động lấy tin (cache) từ các nguồn RSS feed để bạn có thể đọc offline bất kỳ lúc nào. Điểm nhấn của ứng dụng chính là tích hợp trí tuệ nhân tạo (Gemini) để tóm tắt các bài báo dài, cũng như hệ thống bóc tách nội dung thông minh (Readability4J) xử lý được cả các trang web không theo chuẩn cấu trúc.

## Mô tả công nghệ
- **Nền tảng**: Android (Kotlin)
- **Giao diện**: Jetpack Compose (Material 3)
- **Cơ sở dữ liệu cục bộ**: SQLite thông qua thư viện Room
- **Đồng bộ hóa / Backend**: Firebase (Auth, Firestore), WorkManager 
- **AI Tóm tắt**: Google Gemini API (hỗ trợ mô hình Flash 2.5 và Flash 3.0 Lite)
- **Xử lý nội dung (HTML/RSS)**: Jsoup, Readability4J (tổng hợp dữ liệu thông minh)

## Tính năng nổi bật
- Đọc tin tức offline, đọc liên tục (Continuous reading).
- Tóm tắt và dịch thuật bài báo với Gemini AI.
- Hệ thống Fallback trích xuất thông minh đa tầng.
- Tự động thay đổi tài khoản API (Model-First Rotation) giúp tiết kiệm quota.

## Hướng dẫn cài đặt
1. Clone dự án về máy:
   ```bash
   git clone https://github.com/skul9x/RSS-Reader.git
   ```
2. Mở thư mục dự án bằng **Android Studio**.
3. Thêm file cấu hình Firebase (`google-services.json`) vào thư mục `app/` nếu bạn muốn chạy tính năng đồng bộ.
4. Xây dựng (build) và chạy (run) ứng dụng lên máy ảo (Emulator) hoặc thiết bị thật.

## Cách sử dụng
- Mở danh sách các kênh (Feeds), chọn một bài viết để đọc nội dung.
- Dùng tính năng Tóm tắt hoặc Dịch bằng cách cấu hình API key cá nhân (Gemini) trong phần Settings.
- Ứng dụng sẽ tự động tải các tin tức mới nhất thông qua dịch vụ chạy ngầm.

## Cấu trúc thư mục định hướng
- `app/src/main/java/.../rssreader`: Thư mục mã nguồn chính.
    - `/data`: Xử lý Database cục bộ (Room), Network API (Gemini), Parse RSS và bóc tách HTML (extractors).
    - `/ui`: Xây dựng giao diện Compose (Screens, Components, Theme).
    - `/service`: Dịch vụ ngầm tải/đọc tin liên tục.
    - `/utils`: Các hàm tiện ích dùng chung (AppLogger, HtmlCleaner).
- `docs/`: Tài liệu tham khảo, kịch bản specs và kế hoạch xây dựng kiến trúc mở rộng.
- `.brain/`: Thư mục của hệ thống Antigravity lưu lại bối cảnh (context), trạng thái của các phiên làm việc và tài liệu dự án để tối ưu hóa quản lý AI.

## Thông tin bổ sung
Nếu tìm thấy lỗi hoặc có nhu cầu phát triển thêm, vui lòng tạo **Issue** hoặc **Pull Request** trực tiếp tại GitHub. 

## Bản quyền
Copyright 2026 Nguyễn Duy Trường
