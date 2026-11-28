
# Bidora — Online Auction System

<p align="center">
  <strong>Advanced Programming Project · Computer Science · UET</strong>
</p>

<p align="center">
  <a href="https://github.com/25020705NguyenMinh/bidora">
    <img src="https://img.shields.io/badge/Repository-Bidora-blue?logo=github" alt="GitHub Repository">
  </a>
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" alt="Java 21">
  <img src="https://img.shields.io/badge/JavaFX-21-blue" alt="JavaFX 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.6-6DB33F?logo=springboot" alt="Spring Boot 3.5.6">
  <img src="https://img.shields.io/badge/Maven-Multi--module-C71A36?logo=apachemaven" alt="Maven Multi-module">
  <img src="https://img.shields.io/badge/Database-TiDB%20Cloud%20%2F%20MySQL-4479A1?logo=mysql" alt="TiDB Cloud / MySQL">
</p>

<p align="center">
  <em>Hệ thống đấu giá trực tuyến — đăng bán, tham gia đấu giá và theo dõi phiên đấu giá theo thời gian thực.</em>
</p>



---

##  Mục lục

1. [Mô tả hệ thống](#1-mô-tả-hệ-thống)
2. [Công nghệ sử dụng](#2-công-nghệ-sử-dụng)
3. [Cấu trúc thư mục](#3-cấu-trúc-thư-mục)
4. [Hướng dẫn chạy hệ thống](#4-hướng-dẫn-chạy-hệ-thống)
5. [Danh sách chức năng đã hoàn thành](#5-danh-sách-chức-năng-đã-hoàn-thành)
6. [Tài liệu và demo](#6-tài-liệu-và-demo)
7. [Thành viên nhóm](#7-thành-viên-nhóm)
8. [Ghi chú](#8-ghi-chú)

---

## 1. Mô tả hệ thống

Bidora là hệ thống đấu giá trực tuyến, cho phép người dùng đăng bán sản phẩm, tham gia đấu giá và theo dõi phiên đấu giá theo thời gian thực.

- Hệ thống hỗ trợ các vai trò chính gồm **Bidder**, **Seller** và **Admin**.
- Bidder và Seller có thể tham gia mua/bán sản phẩm; Admin có thêm quyền quản lý hệ thống.
- Hỗ trợ quản lý người dùng, quản lý sản phẩm đấu giá, tham gia đấu giá và tự động kết thúc phiên theo thời gian quy định.
- Cung cấp các tính năng nâng cao như **Auto-Bidding**, **Anti-sniping** và **Bid History Visualization**.

## 2. Công nghệ sử dụng

- **Ngôn ngữ lập trình:** `Java 21`
- **GUI:** `JavaFX 21, FXML, CSS`
- **Backend:** `Spring Boot 3.5.6`
- **Kiến trúc:** `Client–Server, MVC`
- **Networking:** `REST API, WebSocket`
- **Build tool:** `Maven multi-module`
- **Database:** `TiDB Cloud / MySQL`
- **Testing:** `JUnit 5`
- **Code Quality:** `Checkstyle`
- **CI/CD:** `GitHub Actions`

### Môi trường chạy

- **Hệ điều hành:** `Windows, Linux, macOS`
- **JDK:** `21+`
- **Maven:** `3.8+`

###  Yêu cầu cài đặt

1. Cài đặt `JDK 21` trở lên và `Maven 3.8+`.
2. Đảm bảo có kết nối Internet.
3. Nếu chạy backend local, cấu hình thông tin kết nối cơ sở dữ liệu.
4. Khởi động server trước, sau đó mới chạy client.

## 3. Cấu trúc thư mục

```text
bidora/
├── shared/                 # Domain Models, Enums, DTO dùng chung
├── server/                 # Spring Boot Server, REST API, WebSocket và nghiệp vụ
│   └── src/main/java/vn/edu/auction/server/
│       ├── config/         # Cấu hình Security, WebSocket, Clock
│       ├── controller/     # REST Endpoints
│       ├── entity/         # JPA Entities
│       ├── event/          # WebSocket và Realtime Events
│       ├── exception/      # Xử lý lỗi tập trung
│       ├── repository/     # Truy vấn Database
│       ├── security/       # JWT Authentication
│       └── service/        # Nghiệp vụ đấu giá, Scheduler, OTP, Mail
├── client/                 # Ứng dụng Desktop JavaFX
│   └── src/main/
│       ├── java/vn/edu/auction/client/
│       └── resources/      # FXML, CSS, Fonts, Icons, I18n
├── docs/                   # Tài liệu kỹ thuật và triển khai
├── .github/workflows/      # CI/CD với GitHub Actions
├── pom.xml                 # Maven Parent POM
└── README.md
```

### Các module chính

- `shared`: Chứa domain model, enum và DTO dùng chung giữa client và server.
- `server`: Xử lý nghiệp vụ đấu giá, xác thực, database, REST API và WebSocket.
- `client`: Cung cấp giao diện JavaFX và giao tiếp với server.
- `Controller`: Tiếp nhận yêu cầu từ giao diện và điều phối xử lý.
- `Service`: Xử lý nghiệp vụ của hệ thống.
- `Repository`: Thực hiện truy vấn và thao tác với cơ sở dữ liệu.

## 4. Hướng dẫn chạy hệ thống

### Cách 1: Sử dụng backend đã triển khai

Mở terminal tại thư mục gốc dự án và chạy:

```bash
mvn "-Dmaven.repo.local=../.m2/repository" -pl shared -am install
mvn "-Dmaven.repo.local=../.m2/repository" -pl client javafx:run
```

### Cách 2: Chạy backend trên máy local

**Bước 1: Cấu hình cơ sở dữ liệu**

Cấu hình thông tin kết nối database và các biến môi trường cần thiết theo cấu hình của server.

**Bước 2: Build và khởi động server**

```bash
mvn "-Dmaven.repo.local=../.m2/repository" -pl server -am -DskipTests package
java -jar server/target/server-1.0-SNAPSHOT.jar
```

**Bước 3: Khởi động client**

Mở terminal mới, cấu hình client trỏ đến server local rồi chạy:

```powershell
$env:AUCTION_SERVER_URL = "http://127.0.0.1:8080"
mvn "-Dmaven.repo.local=../.m2/repository" -pl shared -am install
mvn "-Dmaven.repo.local=../.m2/repository" -pl client javafx:run
```

> **Lưu ý:** Server phải được khởi động trước client. Khi chạy backend local, cần cấu hình database phù hợp.

## 5. Danh sách chức năng đã hoàn thành

Chức năng bắt buộc
- [x] Đăng ký và đăng nhập tài khoản.

- [x] Quản lý người dùng và phân quyền theo vai trò Bidder / Seller / Admin.

- [x] Thêm, sửa, tải ảnh và duyệt sản phẩm đấu giá.

- [x] Hiển thị giá khởi điểm, giá hiện tại, đếm ngược thời gian bắt đầu và kết thúc.

- [x] Hỗ trợ nhiều người tham gia đấu giá đồng thời (Concurrent Bidding với khóa giao dịch).

- [x] Cập nhật kết quả đấu giá theo thời gian thực qua WebSocket (/ws/auctions).

- [x] Tự động kết thúc phiên đấu giá và xác định người thắng cuộc theo thời gian.

- [x] Xử lý lỗi và ngoại lệ tập trung trên cả Server và Client.

- [x] Giao diện người dùng Desktop GUI (JavaFX).

- [x] Thiết kế hướng đối tượng (OOP) và áp dụng Design Patterns (Factory, Singleton, Observer/Event-driven, MVC).

- [x] Kiến trúc Client–Server đa module (shared, server, client).

- [x] Kiểm thử tự động với Unit/Integration Test (JUnit 5) và CI/CD (GitHub Actions).

Chức năng nâng cao
- [x] Auto-Bidding: Tự động đặt giá cạnh tranh theo mức giá trần người dùng thiết lập.

- [x] Anti-Sniping: Tự động gia hạn thêm 60 giây nếu có lượt đặt giá hợp lệ trong 10 giây cuối.

- [x] Bid History Visualization: Biểu đồ trực quan theo dõi lịch sử biến động giá theo thời gian.

- [x] Scheduled Auction: Lên lịch mở phiên đấu giá tự động khi đến thời điểm bắt đầu.

Chức năng mở rộng & Tiện ích
- [x] Ví điện tử nội bộ: Hỗ trợ nạp tiền, rút tiền, kiểm tra số dư và tra cứu lịch sử giao dịch ví.

- [x] Xác thực đa lớp: Xác minh email bằng mã OTP 6 số, khôi phục/đổi mật khẩu và đăng nhập nhanh qua Google OAuth2.

- [x] Tách hồ sơ Mua & Bán (Trading Profiles): Cho phép thiết lập tên, ảnh đại diện và thông tin công khai riêng cho vai trò người mua và người bán trên cùng một tài khoản.

- [x] Tương tác xã hội & Chat Realtime: Theo dõi phiên đấu giá (Watchlist), theo dõi người bán (Follow), nhận thông báo tức thì và nhắn tin P2P trực tiếp qua /ws/user.

- [x] Trải nghiệm người dùng (UX/UI): Hỗ trợ giao diện Sáng/Tối (Light/Dark mode), đa ngôn ngữ Việt – Anh (I18n) và chế độ xem thử offline (--preview).

## 6. Tài liệu và demo

- **Báo cáo PDF:**
- **Video demo:**

## 7. Thành viên nhóm

| Thành viên | MSSV |  Giai đoạn 1 |	Giai đoạn 2	| Giai đoạn 3 |
|---|---|---|---|---|
| Lê Anh Tùng | 25020815 | Frontend đăng nhập |	Backend xác thực| Frontend kết nối xác thực |
| Trịnh Đức Minh | 25020712 | Frontend danh sách sản phẩm, phiên đấu giá | Backend sản phẩm, phiên đấu giá, đặt giá | Frontend kết nối API, realtime|
| Nguyễn Trọng Minh | 25020705 | Backend khởi tạo server, database | Frontend chi tiết đấu giá, lịch sử, kết quả| Backend tự đóng phiên, WebSocket|
| Ngô Hùng Anh | 25020452|	Backend mô hình dùng chung | Frontend trang chủ, quản lý sản phẩm | Backend đấu giá đồng thời, kiểm thử|


## 8. Ghi chú

- Đây là bài tập lớn môn **Lập trình nâng cao**.
- Hệ thống được thiết kế theo mô hình **Client–Server** và **MVC**.
