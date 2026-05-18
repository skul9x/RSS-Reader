# Plan: Universal Dynamic Multi-Model API Rotation & Quota Management
Created: 2026-05-18 09:43
Status: 🟡 In Progress

Chào anh! Em là Hà, Product Manager của dự án. Em đã đọc rất kỹ đặc tả trong file [rotation.md](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/rotation.md) cũng như cấu trúc code hiện tại của chúng ta. 

Hiện tại, cơ chế xoay vòng khóa API (Key-Second) và quản lý 429/503 của chúng ta đã khá tốt, tuy nhiên danh sách các Model AI và mức độ ưu tiên vẫn đang được **code cứng (Hardcoded)** dưới dạng `enum` và danh sách tĩnh. Điều này vi phạm nguyên tắc tùy biến cao của đặc tả ở Mục 5: **Đặc tả Giao diện Quản lý Mô hình Động**. 

Do đó, em xin đề xuất một kế hoạch nâng cấp toàn diện chia làm **4 giai đoạn (Phases)** rõ ràng để biến hệ thống xoay vòng model của chúng ta trở thành **Dynamic & Self-Healing** thực sự, kèm theo các file test tương ứng cho từng Phase để đảm bảo chất lượng tuyệt đối.

---

## 🛠️ Tech Stack & Architecture

- **Data Layer**: Lưu trữ danh sách mô hình tùy chỉnh dạng JSON thông qua `SharedPreferences` bảo mật (hoặc `EncryptedSharedPreferences` nếu cần).
- **Core Logic**: Xây dựng `ModelManager` độc lập để quản lý CRUD danh sách Model động và cơ chế **Self-Healing** (Tự khôi phục danh sách mặc định khi rỗng).
- **Orchestrator**: Cập nhật `GeminiApiClient` để tích hợp danh sách động từ `ModelManager`, sắp xếp độ ưu tiên theo Model được chọn trước, chạy cơ chế xoay vòng tinh tế.
- **UI/UX (Compose)**: Thay thế giao diện RadioButton tĩnh bằng một danh sách động sang trọng, hỗ trợ CRUD nhanh chóng (Add Model, Edit Model, Delete Model) với hiệu ứng Material 3 cao cấp.

---

## 📊 Kế hoạch các Phases (Progress Tracker)

| Phase | Tên Giai Đoạn | Mô Tả | Trạng Thế | Tiến Độ |
|-------|---------------|-------|------------|---------|
| 01 | **Core Setup & Self-Healing** | Xây dựng `ModelManager` và cơ chế lưu trữ bền vững với tính năng tự khôi phục danh sách mặc định. | ⬜ Pending | 0% |
| 02 | **Client Integration & Priority** | Tích hợp danh sách động vào `GeminiApiClient`, tinh chỉnh thứ tự ưu tiên của các model. | ⬜ Pending | 0% |
| 03 | **Premium UI CRUD** | Thiết kế giao diện quản lý mô hình động cực kỳ xịn sò trong Settings Screen. | ⬜ Pending | 0% |
| 04 | **Verification & Robustness** | Chạy kiểm thử tự động, thử nghiệm các kịch bản lỗi mạng, cạn quota 429, server bận 503. | ⬜ Pending | 0% |

---

## ⚡ Các lệnh nhanh (Quick Commands)
- Xem chi tiết Phase 1: Mở [phase-01-core-setup.md](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/plans/260518-0943-dynamic-model-rotation/phase-01-core-setup.md)
- Xem chi tiết Phase 2: Mở [phase-02-client-integration.md](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/plans/260518-0943-dynamic-model-rotation/phase-02-client-integration.md)
- Xem chi tiết Phase 3: Mở [phase-03-ui-settings.md](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/plans/260518-0943-dynamic-model-rotation/phase-03-ui-settings.md)
- Xem chi tiết Phase 4: Mở [phase-04-testing.md](file:///home/skul9x/Desktop/Test_code/RSS-Reader-main/plans/260518-0943-dynamic-model-rotation/phase-04-testing.md)

---

## ❓ Hướng đi tiếp theo
Em đã tạo xong kế hoạch tổng quan và sẽ chi tiết hóa từng Phase dưới đây. Em rất mong nhận được phản hồi từ anh!
