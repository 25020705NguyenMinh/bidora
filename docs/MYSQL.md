# Kết nối Bidora với MySQL hoặc TiDB

Nhóm dùng JavaFX → Render → TiDB. Người mở `AppLauncher` để dùng hệ thống chung chỉ cần kết nối Internet. Các bước MySQL local dưới đây dành cho người phát triển backend.

## Hiểu ba phần trước

- **JavaFX client**: cửa sổ app bạn bấm để đăng ký, đăng bán, đặt giá.
- **Spring Boot server**: nhận yêu cầu, kiểm tra quyền và cập nhật dữ liệu.
- **MySQL Server**: chương trình giữ dữ liệu lâu dài. **MySQL Workbench** chỉ là công cụ để xem/chạy SQL; cài Workbench riêng chưa có MySQL Server.

Client → API server ở cổng **8080** → MySQL ở cổng **3306** (mặc định). Client không cần biết mật khẩu MySQL.

## MySQL local trên Windows

### 1. Cài server

Tải **MySQL Community Server 8.4 LTS, Windows x86 64-bit MSI** tại <https://dev.mysql.com/downloads/mysql/8.4.html>.
Cài xong chạy **MySQL Configurator** đi kèm:

- Chọn cấu hình Development, TCP/IP, cổng `3306`.
- Đặt và ghi nhớ mật khẩu `root` — đây là tài khoản quản trị database.
- Cho chạy dưới dạng Windows Service và khởi động cùng Windows.
- Apply Configuration và hoàn tất. Không cần mở cổng router để chạy local.

Có thể cài thêm Workbench từ <https://dev.mysql.com/downloads/workbench/>. Nếu đã có MySQL hoạt động thì bỏ qua bước cài.

### 2. Tạo database và user cho app

Mở Workbench, tạo kết nối đến `127.0.0.1`, cổng `3306`, user `root`, nhập mật khẩu vừa đặt. Mở SQL tab, thay `TU_DAT_MAT_KHAU_MANH` rồi chạy:

```sql
CREATE DATABASE IF NOT EXISTS auction CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'auction_app'@'localhost' IDENTIFIED BY 'TU_DAT_MAT_KHAU_MANH';
GRANT ALL PRIVILEGES ON auction.* TO 'auction_app'@'localhost';
```

User app chỉ được thao tác trong database `auction`. Nếu đã tạo `auction_app` từ trước, `CREATE USER IF NOT EXISTS` **không đổi mật khẩu**; muốn đổi hãy dùng `ALTER USER 'auction_app'@'localhost' IDENTIFIED BY 'MAT_KHAU_MOI';`.

### 3. Điền cấu hình app

Tại thư mục gốc repo, chạy PowerShell:

```powershell
Copy-Item config/application-local.example.properties config/application-local.properties
notepad config/application-local.properties
```

Điền đúng database local và tài khoản đã tạo:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/auction?serverTimezone=UTC
spring.datasource.username=auction_app
spring.datasource.password=MAT_KHAU_BAN_VUA_DAT
```

Dùng mật khẩu của **auction_app**, không phải tài khoản đăng nhập app. File này được git bỏ qua. Chạy server tại **thư mục gốc repo** để file được tự đọc.

Cách cấu hình đầy đủ cho database, JWT và SMTP nằm ở mục 7 trong [README](../README.md). Đặt `DB_USERNAME` khi dùng biến môi trường. Script `start-server.ps1` đọc file `.env` cạnh script.

### 4. Khởi động, rồi kiểm tra

Chạy backend bằng script trong README. Ở terminal client, đặt `$env:AUCTION_SERVER_URL = 'http://127.0.0.1:8080'` trước khi mở ứng dụng. Hibernate tạo các bảng trong database đã chọn. Test mặc định dùng H2 trong bộ nhớ.

```sql
USE auction;
SHOW TABLES;
SELECT id, full_name, role, status FROM users;
```

Database mới có thể trống. Đăng ký và lấy OTP gửi qua SMTP để xác minh tài khoản.

## Cả nhóm chạy với nhau


```powershell
$env:JAVA_TOOL_OPTIONS = '-Dauction.server.url=http://192.168.1.10:8080'
mvn -pl client javafx:run
```

Thay IP thật; Windows Firewall trên máy server cần cho phép Java/cổng 8080 trong mạng riêng. MySQL vẫn nằm trên máy server, không cần mở cổng 3306 cho các máy client.

## Database chung trên TiDB Cloud

Người vận hành lấy JDBC URL, tên database và tài khoản kết nối trong cụm TiDB của nhóm. Điền `DB_URL`, `DB_USERNAME` và `DB_PASSWORD` ở mục Environment của web service Render. URL có dạng `jdbc:mysql://HOST:4000/DATABASE?sslMode=VERIFY_IDENTITY`. Dockerfile ở gốc repo đóng gói module server và các phụ thuộc Maven. Các client cùng kết nối tới địa chỉ Render để nhận cập nhật realtime từ cùng backend.

## Lỗi thường gặp

| Hiện tượng | Cần kiểm tra |
| --- | --- |
| `mvn` không nhận diện | Cài Maven, thêm `bin` vào PATH, mở lại PowerShell/VS Code. |
| `JAVA_HOME` sai hoặc `release 21 not supported` | Trỏ JAVA_HOME tới **JDK** 21+, không phải JRE; kiểm tra `mvn -v`. |
| `Communications link failure` / connection refused | MySQL Service đã chạy chưa? Host/port đúng chưa? |
| `Access denied for user` | Username/password MySQL, user host và quyền trong `auction`. |
| `Unknown database 'auction'` | Chạy câu `CREATE DATABASE` ở bước 2. |
| Cổng 8080 đã được dùng | Tắt server cũ hoặc đặt `$env:PORT='8082'` và đổi URL client tương ứng. |
| Test thành công nhưng server không chạy | Test dùng H2; cần hoàn tất cấu hình MySQL thật. |
| Không có email OTP | Kiểm tra `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD` và log gửi mail của backend. |

Nguồn: [MySQL Windows installation](https://dev.mysql.com/doc/refman/8.4/en/windows-installation.html), [MySQL Configurator](https://dev.mysql.com/doc/refman/8.4/en/mysql-configurator.html).
