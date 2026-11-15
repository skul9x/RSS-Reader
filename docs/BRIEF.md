# 💡 BRIEF: Tối ưu Dịch Tiêu Đề Bằng `models/gemini-flash-lite-latest`

**Ngày tạo:** 2026-11-15  
**Brainstorm cùng:** skul9x  
**Feature:** Title Translation Optimization using Gemini Flash-Lite & LLM Translation Best Practices

---

## 1. VẤN ĐỀ CẦN GIẢI QUYẾT
- Hiện tại tính năng dịch tiêu đề tiếng Anh sang tiếng Việt (`translateToVietnamese` & `translateTitleBatch`) đang bị hardcode model `"models/gemini-2.5-flash-lite"`.
- Khi Google cập nhật các bản cải tiến Flash-Lite mới (như 3.1, 3.5), app không tự động tận dụng được tốc độ và chất lượng mới nếu không sửa code.
- Cấu hình request hiện tại cho phần dịch dùng chung `GeminiResponseHelper.buildRequestBody` với `temperature = 0.7` và `maxOutputTokens = 4096` - điều này chưa tối ưu cho bài toán dịch tiêu đề (dễ sinh text lan man, tốn token không cần thiết, tăng độ trễ).

---

## 2. GIẢI PHÁP ĐỀ XUẤT
1. **Chuyển model dịch sang alias `models/gemini-flash-lite-latest`**:
   - Tự động tận dụng bản build mới nhất của dòng Flash-Lite từ Google.
   - Bổ sung fallback linh hoạt nếu model alias gặp lỗi hoặc quota.
2. **Áp dụng Best Practices dịch thuật của Google AI**:
   - **System Instruction**: Tách chỉ thị vai trò (`system_instruction`) riêng biệt khỏi user content.
   - **Temperature = 0.1 - 0.2**: Đảm bảo bản dịch chính xác, sát nghĩa, không sáng tạo tùy tiện hay thêm râu ria.
   - **maxOutputTokens nhỏ gọn**: Tiêu đề tin tức rất ngắn, chỉ cần giới hạn `maxOutputTokens = 128` (hoặc `256` cho batch) để giảm TTFT (Time To First Token).
   - **Tắt Thinking Overhead**: Với dòng `latest` / `gemini-3.x`, cấu hình `thinkingLevel: "minimal"` hoặc tắt triệt để nhằm giảm thiểu độ trễ tối đa.
   - **Giữ Structured Output (JSON Mode)**: Cho batch translation để parse kết quả an toàn.

---

## 3. ĐỐI TƯỢNG SỬ DỤNG
- Người dùng đọc tin RSS các kênh tiếng Anh (TechCrunch, The Verge, Reuters, BBC...) cần tiêu đề hiển thị mượt mà bằng tiếng Việt với tốc độ tức thì.

---

## 4. NGHIÊN CỨU & BEST PRACTICES (Google AI / Gemini API)
| Tiêu chí | Hiện tại | Đề xuất tối ưu (Best Practice) |
|---|---|---|
| **Model** | Hardcode `models/gemini-2.5-flash-lite` | `models/gemini-flash-lite-latest` (với danh sách fallback dự phòng) |
| **System Instruction** | Gộp chung trong user prompt text | Đặt tại trường `system_instruction` cấp cao nhất của API |
| **Temperature** | `0.7` (quá cao đối với bài toán dịch) | `0.1` (sát nghĩa, ổn định, tránh biến tấu từ ngữ) |
| **Max Output Tokens** | `4096` | `128` (dịch đơn) / `384` (dịch batch 15 titles) |
| **Thinking Config** | Chưa cấu hình riêng cho translation call | `thinkingLevel: "minimal"` (tiết kiệm thời gian suy nghĩ của model) |
| **Batch Size** | 15 titles/chunk | Giữ nguyên 15 titles/chunk (rất tối ưu cho quota & payload) |

---

## 5. TÍNH NĂNG & PHẠM VI TRIỂN KHAI

### 🚀 MVP (Triển khai ngay):
- [ ] Cập nhật `GeminiApiClient.kt`: chuyển `translationModels` sang ưu tiên `"models/gemini-flash-lite-latest"`, fallback sang các bản flash-lite cụ thể.
- [ ] Tối ưu hóa hàm tạo request cho translation trong `GeminiResponseHelper.kt`: hỗ trợ `system_instruction`, `temperature = 0.1`, `maxOutputTokens = 128..384`.
- [ ] Thêm model `models/gemini-flash-lite-latest` vào `ModelManager.kt` và danh sách model hỗ trợ (nếu user muốn chọn làm summarize model hoặc dynamic model).
- [ ] Viết Unit Test kiểm thử translation request body và parser.

### 🎁 Phase 2 (Cân nhắc sau):
- [ ] Cho phép người dùng tùy chỉnh model dịch trong màn hình Cài đặt (nếu muốn override).
- [ ] Caching bản dịch tiêu đề vào Room Database (đã có sẵn `CachedNewsDao.updateTranslatedTitlesBatch`).

---

## 6. ƯỚC TÍNH SƠ BỘ
- **Độ phức tạp:** 🟢 ĐƠN GIẢN - TRUNG BÌNH (chỉ cần điều chỉnh API payload và danh sách model trong tầng Network Data).
- **Rủi ro:** Không có rủi ro lớn. Cần giữ danh sách fallback để phòng trường hợp Google thay đổi quota hoặc endpoint của alias `latest`.

---

## 7. BƯỚC TIẾP THEO
→ Chạy `/plan` để lập kế hoạch chi tiết các bước code và kiểm thử.
