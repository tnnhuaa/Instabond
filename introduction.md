# Instabond Test Run Guide

File này tổng hợp các bước chạy test app theo đúng thứ tự cho dự án `Instabond`.

## Thứ tự chạy

1. MongoDB
2. `ai-service`
3. `backend`
4. Android app

## 1. Chạy MongoDB

Backend cần MongoDB hoạt động trước.

Nếu chạy local bằng `mongod`:

```powershell
mongod --dbpath D:\data\db
```

Nếu đang chạy MongoDB bằng service hoặc MongoDB Compass thì chỉ cần đảm bảo URI sau dùng được:

```text
mongodb://localhost:27017/instabond
```

## 2. Chạy `ai-service`

### Lần đầu

```powershell
cd D:\Uni\YEAR_3\SEM_2\Mobile\Instabond\ai-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python -m pip install "numpy<2"
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

### Những lần sau

```powershell
cd D:\Uni\YEAR_3\SEM_2\Mobile\Instabond\ai-service
.\.venv\Scripts\Activate.ps1
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Nếu muốn tự reload khi sửa code:

```powershell
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

Swagger của `ai-service`:

- [AI Service Swagger](http://127.0.0.1:8000/docs)

### Florence-2 có cần token không?

- Mặc định không cần token nếu dùng local model `microsoft/Florence-2-base`
- Cần internet ở lần đầu để tải model
- Nếu dùng Florence-2 qua API/server riêng thì mới cần:

```powershell
$env:FLORENCE_API_URL="http://your-florence-server/endpoint"
$env:FLORENCE_API_TOKEN="your-token"
```

## 3. Chạy `backend`

Mở PowerShell mới:

```powershell
cd D:\Uni\YEAR_3\SEM_2\Mobile\Instabond\backend
$env:MONGODB_URI="mongodb://localhost:27017/instabond"
$env:CLOUDINARY_CLOUD_NAME="your_cloud_name"
$env:CLOUDINARY_API_KEY="your_api_key"
$env:CLOUDINARY_API_SECRET="your_api_secret"
$env:JWT_SECRET_KEY="your_jwt_secret"
.\mvnw.cmd spring-boot:run
```

Swagger của backend:

- [Backend Swagger](http://127.0.0.1:8080/swagger-ui/index.html)

Ghi chú:

- Backend mặc định gọi `ai-service` ở `http://localhost:8000`
- Vì vậy hãy chắc chắn `ai-service` đã chạy trước
- Redis không bắt buộc trong flow hiện tại vì `PRESENCE_REDIS_ENABLED` mặc định là `false`

## 4. Chạy Android app

Mở Android Studio tại:

```text
D:\Uni\YEAR_3\SEM_2\Mobile\Instabond\android
```

Sau đó:

1. Bật emulator
2. Nhấn `Run app`

Hoặc chạy bằng command line:

```powershell
cd D:\Uni\YEAR_3\SEM_2\Mobile\Instabond\android
.\gradlew.bat installDebug
```

## Kết nối FE -> BE

Android app hiện đang trỏ tới:

```text
http://10.0.2.2:8080/
```

Điều này có nghĩa là:

- Nếu chạy bằng Android Emulator thì không cần sửa gì
- `10.0.2.2` là cách emulator gọi ngược về `localhost` của máy host

## Quy trình test đề xuất

1. Mở MongoDB
2. Mở `ai-service`
3. Mở backend
4. Test `POST /api/ai/suggest-music` ở Swagger `ai-service`
5. Test `POST /api/posts/suggestions` ở Swagger backend
6. Mở Android app và test trên emulator

## Khi nào cần cài lại package?

Chỉ cần chạy lại `pip install ...` trong `ai-service` khi:

- `requirements.txt` thay đổi
- bạn xóa `.venv`
- môi trường bị lỗi package

Ngoài các trường hợp đó, những lần sau chỉ cần:

```powershell
cd D:\Uni\YEAR_3\SEM_2\Mobile\Instabond\ai-service
.\.venv\Scripts\Activate.ps1
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

## Nếu gặp lỗi thường gặp

### `numpy` bị nâng lên 2.x

Chạy lại:

```powershell
python -m pip install "numpy<2"
```

### Backend không lên vì thiếu biến môi trường

Kiểm tra lại các biến:

- `MONGODB_URI`
- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`
- `JWT_SECRET_KEY`

### App Android không gọi được backend

Kiểm tra:

- backend có đang chạy ở port `8080` không
- emulator có đang dùng không
- `ai-service` có đang chạy ở port `8000` không
