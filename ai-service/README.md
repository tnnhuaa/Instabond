# 🚀 AI Service - Hướng dẫn cài đặt và Chạy

## 📋 Yêu cầu hệ thống
- Python 3.10+
- Đã cài đặt pip

## ⚙️ Cấu hình môi trường (file `.env`)
```env
# MongoDB Configuration
MONGO_URL=mongodb://localhost:27017
MONGO_DB_NAME=your_database_name

# Florence-2 Model Settings
FLORENCE_TASK_PROMPT=<CAPTION>
FLORENCE_MAX_NEW_TOKENS=48
FLORENCE_NUM_BEAMS=1
```

## 🛠 Cài đặt (Setup)

### Bước 1: Tạo môi trường ảo (Virtual Environment)

Việc này giúp các thư viện của dự án không bị xung đột với các dự án khác trên máy.

```powershell
python -m venv venv
```

### Bước 2: Kích hoạt môi trường ảo

- Windows: `./venv/Scripts/activate`
- macOS/Linux: `source venv/bin/activate`

### Bước 3: Cài đặt các thư viện cần thiết

```powershell
pip install -r requirements.txt
```

## 🏃 Chạy ứng dụng (Execution)

Để chạy server ở chế độ phát triển (Development), sử dụng lệnh:

```powershell
uvicorn app.main:app --reload
```

Server sẽ chạy tại: http://127.0.0.1:8000

Tài liệu API (Swagger UI): http://127.0.0.1:8000/docs

## 📁 Cấu trúc thư mục sơ lược

- app/: Thư mục chứa code chính.
- venv/: Môi trường ảo (đã được ignore trong .gitignore).
- requirements.txt: Danh sách các thư viện cần dùng.
