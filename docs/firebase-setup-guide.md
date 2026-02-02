# Firebase Setup Guide cho RSS Reader

## Bước 1: Tạo Firebase Project

1. Truy cập: https://console.firebase.google.com/
2. Click **"Add project"** (hoặc "Thêm dự án")
3. Tên project: `RSS-Reader-Sync` (hoặc tên khác)
4. Click **Continue** → Disable Google Analytics → **Create project**

## Bước 2: Enable Authentication

1. Trong project → Click **Authentication** ở menu trái
2. Click **Get Started**
3. Tab **Sign-in method** → Click **Google**
4. Toggle **Enable** → Click **Save**

## Bước 3: Enable Firestore

1. Click **Firestore Database** ở menu trái
2. Click **Create database**
3. **Màn hình "Select edition":** Chọn **Standard edition** → Click **Next**
4. **Màn hình "Location":** Chọn `asia-southeast1` (hoặc location gần nhất) → Click **Next**
5. **Màn hình "Secure rules" (nếu có):** Chọn **Start in test mode** → Click **Create/Enable**

## Bước 4: Add Android App

1. Nhìn lên góc trên cùng bên trái, bấm vào **biểu tượng bánh răng (⚙️)** bên cạnh chữ "Project Overview".
2. Chọn **Project settings**.
3. Cuộn xuống phần **Your apps**, bấm vào biểu tượng **Android** (hình con robot).
4. **Android package name:** `com.skul9x.rssreader`
3. **App nickname:** RSS Reader
4. Click **Register app**
5. **Download `google-services.json`**
6. **Đặt file vào:** `app/google-services.json`
7. Click **Next** → **Next** → **Continue to console**

## Bước 5: Lấy SHA-1 Certificate

Mở PowerShell trong thư mục project:
```powershell
.\gradlew signingReport
```

Copy dòng SHA-1 (dạng `12:34:56:AB:...`)

Sau đó:
1. Firebase Console → **Project Settings** (⚙️)
2. Tab **Your apps** → Chọn app Android
3. **SHA certificate fingerprints** → **Add fingerprint**
4. Paste SHA-1 → **Save**

---

**Hoàn thành:** Đã có `google-services.json` và SHA-1 certificate!
