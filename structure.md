# Cấu trúc Dự án - Android RSS Reader 📂

Sắp xếp thư mục và vai trò của các thành phần chính trong bản build hiện tại:

```text
app/src/main/java/com/skul9x/rssreader/
├── data/
│   ├── local/              # Lưu trữ dữ liệu cục bộ (Room & Prefs)
│   │   ├── AppDatabase.kt      # Cấu hình Room Database
│   │   ├── AppPreferences.kt   # Quản lý SharedPreferences (Theme, Audio Stream)
│   │   ├── ApiKeyManager.kt    # Quản lý danh sách Gemini API Keys
│   │   ├── NewsSummary.kt      # Entity lưu trữ nội dung tóm tắt (Cache)
│   │   ├── NewsSummaryDao.kt   # Truy vấn Cache tóm tắt
│   │   └── RssFeedDao.kt       # Quản lý nguồn tin RSS
│   ├── model/              # Các Data Models
│   │   └── NewsItem.kt         # Model chứa thông tin bài báo
│   ├── network/            # Giao tiếp mạng
│   │   ├── ArticleContentFetcher.kt # Scraping nội dung từ web HTML
│   │   ├── GeminiApiClient.kt       # Logic gọi AI & Auto-failover model
│   │   └── RssParser.kt             # Phân tích dữ liệu XML (RSS/Atom)
│   └── repository/         # Tầng dữ liệu chung (Single Source of Truth)
│       └── RssRepository.kt         # Logic phối hợp local & network
├── media/                  # Xử lý điều khiển bằng phím cứng (Plan B)
│   ├── MediaButtonReceiver.kt # Broadcast Receiver bắt phím media
│   └── MediaButtonManager.kt  # Quản lý MediaSession chuẩn
├── service/                # Thành phần chạy ngầm
│   └── NewsReaderService.kt   # Foreground Service xử lý đọc tin & Notification
├── tts/                    # Xử lý giọng nói
│   └── TtsManager.kt          # Quản lý engine TextToSpeech Android
├── ui/                     # Giao diện Jetpack Compose
│   ├── main/                  # Màn hình chính (Landscape/Portrait)
│   │   ├── MainScreen.kt
│   │   └── MainViewModel.kt
│   ├── settings/              # Cấu hình hệ thống
│   │   └── SettingsScreen.kt      # Tích hợp toàn bộ cài đặt & Debug Log UI
│   └── theme/                 # Design System (Màu sắc, Font chữ)
├── utils/                  # Tiện ích bổ trợ
│   └── DebugLogger.kt         # Singleton quản lý log thời gian thực cho UI
└── MainActivity.kt         # Điểm khởi đầu app & Navigation logic
```

## Giải thích các thành phần quan trọng:

1.  **NewsReaderService**: Trái tim của ứng dụng. Đây là một `Foreground Service` giúp app có thể đọc tin liên tục ngay cả khi người dùng chuyển sang Google Maps hay tắt màn hình. Nó cũng quản lý Notification thông minh hỗ trợ nút Play/Pause/Next/Prev.
2.  **DebugLogger**: Singleton thu thập log từ mọi nơi (Service, AI Client, Key Events). Dữ liệu này được hiển thị trực tiếp trong mục "Debug Log" ở Settings, cực kỳ hữu ích để kiểm tra xem phím vô lăng của xe có hoạt động hay không.
3.  **GeminiApiClient**: Xử lý logic AI phức tạp. Hỗ trợ tự động chuyển model (từ Pro sang Flash) nếu overload và dọn dẹp nội dung thô thành văn bản sạch cho TTS đọc.
4.  **TtsManager**: Không chỉ gọi lệnh `speak`, class này còn thực hiện "Sentence Chunking" - chia nhỏ bài báo thành từng câu để engine TTS phát âm mượt mà nhất, tránh bị lag hay mất tiếng ở giữa chừng.
5.  **Media Layer**: Sự kết hợp giữa `MediaSession` (Plan A) và `MediaButtonReceiver` (Plan B) giúp app "bất tử" trước mọi loại Android Box ô tô, đảm bảo phím bấm luôn có tác dụng.
