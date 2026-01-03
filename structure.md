# Project Structure - RSS Reader

Sơ đồ cấu trúc thư mục và các thành phần chính của ứng dụng:

```text
app/src/main/java/com/skul9x/rssreader/
├── data/
│   ├── local/              # Lưu trữ dữ liệu cục bộ (Room)
│   │   ├── AppDatabase.kt  # Khởi tạo database
│   │   └── RssFeedDao.kt   # Truy vấn nguồn RSS
│   ├── model/              # Các lớp dữ liệu (Data Models)
│   │   ├── NewsItem.kt     # Model tin tức (In-memory)
│   │   └── RssFeed.kt      # Entity nguồn RSS (Database)
│   ├── network/            # Xử lý mạng và API
│   │   ├── ArticleContentFetcher.kt # Lấy nội dung full từ HTML
│   │   ├── GeminiApiClient.kt       # Giao tiếp với Google Gemini
│   │   └── RssParser.kt             # Parse dữ liệu RSS/Atom
│   └── repository/         # Tầng trung gian dữ liệu
│       └── RssRepository.kt         # Quản lý fetch tin từ nhiều nguồn
├── tts/                    # Xử lý giọng nói
│   └── TtsManager.kt       # Quản lý Android TextToSpeech
├── ui/                     # Giao diện người dùng (Compose)
│   ├── components/         # Các thành phần UI dùng chung
│   │   └── NewsCard.kt     # Card hiển thị tin tức
│   ├── feeds/              # Màn hình quản lý nguồn RSS
│   │   ├── FeedManagementScreen.kt
│   │   └── RssManagementViewModel.kt
│   ├── main/               # Màn hình chính (News Display)
│   │   ├── MainScreen.kt
│   │   └── MainViewModel.kt
│   ├── settings/           # Màn hình cài đặt và logs
│   │   ├── DebugLogsScreen.kt
│   │   └── SettingsScreen.kt
│   └── theme/              # Cấu hình Theme, Color, Type
├── util/                   # Tiện ích hệ thống
│   └── DebugLogManager.kt  # Quản lý log in-memory
└── MainActivity.kt         # Điểm vào ứng dụng & Navigation
```

## Chi tiết các thành phần chính:

1.  **GeminiApiClient**: Chứa logic auto-failover giữa 3 API Key và 5 Model (Gemini 1.5 Flash, Pro, 2.0 Flash). Có hàm `cleanForTts` để dọn dẹp Markdown.
2.  **TtsManager**: Xử lý chia nhỏ văn bản (chunking) theo câu để tránh giới hạn độ dài của engine TTS Android.
3.  **ArticleContentFetcher**: Sử dụng Regex để bóc tách nội dung chính từ HTML của các trang báo lớn (VnExpress, Tuổi Trẻ,...) khi RSS chỉ cung cấp summary.
4.  **MainViewModel**: Điều phối luồng dữ liệu: Fetch RSS -> Lấy Full Content -> Gọi Gemini Summarize -> Gọi TTS Speak.
5.  **DebugLogManager**: Lưu trữ 500 log gần nhất để hiển thị trực tiếp trên app, giúp debug nhanh trên Android Box mà không cần máy tính.
