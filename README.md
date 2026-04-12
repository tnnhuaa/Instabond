# Instabond

Instabond is a mobile social media application built with a native Android client, a Spring Boot backend, and a FastAPI AI microservice. The project focuses on content sharing, social interaction, and AI-assisted posting on top of a clean multi-service architecture that is easy to explain, extend, and demo.

## Quick Overview

- Core platform: Android app + REST API + WebSocket + AI microservice
- Goal: simulate a modern social media ecosystem with intelligent support during content creation
- Deployment style: local development with 3 services running in parallel
- Storage and media: MongoDB + Cloudinary
- Authentication: JWT access token / refresh token

## Project Highlights

- User registration, login, and password recovery with email OTP
- Newsfeed, posts, likes, comments, shares, bookmarks, and personal profiles
- Stories, story viewers, and interaction flows similar to real social apps
- Real-time 1-to-1 chat using STOMP WebSocket, with inbox and online presence
- User/post search, search history, and friend suggestions
- Full social relationship flow: follow, private account, follow request, close friend, block/unblock
- Profile sharing through deep links and QR flow inside the Android app
- AI-powered posting support:
    - Music suggestions from images
    - User tag suggestions from face analysis
    - Batch image embeddings
- Built-in image editing flow before posting stories or posts
- Swagger documentation available for both backend and AI service

## System Architecture

```text
Android App
  |
  |-- HTTP -> Spring Boot Backend (port 8080)
  |             |-- MongoDB
  |             |-- Cloudinary
  |             |-- Gmail SMTP / OTP
  |             |-- STOMP WebSocket (/ws)
  |             '-- REST call -> FastAPI AI Service (port 8000)
  |
  '-- WebSocket -> Spring Boot Backend
```

### 1. Android app

- Language / UI: Java, Android View System, ViewBinding, DataBinding
- Client architecture: Activity + Adapter + Repository + ViewModel
- Main libraries:
    - Retrofit + Gson for REST API communication
    - OkHttp interceptor for attaching JWT tokens
    - Glide for image loading
    - LiveData / ViewModel
    - STOMP Protocol Android + RxJava for real-time chat
    - ZXing for QR / profile sharing flow
- Default backend URL in emulator: `http://10.0.2.2:8080/`

### 2. Backend

- Platform: Spring Boot 4, Java 21
- Main components:
    - Spring Web
    - Spring Security + JWT
    - Spring Data MongoDB
    - Spring WebSocket
    - Spring Validation
    - Spring Mail
    - Springdoc OpenAPI
    - Redis support for presence, optional
- Major business capabilities:
    - Auth, refresh token, forgot/reset password
    - Post management, feed, interactions, and bookmarks
    - Stories and story viewers
    - Chat, conversations, message REST API + WebSocket
    - Notifications
    - Search and search history
    - User profile, social graph, friend suggestions, block list, close friends
    - Profile resolution via payload / QR / deep link

### 3. AI service

- Platform: FastAPI, Uvicorn, Pydantic
- Main routes:
    - `POST /api/ai/suggest-music`
    - `POST /api/ai/suggest-tags`
    - `POST /api/ai/embeddings`
- AI libraries currently used: `transformers`, `torch`, `timm`, `deepface`, `opencv-python`
- Useful for showcasing intelligent features without overloading the main backend

## Main Features

### User and security

- Registration, login, and token refresh
- Forgot password and password reset with email OTP
- Profile updates and tagging permission settings

### Social features

- Create posts with multiple media items
- Like, unlike, comment, share, and bookmark
- Newsfeed and profile pages
- Create and view stories
- Follower / following management
- Private accounts, follow requests, and close friends
- Blocking users and managing blocked users

### Search and connection

- Search users and posts
- Store and manage search history
- Friend suggestions based on friends-of-friends logic
- Profile sharing through QR and deep links

### Chat and notifications

- Inbox, conversations, and 1-to-1 chat
- Send / receive messages through WebSocket
- Online status through presence handling
- Notifications for key system actions

