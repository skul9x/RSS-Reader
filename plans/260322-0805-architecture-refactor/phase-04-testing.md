# Phase 04: Testing
Status: ⬜ Pending | 🟡 In Progress | ✅ Complete
Dependencies: phase-03-extractor-enhance.md

## Objective
Kiểm thử và nghiệm thu diện rộng để đảm bảo 7 lỗ hổng (2 file Bug) đã hoàn toàn bị lấp đi mà không gây ra tác dụng phụ (regression bugs).

## Implementation Steps
1. Kiểm thử trên Android emulator hoặc thiết bị yếu -> mở nhiều bài liên tiếp đo lượng RAM (Check OOM).
2. Tìm 2 site báo chí dùng mã `windows-1258` kiểm thử trực tiếp bằng app.
3. Test các url voz link chuyển hướng để xem tốc độ tải và CPU profile.
4. Lên danh mục review lại.
