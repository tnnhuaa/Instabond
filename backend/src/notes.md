## Prompt:
*"Hãy khảo sát thư mục và làm API Posts và API Profile. Làm các API về bài viết và API Profile theo đúng yêu cầu, tham khảo cấu trúc hiện có trong dự án."*

## AI đã thực hiện:
* **Khảo sát cấu trúc**: Đọc và phân tích các file entity, controller, service, repository và DTO hiện có tại `src/main/java/com/instabond/`.
* **Khởi tạo Code Skeleton**: Sinh ra code ban đầu cho Post API và Profile API, đồng thời tạo mới các file liên quan như `CreatePostRequest`, `PostResponse`, `PostController`, `PostService`, v.v.

## Sinh viên đã thực hiện:
* **Kiểm tra và đối chiếu**: Rà soát toàn bộ code được sinh ra so với yêu cầu dự án, xác định toàn bộ yêu cầu endpoint API và kỳ vọng mã HTTP.
* **Chỉ định thiết kế**: Yêu cầu định dạng request/response, thiết lập ngôn ngữ chú thích Swagger là tiếng Anh và quyết định quy ước đặt DTO nhất quán với pattern của phần Auth.

---

## Prompt:
*"Các chú thích tài liệu Swagger phải được chuẩn hóa, thống nhất về cách trình bày. Các lớp Request và Response (DTO) cần tách riêng ra ngoài, tuân thủ cấu trúc và quy ước đặt tên."*

## AI đã thực hiện:
* **Chuẩn hóa Swagger/OpenAPI**: Bổ sung và đồng bộ các chú thích tài liệu cho toàn bộ controller liên quan.
* **Tái cấu trúc DTO**: Di chuyển và chuẩn hóa tất cả các lớp DTO vào package `dto/` (như `UpdateProfileRequest`, `FollowUserResponse`...).
* **Rà soát API**: Kiểm tra tính đầy đủ và nhất quán của các API Profile và Post.

## Sinh viên đã thực hiện:
* **Xác nhận quy ước**: Chỉ định và áp dụng quy ước đặt tên trường request/response theo chuẩn `snake_case`, đảm bảo thống nhất với tài liệu MongoDB.
* **Đối chiếu đặc tả**: Kiểm thử các endpoint với đặc tả dự án để đảm bảo phản hồi chính xác.

---

## Prompt:
*(Prompt tổng hợp cho Giai đoạn sửa lỗi)* *"Sửa các lỗi phát sinh trong quá trình chạy dự án: ký tự BOM, lỗi không tìm thấy người dùng, lỗi phân tích tên phương thức PostRepository, lỗi Deserialize ObjectId, lỗi 403 khi cập nhật/xóa bài viết, lỗi thiếu bean ObjectMapper và lỗi Content-Type."*

## AI đã thực hiện:
* **Xử lý lỗi biên dịch & Định dạng**: Viết lại file `ProfileResponse.java` để bỏ ký tự BOM (`\ufeff`), khôi phục endpoint Create Post về nhận `@RequestBody` (JSON) thay vì multipart form.
* **Cập nhật logic truy cập Data**: Thay đổi logic phân giải để tra cứu người dùng lần lượt theo ObjectId, email, username; thay thế derived query bằng `@Query` tường minh trong `PostRepository`.
* **Xử lý lỗi Mapping MongoDB**: Bổ sung `@Field("author_id")` để sửa lỗi Deserialize ObjectId; sửa lại logic phân giải ID trong chức năng Update/Delete Post để khắc phục lỗi 403 sai; loại bỏ việc inject `ObjectMapper` thủ công bằng cách sử dụng `MongoTemplate`.

## Sinh viên đã thực hiện:
* **Phân tích và Debug sâu**: Trực tiếp phân tích các vấn đề ánh xạ ObjectId bằng cách truy cập cơ sở dữ liệu và kiểm tra cấu trúc tài liệu thô trên MongoDB.
* **Cập nhật Entity**: Điều chỉnh kiểu dữ liệu `boolean` thành `Boolean` trong `entity/User.java` để tránh các lỗi null pointer hoặc khởi tạo không mong muốn.

---

## Prompt:
*"Bổ sung xử lý lỗi 403, 404 cho API posts/{postId}. Tách riêng các chức năng tìm bài viết theo email, id, username. Khi trả về lỗi 403 cần định nghĩa rõ trong tài liệu API."*

## AI đã thực hiện:
* **Cập nhật tài liệu lỗi**: Bổ sung mô tả chi tiết cho các trường hợp văng lỗi 403 và 404 trực tiếp vào tài liệu Swagger.
* **Tách Endpoint**: Tách chức năng tìm bài viết theo user ID, email và username thành các endpoint riêng biệt, giúp code mạch lạc hơn.
* **Chuẩn hóa Response Lỗi**: Đảm bảo các lỗi trả về đều thông qua một đối tượng có cấu trúc rõ ràng.

## Sinh viên đã thực hiện:
* **Đề xuất luồng xử lý**: Chủ động yêu cầu tách endpoint để dễ quản lý.
* **Kiểm thử toàn diện**: Sử dụng Swagger UI và Postman để test từng trường hợp (kể cả test case lỗi) nhằm đảm bảo hệ thống phản hồi đúng yêu cầu.

---

## Prompt:
*"Hãy tạo user có following và follower ra file để import vào database. User mới không trùng với các user hiện có."*

## AI đã thực hiện:
* **Sinh dữ liệu User**: Tạo file `new_users_seed.json` gồm 4 người dùng mới, đảm bảo dữ liệu không trùng lặp.
* **Sinh dữ liệu Relationship**: Tạo file `relationships_seed.json` định nghĩa các mối quan hệ follow chéo giữa các người dùng để phục vụ test tính năng.

## Sinh viên đã thực hiện:
* **Import và Quản lý dữ liệu**: Thực hiện import dữ liệu mẫu lên database, đối chiếu và xác nhận dữ liệu mới không gây xung đột với các bản ghi hiện có.
* **Kiểm duyệt cuối cùng**: Đảm bảo 100% code và dữ liệu do AI sinh ra trong toàn bộ dự án đều đã được đích thân xem xét, kiểm thử và duyệt trước khi đưa vào nhánh chính.