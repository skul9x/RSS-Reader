# Phase 03: Tích hợp vào GeminiApiClient

## Mục tiêu
Thay đổi logic `summarizeWithRetry` để ưu tiên model người dùng đã chọn thay vì luôn bắt đầu từ đầu danh sách `MODELS` cố định.

## Công việc
- [x] Mở `GeminiApiClient.kt`.
- [x] Trong hàm `summarizeWithRetry`, lấy model đã chọn từ `AppPreferences`.
- [x] Sắp xếp lại (reorder) danh sách `MODELS` tạm thời trong hàm (save để lần sau mở app ra thì vẫn dùng thứ tự models như lần save gần nhất): đưa model được chọn lên vị trí đầu tiên, các model còn lại đẩy xuống dưới để vẫn giữ cơ chế failover (dự phòng) - UI hiển thị cụ thể model nào ở thứ tự nào.
- [x] Đảm bảo tính năng dịch tiêu đề (`translateToVietnamese`) vẫn dùng `gemini-flash-lite-latest` như cũ để tối ưu tốc độ.

## File liên quan
- `app/src/main/java/com/skul9x/rssreader/data/network/GeminiApiClient.kt`

## Kiểm tra
- [ ] Chọn một model cụ thể (VD: Gemini 3 Flash Preview).
- [ ] Thực hiện tóm tắt 1 bài báo.
- [ ] Kiểm tra Logcat hoặc Activity Logs để xác nhận model được gọi đầu tiên chính là model đã chọn.