### AI-enhanced posting

- Analyze images to suggest matching music for a post
- Suggest user tags from detected faces
- Generate embeddings for future extensibility
- Dedicated AI service that can evolve independently

## Folder Structure

```text
Instabond/
|- android/          # Native Android app
|- backend/          # Spring Boot REST API + WebSocket
|- ai-service/       # FastAPI AI microservice
|- introduction.md   # Local run and test guide
'- README.md
```

## Environment Requirements

- JDK 21
- Android Studio + Android SDK
- Python 3.10+
- Local MongoDB
- Maven Wrapper included in the repo
- PowerShell on Windows
- Internet connection for the first AI model download

## Required Environment Variables

### Backend

| Variable                 | Required | Description                                    |
| ------------------------ | -------- | ---------------------------------------------- |
| `MONGODB_URI`            | Yes      | Example: `mongodb://localhost:27017/instabond` |
| `CLOUDINARY_CLOUD_NAME`  | Yes      | Media upload configuration                     |
| `CLOUDINARY_API_KEY`     | Yes      | Media upload configuration                     |
| `CLOUDINARY_API_SECRET`  | Yes      | Media upload configuration                     |
| `JWT_SECRET_KEY`         | Yes      | JWT signing key                                |
| `MAIL_USERNAME`          | No       | Gmail account used for OTP sending             |
| `MAIL_PASSWORD`          | No       | Gmail app password                             |
| `PRESENCE_REDIS_ENABLED` | No       | Default is `false`                             |
| `REDIS_HOST`             | No       | Default is `localhost`                         |
| `REDIS_PORT`             | No       | Default is `6379`                              |
| `REDIS_USERNAME`         | No       | Redis auth                                     |
| `REDIS_PASSWORD`         | No       | Redis auth                                     |
| `REDIS_SSL_ENABLED`      | No       | Default is `false`                             |

### AI service

- You can create a dedicated `.venv` inside `ai-service/`
- The first run requires package installation and model download
- If you use a local model, no token is required by default
- If you use a custom Florence API/server, you can define:
    - `FLORENCE_API_URL`
    - `FLORENCE_API_TOKEN`

## How to Run Locally

Recommended startup order:

1. MongoDB
2. `ai-service`
3. `backend`
4. Android app

### 1. Run MongoDB

```powershell
mongod --dbpath D:\data\db
```

Or make sure this URI is available:

```text
mongodb://localhost:27017/instabond
```

### 2. Run the AI service

First time setup:

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python -m pip install "numpy<2"
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Next runs:

```powershell
cd ai-service
.\.venv\Scripts\Activate.ps1
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

For auto reload during development:

```powershell
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

### 3. Run the backend

```powershell
cd backend
$env:MONGODB_URI="mongodb://localhost:27017/instabond"
$env:CLOUDINARY_CLOUD_NAME="your_cloud_name"
$env:CLOUDINARY_API_KEY="your_api_key"
$env:CLOUDINARY_API_SECRET="your_api_secret"
$env:JWT_SECRET_KEY="your_jwt_secret"
$env:MAIL_USERNAME="your_email@gmail.com"
$env:MAIL_PASSWORD="your_app_password"
.\mvnw.cmd spring-boot:run
```

Notes:

- The backend calls `ai-service` at `http://localhost:8000` by default
- Redis is not required in the current flow because `PRESENCE_REDIS_ENABLED=false`

### 4. Run the Android app

Open the project in Android Studio from the `android/` folder, then:

1. Start an emulator
2. Run the app

Or use command line:

```powershell
cd android
.\gradlew.bat installDebug
```

## API Docs and Useful Endpoints

- Backend Swagger: [http://127.0.0.1:8080/swagger-ui/index.html](http://127.0.0.1:8080/swagger-ui/index.html)
- AI Service Swagger: [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)
- WebSocket endpoint: `ws://<host>:8080/ws`
- The Android emulator reaches the host machine through `10.0.2.2`
