# HƯỚNG DẪN VẬN HÀNH & DANH SÁCH TÀI KHOẢN HỆ THỐNG PMS
*(Property Management System - Hệ thống Quản trị & Cho thuê Phòng trọ Đa vai trò)*

---

## 1. DANH SÁCH TÀI KHOẢN ĐĂNG NHẬP MẶC ĐỊNH (DEFAULT ACCOUNTS)

Tất cả các tài khoản mặc định dưới đây được khởi tạo tự động trong hệ thống ([UserInitializer.java](file:///d:/.idea/it63minhduc/src/main/java/com/pms/propertymanagement/config/init/UserInitializer.java)).  
**Mật khẩu dùng chung cho tất cả tài khoản:** `123`

| Vai trò (Role) | Tên đăng nhập (Username) | Mật khẩu | Họ và tên | Số điện thoại | Cổng đăng nhập phù hợp | Quyền hạn & Chức năng chính |
| :--- | :--- | :---: | :--- | :---: | :--- | :--- |
| **Quản trị viên (ADMIN)** | `admin` | **`123`** | System Admin | - | `/login/owner` hoặc `/login` | Quản trị toàn hệ thống, quản lý người dùng, xem thống kê doanh thu toàn sàn, quản lý danh mục và gói dịch vụ. |
| **Chủ trọ chính (OWNER)** | `owner1` | **`123`** | Chủ trọ 1 | 0975893248 | `/login/owner` | **(Có sẵn dữ liệu mẫu đầy đủ)** Quản lý tòa nhà, phòng trọ, hợp đồng, ghi chỉ số điện nước, lập hóa đơn, ví tiền, quản lý sự cố bảo trì. |
| **Chủ trọ 2 (OWNER)** | `owner2` | **`123`** | Chủ trọ 2 | 0909090909 | `/login/owner` | Quản lý bất động sản và các phòng trọ của Chủ trọ 2. |
| **Chủ trọ 3 (OWNER)** | `owner3` | **`123`** | Chủ trọ 3 | 0707070707 | `/login/owner` | Quản lý bất động sản và các phòng trọ của Chủ trọ 3. |
| **Chủ trọ 4 (OWNER)** | `owner4` | **`123`** | Chủ trọ 4 | 0808080808 | `/login/owner` | Quản lý bất động sản và các phòng trọ của Chủ trọ 4. |
| **Khách thuê 1 (TENANT)** | `tenant1` | **`123`** | Nguyễn Văn An | 0901234567 | `/login` | Khách thuê đang ở tại phòng mẫu (có hợp đồng hiệu lực, xem hóa đơn tiền phòng, gửi yêu cầu sửa chữa). |
| **Khách thuê 2 (TENANT)** | `tenant2` | **`123`** | Trần Thị Bích | 0912345678 | `/login` | Khách thuê phòng trọ mẫu 2, tra cứu thanh toán và lịch sử thuê. |
| **Khách thuê 3 (TENANT)** | `tenant3` | **`123`** | Lê Hoàng Nam | 0987654321 | `/login` | Khách thuê phòng trọ mẫu 3. |
| **Kỹ thuật viên (STAFF)** | `staff1` | **`123`** | Staff 1 | 0901111111 | `/login/owner` | Tiếp nhận yêu cầu sửa chữa/bảo trì được chủ nhà phân công, cập nhật tiến độ và chi phí sửa chữa. |
| **Kiểm duyệt viên (MODERATOR)** | `moderator1` | **`123`** | Kiểm duyệt viên 1 | 0900000099 | `/login/owner` | Kiểm duyệt tin đăng cho thuê phòng của các chủ trọ trước khi hiển thị ra trang chủ. |

---

## 2. CỔNG TRUY CẬP & ĐIỀU HƯỚNG TỰ ĐỘNG (ROUTING)

Sau khi nhập đúng Username và Mật khẩu, hệ thống sẽ **tự động phân loại quyền** và điều hướng đến Dashboard tương ứng:

* **Trang chủ tìm kiếm phòng (Khách vãng lai):** [http://localhost:8081/](http://localhost:8081/)
* **Cổng đăng nhập Chủ nhà / Quản trị / Nhân viên / Kiểm duyệt:** [http://localhost:8081/login/owner](http://localhost:8081/login/owner)
* **Cổng đăng nhập Khách thuê (Tenant Portal):** [http://localhost:8081/login](http://localhost:8081/login)
* **Đăng xuất:** [http://localhost:8081/logout](http://localhost:8081/logout)

### URL Dashboard theo từng vai trò:
* **Admin (Quản trị):** `http://localhost:8081/admin/dashboard`
* **Owner (Chủ trọ):** `http://localhost:8081/owner/dashboard`
* **Tenant (Khách thuê):** `http://localhost:8081/tenant/dashboard`
* **Staff (Kỹ thuật viên):** `http://localhost:8081/staff/maintenance`
* **Moderator (Kiểm duyệt viên):** `http://localhost:8081/moderator`

> **💡 Lưu ý quan trọng cho Admin:**
> - Tài khoản `admin` hiện đã được cấp quyền tối cao (Full Roles: `ADMIN`, `OWNER`, `STAFF`, `MODERATOR`, `USER`).
> - Khi đang ở trang **Quản Trị (`/admin/dashboard`)**, bạn có thể bấm vào nút **"🔄 Chuyển Cổng Vai Trò"** trên thanh Menu trên cùng (Topbar) để nhảy nhanh sang giao diện của bất kỳ vai trò nào mà không cần đăng xuất.
> - **Để test dữ liệu thực tế chuyên biệt:** Nên dùng đúng tài khoản của vai trò đó (Mật khẩu: `123`):
>   + Dùng `owner1`: Để thấy 15+ phòng trọ, đồng hồ điện nước và hóa đơn có sẵn.
>   + Dùng `tenant1`: Để thấy phòng đang thuê, hợp đồng và hóa đơn cần đóng tiền.
>   + Dùng `staff1`: Để thấy danh sách sự cố sửa chữa được phân công.
>   + Dùng `moderator1`: Để thấy danh sách bài đăng chờ duyệt.

---

## 3. YÊU CẦU MÔI TRƯỜNG (PREREQUISITES)

1. **Java:** Java JDK 17 trở lên hoặc OpenJDK 21 (Đã kiểm tra tương thích hoàn toàn với OpenJDK 21 trên hệ thống).
2. **Cơ sở dữ liệu:** MySQL Server phiên bản 8.0 trở lên (Cổng mặc định: `3306`).
   * *Có thể bật MySQL qua XAMPP, WampServer, Laragon, MySQL Workbench hoặc Docker container.*
3. **Build Tool:** Đã tích hợp sẵn Maven Wrapper trong thư mục gốc (`mvnw.cmd` / `mvnw`), không bắt buộc phải cài Maven độc lập.

---

## 4. HƯỚNG DẪN CÁCH CHẠY DỰ ÁN (HOW TO RUN)

### Bước 1: Chuẩn bị Cơ sở dữ liệu MySQL
* Khởi động dịch vụ MySQL trên máy tính (đảm bảo cổng `3306` đang hoạt động).
* Không cần tạo bảng thủ công: Hệ thống đã cấu hình tham số `createDatabaseIfNotExist=true` và `ddl-auto=update`, ứng dụng sẽ **tự động tạo database `property_management` và toàn bộ các bảng, quan hệ, khóa ngoại, dữ liệu ban đầu** trong lần đầu chạy.

### Bước 2: Chạy ứng dụng

#### 👉 Cách 1: Chạy trực tiếp bằng Maven Wrapper (Khuyên dùng trong Terminal / PowerShell)
Mở cửa sổ PowerShell hoặc CMD tại thư mục gốc dự án (`d:\.idea\it63minhduc`) và gõ lệnh:
```powershell
.\mvnw.cmd spring-boot:run
```
*(Trên Linux/macOS dùng lệnh: `./mvnw spring-boot:run`)*

#### 👉 Cách 2: Chạy từ IntelliJ IDEA / VS Code
1. Mở dự án trong **IntelliJ IDEA**.
2. Tìm đến file khởi động chính:  
   `src/main/java/com/pms/propertymanagement/PropertyManagementSystemApplication.java`
3. Click chuột phải chọn **Run 'PropertyManagementSystemApplication'** (hoặc bấm tổ hợp phím `Shift + F10`).

#### 👉 Cách 3: Đóng gói thành file JAR và chạy nền
1. Đóng gói file `.jar`:
   ```powershell
   .\mvnw.cmd clean package -DskipTests
   ```
2. Chạy ứng dụng bằng lệnh Java:
   ```powershell
   java -jar target/property-management-0.0.1-SNAPSHOT.jar
   ```

### Bước 3: Mở trình duyệt và trải nghiệm
Khi màn hình Console hiển thị thông báo `Hệ thống đã sẵn sàng` hoặc `Started PropertyManagementSystemApplication`:
* Mở trình duyệt web và truy cập: **[http://localhost:8081](http://localhost:8081)**

---

## 5. CẤU HÌNH BIẾN MÔI TRƯỜNG & TÍCH HỢP BÊN NGOÀI (CONFIGURATION)

File cấu hình chính nằm tại: [src/main/resources/application.properties](file:///d:/.idea/it63minhduc/src/main/resources/application.properties)

| Tham số cấu hình | Giá trị mặc định | Giải thích |
| :--- | :--- | :--- |
| `server.port` | `8081` | Cổng HTTP của ứng dụng (đã đổi sang 8081 để tránh xung đột cổng 8080) |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/property_management...` | Chuỗi kết nối MySQL Local |
| `spring.datasource.username` | `root` | Tài khoản đăng nhập MySQL |
| `spring.datasource.password` | *(Để trống)* | Mật khẩu MySQL (Điền mật khẩu MySQL nếu máy bạn có cài đặt) |
| `vnpay.payUrl` | `https://sandbox.vnpayment.vn/...` | Cổng thanh toán Sandbox VNPay |
| `gemini.api.key` | *Biến môi trường `GEMINI_API_KEY`* | API Key Google Gemini (cho Chatbot AI tìm phòng) |
| `cloudinary.cloud-name` | *Biến môi trường `CLOUDINARY_CLOUD_NAME`* | Lưu trữ hình ảnh phòng trọ và hóa đơn |

---

## 6. XỬ LÝ LỖI THƯỜNG GẶP (TROUBLESHOOTING)

1. **Lỗi `Access denied for user 'root'@'localhost'`:**
   * Mở file [application.properties](file:///d:/.idea/it63minhduc/src/main/resources/application.properties#L10-L11), cập nhật `spring.datasource.password=mật_khẩu_mysql_của_bạn`.
2. **Lỗi cổng bị chiếm dụng (`Port ... was already in use`):**
   * Đổi sang cổng khác bằng cách thêm tham số khi chạy:
     ```powershell
     .\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--server.port=8082"
     ```
3. **Lỗi 403 Forbidden khi truy cập:**
   * Kiểm tra vai trò của tài khoản. Ví dụ tài khoản `tenant1` không thể truy cập các đường dẫn `/owner/**` hay `/admin/**`. Truy cập [http://localhost:8081/logout](http://localhost:8081/logout) để đăng xuất và đăng nhập lại bằng tài khoản thích hợp.
