# Phase 02: Giao diện Cài đặt (UI)

## Mục tiêu
Tạo Section chọn Model trong màn hình Settings tương tự như các mục `AudioStreamSection`.

## Công việc
- [x] Tạo file `SummarizeModelSection.kt` sử dụng Jetpack Compose.
- [x] Hiển thị danh sách 4 model với RadioButton hoặc Checkmark để người dùng chọn.
- [x] Tích hợp vào `SettingsScreen.kt`:
    - Khai báo state `currentModel` lấy từ `appPreferences`.
    - Thêm Component mới vào Column chính của Settings.
    - Gọi `appPreferences.setSelectedSummarizeModel(it)` khi người dùng thay đổi.
- [x] Thêm thông báo `Toast` khi đổi model thành công.

## File liên quan
- `app/src/main/java/com/skul9x/rssreader/ui/settings/SummarizeModelSection.kt` (Tạo mới)
- `app/src/main/java/com/skul9x/rssreader/ui/settings/SettingsScreen.kt`

## Kiểm tra
- [ ] Mở Settings, bấm chọn model khác và kiểm tra xem UI có cập nhật đúng trạng thái đã chọn không.