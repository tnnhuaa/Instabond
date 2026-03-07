# Báo cáo sử dụng AI – Newsfeed UI (Android)

---

## (1) Prompt (06/03/2026)

> Tao đang làm app Android cho InstaBond và cần dựng màn hình **Newsfeed** theo mockup/Figma.
>
> Hãy giúp tao tạo giao diện trang chủ theo style dark mode giống Instagram, gồm:
> - `activity_main.xml` có **Toolbar** phía trên, **RecyclerView feed** ở giữa và **BottomNavigationView** 5 tab ở dưới
> - `item_post.xml` làm layout mẫu cho từng bài post: avatar có ring, username, ảnh bài viết, nút like/comment/bookmark, caption và nút xem bình luận
> - `bottom_nav_menu.xml` cho các tab Home, Search, Create, Notifications, Profile
> - các resource cần thiết như icon vector, màu sắc, theme, strings
> - cập nhật `MainActivity.java` để dùng layout mới, set toolbar và xử lý chọn item cho bottom nav bằng `ViewBinding`
>
> Nếu cần thì tách thêm color token, drawable shape và theme để giao diện dễ maintain hơn.

---

## (2) Kết quả AI trả về

AI tạo bộ khung UI cho feature newsfeed trên nhánh `feat/newsfeed`, tập trung ở module Android:

- **`MainActivity.java`**: thay màn hình test API trước đó bằng màn hình newsfeed dùng `ActivityMainBinding`, gọi `setSupportActionBar(binding.toolbar)` và gắn `setOnItemSelectedListener()` cho `bottomNav`.

- **`activity_main.xml`**: dựng layout màn hình chính bằng `ConstraintLayout`, gồm:
  - top app bar với tên app, nút menu, QR và messages
  - `RecyclerView` cho feed (`rv_feed`)
  - `BottomNavigationView` ở cuối màn hình dùng menu riêng

- **`item_post.xml`**: tạo layout mẫu cho một bài post hoàn chỉnh, có:
  - header với avatar + username + overflow button
  - ảnh bài viết và badge music
  - action row với like/comment/bookmark
  - likes count, caption và nút xem bình luận

- **`bottom_nav_menu.xml`**: khai báo 5 tab `Home`, `Search`, `Create`, `Notifications`, `Profile` cho thanh điều hướng dưới.

- **Nhóm resource UI**:
  - thêm các vector drawable như `ic_home`, `ic_search`, `ic_add_circle`, `ic_bell`, `ic_person`, `ic_heart`, `ic_bookmark`, `ic_message_square`, `ic_message_circle`, `ic_more_horizontal`, `ic_music`, `ic_qr_code`, `ic_menu`
  - thêm drawable shape như `badge_dot`, `music_badge_bg`, `avatar_circle_bg`, `avatar_ring_gradient`, `avatar_ring_gradient_blue`
  - thêm `bottom_nav_tint.xml` để xử lý màu active/inactive cho bottom navigation

- **Resource cấu hình giao diện**:
  - cập nhật `colors.xml` theo palette dark mode của thiết kế
  - bổ sung `dimens.xml` để gom các spacing/size token
  - cập nhật `themes.xml`, `values-night/themes.xml` để dùng theme `Theme.InstaBond`
  - cập nhật `strings.xml` cho app name và một số content description
  - đổi `AndroidManifest.xml` sang dùng theme mới

---

## (3) Chỉnh sửa / cải tiến sau khi nhận kết quả từ AI

### `MainActivity.java`

- Loại bỏ hoàn toàn đoạn code test Retrofit/API tạm thời trước đó để `MainActivity` chỉ còn đóng vai trò host cho màn hình newsfeed.
- Chuyển từ `setContentView(R.layout.activity_main)` sang `ActivityMainBinding.inflate(...)` — gọn hơn, an toàn hơn khi thao tác view.
- Thiết lập `Toolbar` bằng `setSupportActionBar(binding.toolbar)` để khớp với app bar custom trong layout.
- Gắn sẵn listener cho `bottomNav` để chuẩn bị cho bước điều hướng tab ở các task tiếp theo.

### `activity_main.xml`

- Thay layout mẫu mặc định kiểu “Hello World” bằng màn hình newsfeed hoàn chỉnh.
- Tách bố cục thành 3 vùng rõ ràng: app bar trên, feed ở giữa, bottom navigation ở dưới.
- Dùng `RecyclerView` với `LinearLayoutManager` và `tools:listitem="@layout/item_post"` để preview item ngay trong editor.
- Áp dụng dark background, divider và hệ icon action đúng với mockup thay vì giao diện mặc định của template Android.

### `item_post.xml`

- Tạo component post item riêng để có thể tái sử dụng cho adapter sau này, thay vì nhét cứng dữ liệu vào `activity_main.xml`.
- Chia item thành các phần semantic: header, image, action row, likes, caption, comments.
- Bổ sung các chi tiết UI bám thiết kế như avatar ring gradient, music badge overlay, overflow button và action buttons.
- Dùng `tools:text`, `tools:src` để mock dữ liệu preview trong Android Studio mà không ảnh hưởng runtime.

### `bottom_nav_menu.xml` và drawable resources

- Tách menu điều hướng dưới sang file riêng để dễ mở rộng logic tab về sau.
- Tạo riêng bộ icon vector cho newsfeed thay vì dùng icon mặc định, giúp giao diện đồng bộ hơn với mockup.
- Thêm `bottom_nav_tint.xml` để xử lý state màu active/inactive cho icon và label, tránh hard-code trực tiếp trong từng item.

### `colors.xml`, `dimens.xml`, `themes.xml`, `values-night/themes.xml`

- Đổi từ bộ màu mặc định (`black`, `white`) sang một design token set riêng cho màn hình newsfeed như `bg_primary`, `text_primary`, `text_secondary`, `divider`, `overlay_60`.
- Bổ sung token cho gradient avatar ring và màu active/inactive của bottom nav để dễ tái sử dụng ở các màn khác.
- Tạo `dimens.xml` để chuẩn hóa spacing, icon size, text size theo số đo từ mockup/Figma.
- Đổi theme ứng dụng từ `Theme.Instabond_FE` sang `Theme.InstaBond`, đồng thời cấu hình lại `windowBackground`, `statusBarColor`, `navigationBarColor` cho đúng dark mode.
- Đồng bộ cả `values` và `values-night` để giao diện không bị lệch giữa các mode theme.

### `strings.xml` và `AndroidManifest.xml`

- Đổi `app_name` từ `Instabond_FE` sang `InstaBond` để thống nhất branding trên app bar và launcher.
- Bổ sung các string phục vụ content description như menu, QR code, messages, like, comment, bookmark.
- Cập nhật `AndroidManifest.xml` để app dùng `@style/Theme.InstaBond` thay vì theme mặc định cũ.

### Tổng kết phần cải tiến

So với trạng thái trước khi làm feature này, nhánh `feat/newsfeed` đã nâng app Android từ màn hình test API + layout mẫu sang một **bộ khung giao diện newsfeed hoàn chỉnh**, có thể dùng ngay làm nền cho các bước tiếp theo như:
- gắn `RecyclerView.Adapter` với dữ liệu thật từ API feed
- điều hướng giữa các tab bottom navigation
- load ảnh bằng Glide/Coil
- bind caption, likes, comments từ model backend

