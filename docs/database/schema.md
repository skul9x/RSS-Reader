# Database Schema

## Room Database (Local)

### 1. `rss_feeds` Table
Stores RSS feed sources.
- `url` (PK): Text
- `title`: Text
- `category`: Text
- `lastUpdated`: Long

### 2. `cached_news` Table
Stores fetched news items for offline reading.
- `link` (PK): Text
- `title`: Text
- `pubDate`: Long
- `sourceUrl`: Text
- `isRead`: Boolean (Legacy - migrating to `read_news`)

### 3. `read_news` Table (Updated v7)
Tracks read history and sync status.
- `newsId` (PK): Text
- `readAt`: Long
- `deviceType`: Text ("smartphone" / "androidbox")
- `syncStatus`: Text ("PENDING" / "SYNCED" / "FAILED")

### 4. `activity_logs` Table
Debug logs for crash analysis.
- `id` (PK): Auto-increment
- `timestamp`: Long
- `level`: Text
- `message`: Text
- `tag`: Text

## DataStore Preferences

### `sync_preferences`
- `last_sync_time`: Long
- `pending_sync_count`: Int
- `device_type`: String

---

## Cloud Firestore (Remote - Planned)

### Collection: `users`
Document ID: `uid` (from Firebase Auth)

#### Sub-collection: `readNews`
Document ID: `newsId`
- `n`: newsId (string) - Minimized key to save bandwidth
- `t`: readTimestamp (number)
- `d`: deviceType (string)
- `u`: updatedAt (serverTimestamp)
