# TO-DO theo từng member

> Tổng hợp các công việc **chưa hoàn thành** dựa trên `Report_W05_Group05.md`, phân chia theo đúng mạch công việc mỗi người đã làm ở tuần trước.

## 1. Tuấn Anh
**Phụ trách chính:** Android UI + tích hợp app với backend

### Việc cần làm
- Nối các màn `Sign In`, `Sign Up`, `Profile`, `Newsfeed` với API backend thật.
- Hoàn thiện hiển thị dữ liệu thật ở `Newsfeed` và `Profile`.
- Làm grid ảnh ở màn `Profile` bằng `RecyclerView` và `Glide`.
- Làm màn hình `Create Post`, gồm chọn ảnh / chụp ảnh và gửi bài viết.
- Bổ sung `pull-to-refresh` và `infinite scroll` cho newsfeed.

### Kết quả mong đợi
- App Android chạy được các luồng chính với dữ liệu thật.
- Giao diện profile, feed và tạo bài viết hoạt động ổn định.

---

## 2. Luân
**Phụ trách chính:** Upload file + media flow backend

### Việc cần làm
- Hoàn thiện luồng upload avatar và ảnh bài viết từ Mobile → Backend → Cloudinary → MongoDB.
- Chuẩn hóa format response trả về sau khi upload để Android dễ dùng.
- Kiểm tra và xử lý validate file: loại file, dung lượng, lỗi upload.
- Hỗ trợ lưu metadata ảnh vào đúng cấu trúc dữ liệu đã thiết kế.

### Kết quả mong đợi
- Upload ảnh hoạt động end-to-end.
- Ảnh avatar và ảnh bài viết được lưu, truy xuất ổn định.

---

## 3. Nam
**Phụ trách chính:** Backend API cho bài viết, hồ sơ và tương tác xã hội

### Việc cần làm
- Hoàn thiện API `like` và `comment` cho bài viết.
- Cập nhật thống kê tương tác trong bài viết sau khi like/comment.
- Hoàn thiện logic `follow` / `bạn thân` dựa trên collection `relationships`.
- Rà soát lại API `feed` và `profile` để bổ sung field còn thiếu nếu phía Android cần.
- Cập nhật Swagger cho các API mới hoặc API đã chỉnh sửa.

### Kết quả mong đợi
- Backend có đủ API cho tương tác xã hội cơ bản.
- Android có thể dùng ổn định các API feed, profile, like, comment, follow.

---

## 4. Khoa
**Phụ trách chính:** Chat + thông báo real-time

### Việc cần làm
- Cấu hình `Spring WebSocket` ở backend để xử lý chat và thông báo thời gian thực.
- Tích hợp thư viện `Socket.IO Client` trên Android để kết nối realtime với backend.
- Hoàn thiện chức năng chat văn bản giữa các người dùng.
- Hỗ trợ gửi ảnh trong đoạn chat và đồng bộ hiển thị theo thời gian thực.
- Xây dựng luồng thông báo real-time cho tin nhắn mới và các sự kiện liên quan.

### Kết quả mong đợi
- Chức năng chat văn bản và gửi ảnh hoạt động ổn định theo thời gian thực.
- Android nhận được thông báo real-time từ backend đúng và kịp thời.

---

## Ưu tiên thực hiện
1. Hoàn thiện upload ảnh end-to-end.
2. Nối Android với API thật cho auth, profile, newsfeed.
3. Làm xong `like`, `comment`, `follow`.
4. Triển khai realtime chat và thông báo.
