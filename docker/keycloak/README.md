# Keycloak Custom Image & Ecommerce Theme

Custom Keycloak 26.4.5 deployment for the E-Commerce Platform.

---

## 1. Thành phần tích hợp
- **Base image**: `quay.io/keycloak/keycloak:26.4.5`
- **SPI Provider**: `keycloak-profile-sync` (tự động provision hồ sơ người dùng sang `user-service`)
- **Custom Theme**: `ecommerce` (đặt tại `/opt/keycloak/themes/ecommerce/`)
  - **Login Theme**: Màn hình đăng nhập, đăng ký, quên mật khẩu, cập nhật mật khẩu mới, xác minh email.
  - **Account Theme**: Giao diện quản lý tài khoản và thông tin người dùng.
  - **Email Theme**: Bản tin email đặt lại mật khẩu và xác minh tài khoản HTML/text.
- **Hỗ trợ đa ngôn ngữ**: Tiếng Việt (`vi` - mặc định) và English (`en`).

---

## 2. Cấu trúc thư mục Theme
```text
themes/ecommerce/
├── login/
│   ├── theme.properties
│   ├── resources/
│   │   ├── css/tokens.css
│   │   ├── css/styles.css
│   │   └── img/logo.svg
│   └── messages/
│       ├── messages_vi.properties
│       └── messages_en.properties
├── account/
│   └── theme.properties
└── email/
    ├── theme.properties
    └── messages/
        ├── messages_vi.properties
        └── messages_en.properties
```

---

## 3. Lệnh đóng gói Docker Image
Chạy từ thư mục `backend/`:
```bash
docker build -f docker/keycloak/Dockerfile -t de013/ecommerce-keycloak:26.4.5-profile-sync .
```

---

## 4. Kích hoạt Theme trên Realm
Trong `docker/keycloak/realm-config/ecommerce-realm-realm.json`:
```json
{
  "loginTheme": "ecommerce",
  "accountTheme": "ecommerce",
  "emailTheme": "ecommerce",
  "internationalizationEnabled": true,
  "supportedLocales": [
    "vi",
    "en"
  ],
  "defaultLocale": "vi"
}
```
*(Nếu database Keycloak đã tồn tại phiên làm việc trước đó, có thể cập nhật trực tiếp tại Admin Console: **Realm settings -> Themes -> Login theme: ecommerce**).*
