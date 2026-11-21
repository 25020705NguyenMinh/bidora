# Ghi chu deploy Bidora

Ban UI trong `feature/p3.uiux.playground` can backend cung phien ban de dung anh san pham va ho so rieng. Chuyen mau, hover, ve icon va animation cua so nam o client JavaFX.

## Backend can cap nhat

- Deploy module `server` va `shared` tu cung mot commit da duoc nhom thong nhat.
- TiDB can cot `items.image` kieu LONGTEXT de luu anh san pham. Anh duoc client chuyen thanh JPEG toi da 1280 px va server kiem tra truoc khi luu.
- Bang `trading_profiles` luu ten, gioi thieu va avatar PNG cua ho so mua/ban. Nguoi dung co the chon anh lon: client tu thu nho de canh dai nhat toi da 256 px, giu ti le va nen trong suot. Anh nho hon giu nguyen kich thuoc; file nguon toi da 12 MB. Cau hinh Hibernate hien tai la `ddl-auto=update`, nen can kiem tra quyen tao bang/them cot khi server khoi dong.
- `GET/PUT /api/me/trading-profiles` phai hoat dong sau dang nhap. Client dung API nay de upload avatar, doi ten/anh giua ho so mua va ban. Chuyen ho so khong doi account ID, email, mat khau, so du hay quyen ADMIN.
- API san pham va phien dau gia can tra truong `image`. San pham cu chua co anh van hien duoc.
- Server phai cho phep khach goi GET danh sach phien, chi tiet phien va lich su gia. Truoc day Render tra 401 cho `/api/auctions`, nen can deploy SecurityConfig moi de khach xem phien khi chua dang nhap.
- WebSocket `/ws/auctions` va `/ws/user` phai chap nhan token dang nhap. Thong bao moi va chat duoc day qua `/ws/user`.

## Cau hinh Render

- Doi chieu `server/src/main/resources/application.properties` voi ban nhom vua sua tren `dev` truoc khi deploy. File local hien tai van dung DB_USERNAME, DB_PASSWORD, JWT_SECRET, MAIL_USER va MAIL_PASSWORD. Tranh ghi de nham cau hinh Render trong luc xu ly conflict.
- Giu `server.port=${PORT:8080}`, ket noi TiDB dung TLS va timezone UTC.
- Neu bat profile `google`, cung cap GOOGLE_CLIENT_ID va GOOGLE_CLIENT_SECRET theo `application-google.properties`. SMTP can tai khoan va mat khau hop le de gui OTP.
- Neu Render deploy theo GitHub, chon dung branch/commit da duoc nhom thong nhat roi Manual Deploy. Code local chua duoc push se chua xuat hien tren Render.

## Kiem tra sau deploy

1. `/api/health` tra 200. `/api/auctions` tra 200 khi chua dang nhap.
2. Dang nhap hai tai khoan that tren hai cua so client cung URL backend.
3. Upload anh san pham va avatar. Dong/mo lai client de kiem tra anh da duoc luu tren server.
4. Bat tach ho so, luu ten/anh rieng, roi dung menu tai khoan doi ho so. Ten/anh goc phai phai doi, so du va account ID van chung.
5. Dat gia hop le o cua so thu nhat. Gia va lich su cua so thu hai phai cap nhat; tai khoan theo doi co thong bao moi.
6. Gui chat giua hai tai khoan. Tin nhan phai den cua so con lai.

GitHub Actions chay build, JUnit va Checkstyle. Ket qua CI khong tu xac nhan SMTP, cau hinh OAuth, quyen sua schema TiDB hay WebSocket cua service Render dang deploy.
