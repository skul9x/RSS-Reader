# Phase 01: Core Fetch & Redirect
Status: ⬜ Pending | ✅ Complete | ⬜ Complete
Dependencies: None

## Objective
Sửa lỗi font chữ do tự ý ép kiểu String (Charset), tối ưu CPU bằng cách bắt "Voz Redirect" qua Regex và nới lỏng Check Anti-bot.

## Requirements
### Functional
- [x] Đọc trực tiếp `InputStream` (byteStream) của OkHttp đẩy cho `Jsoup.parse` (bỏ ép String UTF-8 sớm).
- [x] Gỡ bỏ/thay đổi điều kiện hardcode `html.length < 500` trong logic Anti-bot.
- [x] Triển khai Regex bắt link Voz Redirect trước khi kích hoạt `Jsoup.parse`.

### Non-Functional
- [x] Bảo toàn nguyên gốc bảng mã (charset) của các trang báo cổ (ví dụ `windows-1258`, `utf8` k có gạch ngang).
- [x] Tiết kiệm CPU, không tốn công dựng cây DOM khổng lồ của forum Voz cho những đường link chuyển hướng.

## Implementation Steps
1. [x] Sửa `HtmlFetcher.kt` / `ArticleContentFetcher.kt`: Thay vì dùng `resp.body?.string()`, chuyển sang giữ lại byte array hoặc truyền `resp.body!!.byteStream()` cho Jsoup (Lưu ý vẫn cần cách lấy String raw cho Regex quét trước khi ném vào Jsoup).
2. [x] Sửa `RedirectResolver.kt` (hoặc nơi liên quan): Thêm hàm (`extractVozOriginalLinkRegex`) chạy regex quét chuỗi HTML thô tìm redirect URL.
3. [x] Cập nhật `ArticleContentFetcher.kt` hàm `fetchArticleWithTitle`: Đưa logic check Voz Redirect lên ngay dưới AI-Hay redirect. Nới lỏng điều kiện kiểm tra độ dài payload đối với Anti-bot.

## Files to Create/Modify
- `app/src/main/java/com/skul9x/rssreader/data/network/HtmlFetcher.kt` - [Sửa read stream và charset]
- `app/src/main/java/com/skul9x/rssreader/data/network/ArticleContentFetcher.kt` - [Sửa logic parse và Anti-bot check]
- `app/src/main/java/com/skul9x/rssreader/data/network/RedirectResolver.kt` - [Thêm regex bắt link Voz redirect]

## Test Criteria
- [ ] Các bài báo có thẻ `<meta charset="windows-1258">` hiểu đúng tiếng Việt, không bị lỗi font kí tự lạ.
- [ ] Link Voz chuyển hướng (như theNEXTvoz) tự động nhảy sang original link mà không tạo gánh nặng RAM/CPU để parse DOM.
- [ ] Trang có Anti-Bot Javascript obfuscate dung lượng lớn (>500 bytes) tự động nhận diện và retry thành công.

---
Next Phase: phase-02-memory-logic.md
