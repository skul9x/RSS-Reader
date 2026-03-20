<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="RSS Reader Logo" width="128"/>
  <h1>RSS Reader (Refactored)</h1>
  <p><strong>Ứng dụng đọc tin tức RSS hiện đại với khả năng Tóm tắt bằng AI và Đọc văn bản (TTS)</strong></p>
</div>

---

## 🚀 Giới thiệu
**RSS Reader** là một ứng dụng đọc báo thông minh dành cho Android, được thiết kế để mang lại trải nghiệm tiện lợi nhất cho người dùng – đặc biệt là với tài xế hoặc những người đang bận tay. Thay vì phải chằm chằm duyệt qua hàng chục bài viết dài, ứng dụng sẽ tóm tắt nội dung cốt lõi và đọc nó lên thành tiếng thông qua công nghệ Text-to-Speech rành mạch.

Dự án này là phiên bản đã được **refactor** toàn diện từ kiến trúc, giao diện (Jetpack Compose), đến việc tối ưu hoá luồng xử lý dữ liệu và mạng, bổ sung tính năng tự động chuyển trạng thái chân dung sang cảnh quan màn hình cực kì phù hợp với việc lái xe.

## ✨ Tính năng nổi bật
*   **📡 Tự động lấy tin (Auto-fetch):** Đọc RSS Feed từ các nguồn phổ biến tại Việt Nam.
*   **🤖 AI Summarization:** Kết nối với mô hình Ngôn Ngữ tự động gom gọn các bản tin dài thành các bản tóm tắt siêu ngắn gọn, dễ hiểu chỉ trong vài giây.
*   **🗣 Đọc văn bản (TTS Engine):** Tuỳ chỉnh đọc to tin tức. Hỗ trợ thay đổi giọng và tốc độ – rất an toàn cho người thao tác trên xe.
*   **🔄 Đọc liên tục (Continuous Reading):** Chỉ cần nhấn giữ nút Play, app sẽ tự động tóm tắt và đọc qua toàn bộ list tin từ trên xuống dưới mà không cần chạm màn hình. Có phản hồi haptic & banner trạng thái xịn sò.
*   **📱 Nhận diện Giao diện Động (Portrait/Landscape):** Tự động chuyển đổi layout từ dạng thẻ dọc sang chế độ bảng điều khiển cho xe ô tô dạng Card Row ngang vô cùng tối giản. Hẹn giờ, Wifi đều có mặt trong tap.
*   **⏯ Resumable Audio:** Đang nghe dở bỏ đi? Lần sau mở lại app hiện gợi ý đọc tiếp bản tin đang ngắt quãng.

## 🛠 Tech Stack
- **Ngôn ngữ:** Kotlin 100%
- **Giao diện:** Jetpack Compose (Material Design 3)
- **Kiến trúc:** MVVM (Model-View-ViewModel) kết hợp Uni-directional Data Flow (UDF)
- **Networking:** Retrofit2 + OkHttp + XML/HTML Parser
- **Cơ sở dữ liệu:** Room Database (Offline Caching)
- **Asynchronous/Concurrency:** Kotlin Coroutines & Flow
- **Background Work:** Đã tích hợp Foreground Services (Service đọc tin nền không bị ngắt) & MediaBrowserServiceCompat (Đồng bộ với nút cứng trên tay lái).

## 🎮 Cách sử dụng
1. Mở ứng dụng, thêm link nguồn RSS yêu thích (VD: VnExpress, Tuổi Trẻ, Thanh Niên...).
2. Tại màn hình chính, app tự tải xuống các bản tin mới nhất.
3. **Đọc 1 bài:** Click thẳng thẻ bài để đọc.
4. **Đọc tóm tắt:** Nhấn nút đọc (Play), AI sẽ tự tóm tắt và tự phát âm.
5. **Chế độ đa nhiệm ôtô (Continuous Mode):** Ở bất cứ màn hình nào (Dọc/Ngang), nhấn giữ 2s vào nút "5 Tin", App tự kích hoạt chế độ tự động hoá toàn bộ — phù hợp 100% cho việc vừa lái xe vừa nghe ngóng thế giới.

## 📥 Cài đặt
* Yêu cầu Android 8.0 (API level 26) trở lên.
* Tải xuống và cài đặt tập tin APK gốc hoặc clone trực tiếp repo về để compile.
```bash
git clone https://github.com/skul9x/RSS-Reader.git
cd RSS-Reader
./gradlew assembleDebug
```

---
*Phát triển bởi [skul9x](https://github.com/skul9x)*
*Copyright © 2026 Nguyễn Duy Trường*
