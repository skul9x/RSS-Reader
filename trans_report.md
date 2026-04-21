# Báo cáo Phân tích Luồng Dịch Tiêu đề (Gemini API)

Báo cáo này tổng hợp các phân tích về kiến trúc, hiệu năng và các lỗi logic hiện có trong luồng dịch tiêu đề của ứng dụng RSS Reader.

---

## 1. Tổng quan Luồng hoạt động (Workflow)

Hệ thống hiện triển khai dịch tự động tại `MainViewModel` và `NewsReaderService` với cơ chế như sau:
1. **Lọc nội dung:** Sử dụng `VietnameseDetector` để chỉ dịch các tiêu đề không phải tiếng Việt.
2. **Cơ chế Batch (Hàng loạt):** Gom các tiêu đề vào một Map `ID -> Title`, chuyển thành JSON và gửi một Request duy nhất tới Gemini.
3. **Cơ chế Single (Đơn lẻ):** Gửi từng đoạn text riêng biệt (ít được sử dụng trong luồng chính).
4. **Xử lý kết quả:** Sử dụng Regex để tách khối JSON từ response text và parse ngược lại thành dữ liệu ứng dụng.

---

## 2. So sánh Hiệu năng: Single vs Batch

| Đặc điểm | Dịch Single | Dịch Batch (Hiện tại) |
| :--- | :--- | :--- |
| **Số lượng Request** | N request / N tiêu đề | 1 request / N tiêu đề |
| **Tốc độ (Latency)** | Chậm (tổng độ trễ tích lũy) | Nhanh (chỉ mất 1 lần round-trip) |
| **Quota API** | Tốn kém, dễ bị chặn (429) | Tiết kiệm, tối ưu hóa quota |
| **Độ tin cây** | Cao (Lỗi mục nào chỉ hỏng mục đó) | Trung bình (Lỗi 1 mục hỏng cả Batch) |

---

## 3. Các vấn đề kỹ thuật & Lỗi sụt giảm hiệu năng

Dựa trên phân tích mã nguồn (`GeminiApiClient.kt`), dưới đây là 4 vấn đề "chí mạng" đang tồn tại:

### ⚠️ Lỗi 1: Nút thắt cổ chai do Hardcode Model
Khác với luồng tóm tắt bài viết có danh sách dự phòng (`MODELS`), luồng dịch bị fix cứng chỉ dùng model `models/gemini-flash-lite-latest`.
- **Hậu quả:** Nếu model này quá tải hoặc hết quota, tính năng dịch sẽ chết hoàn toàn ngay cả khi các model khác (`gemini-2.5-flash`) vẫn đang rảnh.

### ⚠️ Lỗi 2: Xử lý JSON bằng Regex cực kỳ rủi ro
Việc ép AI trả về JSON qua prompt rồi dùng Regex `\{[\\s\\S]*\}` để "mò" kết quả là một giải pháp thiếu ổn định.
- **Hậu quả:** Chỉ cần Gemini thêm một vài câu giải thích hoặc dùng sai dấu ngoặc, toàn bộ đợt dịch sẽ thất bại. Hệ thống lại tiếp tục Retry với Key khác, gây lãng phí tài nguyên cho một lỗi không thuộc về API Key.

### ⚠️ Lỗi 3: Hiệu ứng "All-or-Nothing" của Safety Filter
Gemini áp dụng bộ lọc an toàn cho toàn bộ response. 
- **Hậu quả:** Nếu trong batch 5 tin có **duy nhất 1 tin** chứa từ khóa nhạy cảm bị AI chặn, Gemini sẽ không trả về bất cứ gì cho cả 5 tin đó. Người dùng sẽ thấy 4 tin bình thường cũng không được dịch.

### ⚠️ Lỗi 4: Vòng lặp Retry "Mù quáng" khi mất mạng
Khi gặp lỗi `IOException` (mất mạng, timeout), code hiện tại vẫn `continue` để thử với API Key tiếp theo.
- **Hậu quả:** Nếu bạn có 10 API Key, ứng dụng sẽ gọi lỗi 10 lần liên tục trước khi chịu dừng. Việc này gây lãng phí CPU, treo log và làm nóng thiết bị vô ích.

---

## 4. Đề xuất Khắc phục & Tối ưu hóa

Để đạt được hiệu năng cao nhất và trải nghiệm "premium", cần thực hiện các thay đổi sau:

1.  **Đồng bộ Model Fallback:** Sử dụng danh sách `MODELS` tương tự như luồng tóm tắt để tự động chuyển đổi khi model Lite gặp sự cố.
2.  **Kích hoạt JSON Mode chuẩn:** Trong request body, thiết lập `responseMimeType = "application/json"`. Điều này bắt buộc Gemini trả về JSON chuẩn, không cần dùng Regex để cắt gọt.
3.  **Cơ chế Fail-Fast (Dừng sớm):** Phân loại lỗi mạng (IOException) để thoát vòng lặp Retry ngay lập tức thay vì thử các API Key khác trong vô vọng.
4.  **Cơ chế Fallback Single-Translation:** Nếu dịch Batch thất bại, hệ thống nên tự động thử dịch lẻ từng tiêu đề để đảm bảo những tiêu đề "sạch" vẫn được dịch thành công.
5.  **Chunking (Chia nhỏ mẻ dịch):** Đảm bảo mỗi đợt dịch Batch không quá 10-20 tiêu đề để tránh hiện tượng AI "mất trí nhớ" hoặc làm phình to Prompt quá mức.

---
**Người báo cáo:** Antigravity AI Assistant
**Ngày lập:** 21/04/2026
