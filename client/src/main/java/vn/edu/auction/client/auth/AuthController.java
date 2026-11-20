package vn.edu.auction.client.auth;


import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import vn.edu.auction.client.I18n;
import vn.edu.auction.client.api.AuthApiClient;
import vn.edu.auction.client.controller.NavigationController;
import vn.edu.auction.client.model.SessionData;
import vn.edu.auction.client.navigation.Navigator;
import vn.edu.auction.shared.dto.AuthResponse;
import vn.edu.auction.shared.dto.LoginRequest;
import vn.edu.auction.shared.dto.RegisterRequest;
import vn.edu.auction.shared.enums.UserRole;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.regex.Pattern;


import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import java.util.function.UnaryOperator;

public class AuthController {

    // ====================================================================
    // 1. KHAI BÁO CÁC THÀNH PHẦN TRONG FXML (fx:id)
    // ====================================================================
    @FXML private ComboBox<String> languageComboBox;

    @FXML private TextField emailField;
    @FXML private Label emailError;

    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private HBox passwordContainer;
    @FXML private Label passwordError;
    @FXML private Button togglePasswordButton;
    private boolean passwordVisible = false;

    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    private Runnable afterProfileReset;
    @FXML private Label firstNameError;
    @FXML private Label lastNameError;

    @FXML private Label messageLabel;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    // ---- Màn hình OTP (khớp fx:id trong otp-verification-view.fxml) ----
    @FXML private TextField otp1, otp2, otp3, otp4, otp5, otp6;
    @FXML private Label emailLabel;
    @FXML private Hyperlink resendLink;
    @FXML private Label countdownLabel;
    @FXML private Button continueButton;

    @FXML private PasswordField confirmPasswordField;
    @FXML private Label confirmPasswordError;
    @FXML private Button submitButton;
    private TextField[] otpBoxes;
    private Timeline resendTimer;
    private boolean otpFilling = false;          // true khi code đang tự điền ô (dán mã)
    private static final int RESEND_SECONDS = 60; // phải khớp app.otp.resend-cooldown-seconds của server

    private static final String LANG_VI = "Tiếng Việt";
    private static final String LANG_EN = "English";

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    // ---- SỬA: cấu hình chuyển trang ----
    // Thư mục chứa các file FXML của phần auth (tính từ /vn/edu/auction/client/)
    private static final String VIEW_DIR = "/vn/edu/auction/client/fxml/auth/";

    private boolean busy;
    private int resendSecondsLeft;

    // [3] Dùng System.Logger để ghi chi tiết lỗi ra CONSOLE cho lập trình viên,
    // còn giao diện chỉ hiện câu thông báo dễ hiểu (không hiện stack trace).
    private static final System.Logger LOG = System.getLogger(AuthController.class.getName());

    // [3] static final: dùng chung 1 đối tượng cho mọi màn hình auth.
    // VÌ SAO: mỗi lần chuyển trang JavaFX tạo controller mới; nếu `new AuthApiClient()` trong
    // controller thì mỗi lần lại tạo thêm 1 HttpClient (tốn luồng và bộ nhớ).
    private static final AuthApiClient AUTH_API = new AuthApiClient();

    // ====================================================================
    // 2. KHỞI TẠO
    // ====================================================================
    @FXML
    public void initialize() {
        if (messageLabel != null) {
            messageLabel.setText("");
            messageLabel.visibleProperty().bind(messageLabel.textProperty().isNotEmpty());
            messageLabel.managedProperty().bind(messageLabel.visibleProperty());
        }

        setupLanguageComboBox();

        if (emailField != null) {
            emailField.setText(RegisterData.email);
            emailField.textProperty().addListener(
                    (obs, oldValue, newValue) -> clearFieldError(emailError, emailField));
        }

        if (passwordField != null && passwordVisibleField != null) {
            passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
        }
        if (passwordField != null) {
            passwordField.textProperty().addListener(
                    (obs, oldValue, newValue) -> clearFieldError(passwordError, passwordContainer));
        }
        if (togglePasswordButton != null) {
            togglePasswordButton.setGraphic(passwordIcon(false));
            I18n.localize(togglePasswordButton.accessibleTextProperty(), "password.show");
        }
        if (confirmPasswordField != null) {
            confirmPasswordField.textProperty().addListener((obs, oldValue, newValue) ->
                    clearFieldError(confirmPasswordError, confirmPasswordField));
        }

        if (firstNameField != null) {
            firstNameField.textProperty().addListener(
                    (obs, oldValue, newValue) -> clearFieldError(firstNameError, firstNameField));
        }
        if (lastNameField != null) {
            lastNameField.textProperty().addListener(
                    (obs, oldValue, newValue) -> clearFieldError(lastNameError, lastNameField));
        }
        // Trang OTP: có ô otp1 thì cấu hình 6 ô nhập và đồng hồ gửi lại
        if (otp1 != null) {
            setupOtpView();
        }
    }

    // ====================================================================
    // 3. CHỌN NGÔN NGỮ
    // ====================================================================
    private void setupLanguageComboBox() {
        if (languageComboBox == null) {
            return;
        }
        languageComboBox.getItems().setAll(LANG_VI, LANG_EN);
        languageComboBox.setValue(I18n.isVietnamese() ? LANG_VI : LANG_EN);
        languageComboBox.setOnAction(e -> handleLanguageChange());
        I18n.onChange(languageComboBox, () ->
                languageComboBox.setValue(I18n.isVietnamese() ? LANG_VI : LANG_EN));
    }

    private void handleLanguageChange() {
        Locale selected = LANG_EN.equals(languageComboBox.getValue())
                ? Locale.ENGLISH
                : I18n.VIETNAMESE;

        if (selected.getLanguage().equals(I18n.getLocale().getLanguage())) {
            return;
        }

        I18n.setLocale(selected);


    }

    // ====================================================================
    // 4. ĐĂNG NHẬP
    // ====================================================================
    private static AuthResponse fromJwt(String token, String fallbackName) {
        try {
            String[] parts = token.split("\\.");
            byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
            com.fasterxml.jackson.databind.JsonNode n =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            AuthResponse r = new AuthResponse();
            r.setToken(token);
            r.setUserId(Long.valueOf(n.get("sub").asText()));
            r.setEmail(n.path("email").asText(null));
            r.setRole(UserRole.valueOf(n.path("role").asText("BIDDER")));
            r.setFullName(fallbackName);
            return r;
        } catch (Exception e) {
            return null;
        }
    }
    @FXML
    private void handleLogin() {
        if (busy) return;
        showMessage("");

        boolean emailOk = validateEmail();
        boolean passwordOk = validatePassword(false);

        if (!emailOk || !passwordOk) {
            return;
        }

        // Dựng request ngay trên luồng giao diện (đọc ô nhập chỉ được làm ở đây);
        // luồng nền chỉ nhận request đã dựng sẵn.
        LoginRequest request = new LoginRequest(
                emailField.getText().trim(),
                passwordField.getText());

        runAsync(() -> AUTH_API.login(request), this::onLoginSuccess);
    }
    private void onLoginSuccess(AuthResponse response) {
        Stage stage = findStage();
        if (stage == null) {
            showLocalizedMessage("error.openView");
            return;
        }
        if (!stage.isShowing()) {
            return;
        }
        // 1) Lưu AuthResponse (có token) vào phiên để các màn hình sau dùng gọi API.
        SessionData.getInstance().setSession(response);

        // 2) Xóa mật khẩu khỏi ô nhập. VÌ SAO: không để mật khẩu nằm lại trong bộ nhớ giao diện
        //    lâu hơn cần thiết. Mật khẩu cũng không được lưu ở SessionData hay RegisterData.
        if (passwordField != null) {
            passwordField.clear();
        }
        RegisterData.reset();

        if (Boolean.TRUE.equals(stage.getProperties().get("auction.auth.popup"))) {
            stage.close();
            return;
        }

        // 3) Báo cho các bộ điều hướng biết cửa sổ chính, rồi chuyển tới trang chủ.
        //    VÌ SAO set cả hai: Main chưa gọi setStage/setPrimaryStage; thiếu bước này
        //    showHome() không làm gì vì stage = null, và đăng xuất cũng hỏng theo.
        Navigator.setStage(stage);
        NavigationController.setPrimaryStage(stage);
        NavigationController.showHome();
    }
    @FXML
    private void handleGoogleLogin() {
        showMessage("");

        // 1. Mở trạm thu sóng nội bộ tại port 8081 để hứng Token và Name từ Server
        startLocalCallbackServer();

        // 2. Mở trình duyệt gọi lên Spring Boot Server
        String googleAuthUrl = vn.edu.auction.client.api.BaseApiClient.serverUrl() + "/oauth2/authorization/google";
        openOAuthBrowser(googleAuthUrl);
    }

    /**
     * Mở một HTTP Server tạm thời trên JavaFX (cổng 8081) để nhận Token và Name trả về.
     */
    private void startLocalCallbackServer() {
        try {
            com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(8081), 0);
            Stage authStage = findStage();
            if (authStage != null && Boolean.TRUE.equals(authStage.getProperties().get("auction.auth.popup"))) {
                authStage.getProperties().put("auction.google.callback", server);
            }

            server.createContext("/callback", exchange -> {
                try {
                    String query = exchange.getRequestURI().getQuery();
                    String token = null;
                    String name = I18n.get("account.user"); // Tên mặc định nếu không lấy được

                    // Tách lấy token và name từ URL
                    if (query != null) {
                        for (String param : query.split("&")) {
                            if (param.startsWith("token=")) {
                                token = param.substring(6);
                            } else if (param.startsWith("name=")) {
                                // Giải mã tiếng Việt
                                name = java.net.URLDecoder.decode(param.substring(5), java.nio.charset.StandardCharsets.UTF_8);
                            }
                        }
                    }

                    // Trả về HTML báo cho người dùng tắt trình duyệt
                    String htmlResponse = I18n.get("oauth.pageTitle")
                            + "<body style='text-align:center; padding-top:50px; font-family:Arial;'>"
                            + I18n.get("oauth.done")
                            + I18n.get("oauth.return")
                            + "</body></html>";

                    byte[] responseBytes = htmlResponse.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    exchange.getResponseBody().write(responseBytes);

                    if (token != null && !token.isEmpty()) {
                        AuthResponse me = fromJwt(token, name);
                        Platform.runLater(() -> {
                            if (me == null) {
                                showLocalizedMessage("oauth.invalid");
                            } else {
                                onLoginSuccess(me);
                            }
                        });
                    }
                } finally {
                    exchange.close();
                    server.stop(0); // Tắt server nội bộ ngay khi nhận xong
                }
            });

            server.start();
            System.out.println("Local Callback Server started on port 8081");

        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Loi khoi tao tram nhan dang nhap Google", e);
            Platform.runLater(() -> showLocalizedMessage("oauth.callback"));
        }
    }

//
//    @FXML
//    private void handleFacebookLogin() {
//        // Link đăng nhập Facebook OAuth2 (Thay app_id bằng App ID từ Meta Developer)
//        String facebookAuthUrl = "https://www.facebook.com/v18.0/dialog/oauth"
//                + "?client_id=YOUR_FACEBOOK_APP_ID"
//                + "&redirect_uri=http://localhost:8080/login/oauth2/code/facebook"
//                + "&scope=email,public_profile";
//
//        openOAuthBrowser(facebookAuthUrl);
//    }
//
//    @FXML
//    private void handleLinkedinLogin() {
//        // Link đăng nhập LinkedIn OAuth2
//        String linkedinAuthUrl = "https://www.linkedin.com/oauth/v2/authorization"
//                + "?response_type=code"
//                + "&client_id=YOUR_LINKEDIN_CLIENT_ID"
//                + "&redirect_uri=http://localhost:8080/login/oauth2/code/linkedin"
//                + "&scope=openid%20profile%20email";
//
//        openOAuthBrowser(linkedinAuthUrl);
//    }

    /**
     * Hàm mở URL trang đăng nhập OAuth2 trên trình duyệt hệ thống (Chrome/Edge)
     */
    private void openOAuthBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } else {
                // Dự phòng nếu hệ thống không hỗ trợ AWT Desktop
                String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
                if (os.contains("win")) {
                    new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", url).start();
                } else if (os.contains("nix") || os.contains("nux")) {
                    new ProcessBuilder("xdg-open", url).start();
                } else {
                    throw new IOException("Unsupported browser launcher on " + os);
                }
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Khong the mo trang dang nhap mang xa hoi", e);
            showLocalizedMessage("oauth.browser");
        }
    }

    // ====================================================================
    // 5. ĐĂNG KÝ
    // ====================================================================
    @FXML
    private void handleEmailContinue() {
        showMessage("");
        if (!validateEmail()) {
            return;
        }
        RegisterData.email = emailField.getText().trim();
        openRegisterView();
    }

    @FXML
    private void handleRegister() {
        if (busy) return;
        showMessage("");

        boolean firstOk = validateRequired(firstNameField, firstNameError, "error.firstName.empty");
        boolean lastOk = validateRequired(lastNameField, lastNameError, "error.lastName.empty");
        boolean passwordOk = validatePassword(true);

        if (!firstOk || !lastOk || !passwordOk) {
            return;
        }

        String fullName = firstNameField.getText().trim() + " " + lastNameField.getText().trim();
        String email = RegisterData.email.trim();

        // Dùng đúng RegisterRequest trong module shared để gửi cho server.
        RegisterRequest request = new RegisterRequest(
                fullName,
                email,
                passwordField.getText(),
                UserRole.BIDDER);

        runAsync(() -> AUTH_API.register(request), response -> onRegisterSuccess(email));
    }

    /**
     * Đăng ký xong: hiện thông báo thành công, rồi chuyển về trang đăng nhập.
     * VÌ SAO không tự đăng nhập luôn dù server có trả token: đề yêu cầu "hiển thị thành công";
     * để người dùng tự đăng nhập là luồng quen thuộc, và token chỉ được lưu ở một chỗ (handleLogin).
     */
    /**
     * Đăng ký xong: server đã gửi mã OTP tới email, nên chuyển sang trang nhập mã.
     * Trang OTP đọc email từ RegisterData.email (đã được giữ lại).
     */
    private void onRegisterSuccess(String email) {
        if (passwordField != null) {
            passwordField.clear();
        }
        RegisterData.email = email;
        switchView("otp-verification-view.fxml", "title.otpMethod");
    }


    // ====================================================================
    // 6. GỌI API Ở LUỒNG NỀN  [MỚI]
    // ====================================================================
    private <T> void runAsync(Callable<T> apiCall, Consumer<T> onSuccess) {
        if (busy) return;
        showLocalizedMessage("message.loading");
        messageLabel.getStyleClass().add("pending");
        setBusy(true);

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return apiCall.call();
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false);
            Stage stage = findStage();
            if (stage == null || !stage.isShowing()) return;
            showMessage("");
            onSuccess.accept(task.getValue());
        });

        task.setOnFailed(e -> {
            setBusy(false);
            showMessage("");
            Throwable error = task.getException();
            I18n.track(messageLabel.textProperty(), () -> describeError(error));
        });

        Thread thread = new Thread(task, "auth-api-call");
        thread.setDaemon(true);
        thread.start();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        if (languageComboBox != null) languageComboBox.setDisable(busy);
        if (submitButton != null) submitButton.setDisable(busy);
        if (resendLink != null) resendLink.setDisable(busy || resendSecondsLeft > 0);
        if (loginButton != null) {
            loginButton.setDisable(busy);
        }
        if (registerButton != null) {
            registerButton.setDisable(busy);
        }
        if (continueButton != null) {
            continueButton.setDisable(busy);
        }
    }

    private String describeError(Throwable error) {
        LOG.log(System.Logger.Level.WARNING, "Goi API xac thuc that bai", error);

        if (error instanceof AuthApiClient.ApiException apiError) {
            String serverMessage = I18n.isVietnamese() ? apiError.getServerMessage() : null;
            return switch (apiError.getType()) {
                // Các lỗi mà câu của server cụ thể hơn: sai OTP (còn mấy lần thử), chờ bao lâu, chưa xác minh...
                case BAD_REQUEST -> serverMessage != null ? serverMessage : I18n.get("error.badRequest");
                case FORBIDDEN -> serverMessage != null ? serverMessage : I18n.get("error.forbidden");
                case RATE_LIMITED -> serverMessage != null ? serverMessage : I18n.get("error.throttle");
                case UNAUTHORIZED -> I18n.get("error.login.invalid");
                case CONFLICT -> I18n.get("error.email.exists");
                case OTP_DELIVERY -> I18n.get("error.otp.delivery");
                case SERVER -> I18n.get("error.server");
                case NETWORK -> I18n.get("error.network");
                case TIMEOUT -> I18n.get("error.timeout");
                case UNKNOWN -> I18n.get("error.unknown");
            };
        }
        return I18n.get("error.unknown");
    }
    // ====================================================================
    // 6. QUÊN MẬT KHẨU
    // ====================================================================
    @FXML
    private void handleConfirmReset() {
        if (busy) return;
        showMessage("");
        if (!validateEmail()) return;

        String email = emailField.getText().trim();
        RegisterData.email = email;
        RegisterData.isResetPasswordFlow = true; // Bật cờ luồng quên mật khẩu

        // Gọi API Server gửi OTP tới email
        runAsync(() -> AUTH_API.forgotPassword(email), response -> {
            openOtpVerification(); // Chuyển sang Màn hình OTP
        });
    }
    @FXML
    private void handleResetPasswordSubmit() {
        if (busy) return;
        showMessage("");
        boolean passOk = validatePassword(true);
        String newPassword = passwordField.getText();
        String confirmPassword = confirmPasswordField != null ? confirmPasswordField.getText() : "";

        if (!passOk) return;
        if (!newPassword.equals(confirmPassword)) {
            showFieldError(confirmPasswordError, confirmPasswordField, "error.password.mismatch");
            return;
        }

        String email = RegisterData.email;
        String otp = RegisterData.verifiedOtp;

        // Gọi API gửi Email + OTP + Pass mới lên Server để cập nhật DB
        runAsync(() -> AUTH_API.resetPassword(email, otp, newPassword), response -> {
            RegisterData.reset();
            if (afterProfileReset != null) {
                findStage().close();
                afterProfileReset.run();
                return;
            }
            AuthController login = switchView("login-view.fxml", "title.login");
            if (login != null) login.showSuccess("account.resetDone");
        });
    }
    /** Nút Tiếp tục trên màn hình OTP (khớp onAction="#onContinue" trong FXML). */
    @FXML
    private void onContinue() {
        if (busy) return;
        showMessage("");
        String code = getOtpCode();
        if (code.length() != 6) {
            showLocalizedMessage("error.otp.incomplete");
            return;
        }

        String email = RegisterData.email;

        if (RegisterData.isResetPasswordFlow) {
            // Luồng Quên mật khẩu: Lưu OTP lại và chuyển sang Màn hình Reset 2
            RegisterData.verifiedOtp = code;
            switchView("reset-password2-view.fxml", "title.reset");
        } else {
            // Luồng Đăng ký: Xác minh OTP và đăng nhập vào thẳng Trang chủ
            runAsync(() -> AUTH_API.verifyEmail(email, code), this::onLoginSuccess);
        }
    }
    // ====================================================================
    // 7. CHUYỂN TRANG (ĐÃ SỬA)
    //    Chỉ truyền tên file, VIEW_DIR sẽ tự thêm "fxml/auth/" phía trước.
    // ====================================================================

    @FXML
    private void openLoginView() {
        RegisterData.reset();
        if (afterProfileReset != null) {
            if (resendTimer != null) resendTimer.stop();
            findStage().close();
            return;
        }
        switchView("login-view.fxml", "title.login");
    }

    @FXML
    private void openSignupMethodView() {
        switchView("signup-method-view.fxml", "title.signupMethod");
    }

    @FXML
    private void openRegisterView() {
        switchView("register-view.fxml", "title.register");
    }
    @FXML
    private void openOtpVerification() {
        switchView("otp-verification-view.fxml", "title.otpMethod");
    }

    @FXML
    private void openResetPasswordView() {
        switchView("reset-password-view.fxml", "title.reset");
    }

    public void startProfileReset(String email, Runnable onSuccess) {
        emailField.setText(email);
        emailField.setEditable(false);
        afterProfileReset = onSuccess;
    }

    private AuthController switchView(String fxmlFile, String titleKey) {
        try {
            Stage stage = findStage();
            if (stage == null) {
                throw new IOException("Không tìm thấy cửa sổ hiện tại");
            }
            FXMLLoader loader = new FXMLLoader(getClass().getResource(VIEW_DIR + fxmlFile), I18n.getBundle());
            stage.getScene().setRoot(loader.load());
            I18n.localize(stage.titleProperty(), titleKey);
            AuthController next = loader.getController();
            next.afterProfileReset = afterProfileReset;
            if (resendTimer != null) resendTimer.stop();
            return next;
        } catch (IOException e) {
            LOG.log(System.Logger.Level.ERROR, "Khong mo duoc man hinh " + fxmlFile, e);
            showLocalizedMessage("error.openView");
            return null;
        }
    }

    /** Lấy Stage từ bất kỳ ô nào đang có trên trang hiện tại. */
    private Stage findStage() {
        Node[] nodes = {
                emailField, passwordField, firstNameField, lastNameField,
                languageComboBox, messageLabel, loginButton, registerButton
        };
        for (Node node : nodes) {
            if (node != null && node.getScene() != null) {
                Window window = node.getScene().getWindow();
                if (window instanceof Stage stage) {
                    return stage;
                }
            }
        }
        return null;
    }

    // ====================================================================
    // 8. ẨN / HIỆN MẬT KHẨU
    // ====================================================================
    @FXML
    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;

        passwordField.setVisible(!passwordVisible);
        passwordField.setManaged(!passwordVisible);

        passwordVisibleField.setVisible(passwordVisible);
        passwordVisibleField.setManaged(passwordVisible);

        togglePasswordButton.setGraphic(passwordIcon(passwordVisible));
        I18n.localize(togglePasswordButton.accessibleTextProperty(), passwordVisible ? "password.hide" : "password.show");

        TextField shown = passwordVisible ? passwordVisibleField : passwordField;
        shown.requestFocus();
        shown.positionCaret(shown.getText().length());
    }

    // ====================================================================
    // 9. KIỂM TRA + HIỆN LỖI DƯỚI Ô NHẬP
    // ====================================================================
    private boolean validateEmail() {
        String email = emailField.getText().trim();

        if (email.isEmpty()) {
            showFieldError(emailError, emailField, "error.email.empty");
            return false;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            showFieldError(emailError, emailField, "error.email.invalid");
            return false;
        }
        return true;
    }

    /** checkLength = true khi đăng ký (tối thiểu 6 ký tự), false khi đăng nhập. */
    private boolean validatePassword(boolean checkLength) {
        String password = passwordField.getText();

        if (password.isEmpty()) {
            showFieldError(passwordError, passwordContainer, "error.password.empty");
            return false;
        }
        if (checkLength && password.length() < 6) {
            showFieldError(passwordError, passwordContainer, "error.password.short");
            return false;
        }
        return true;
    }

    private boolean validateRequired(TextField field, Label errorLabel, String messageKey) {
        if (field.getText().trim().isEmpty()) {
            showFieldError(errorLabel, field, messageKey);
            return false;
        }
        return true;
    }

    private void showFieldError(Label errorLabel, Node field, String key) {
        if (errorLabel != null) {
            I18n.localize(errorLabel.textProperty(), key);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
        if (field != null && !field.getStyleClass().contains("input-error")) {
            field.getStyleClass().add("input-error");
            vn.edu.auction.client.ui.UiMotion.error(field);
        }
    }

    private void clearFieldError(Label errorLabel, Node field) {
        if (errorLabel != null) {
            errorLabel.setText("");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
        if (field != null) {
            field.getStyleClass().remove("input-error");
        }
    }

    // [SỬA] Thông báo lỗi: trả về kiểu mặc định (đỏ theo CSS)
    private void showLocalizedMessage(String key) {
        showMessage("");
        if (messageLabel != null) I18n.localize(messageLabel.textProperty(), key);
    }

    private void showMessage(String message) {
        if (messageLabel != null) {
            messageLabel.getStyleClass().removeAll("pending", "success");
            messageLabel.setText(message);
        }
    }

    // [MỚI] Thông báo thành công: đổi sang màu xanh lá
    private void showSuccess(String key) {
        if (messageLabel != null) {
            showLocalizedMessage(key);
            messageLabel.getStyleClass().add("success");
        }
    }
    // ====================================================================
    // 12. MÀN HÌNH NHẬP OTP  [MỚI]
    // ====================================================================
    private void setupOtpView() {
        otpBoxes = new TextField[]{otp1, otp2, otp3, otp4, otp5, otp6};
        for (int i = 0; i < otpBoxes.length; i++) {
            setupOtpBox(i);
        }

        if (emailLabel != null) {
            emailLabel.setText(maskEmail(RegisterData.email));
        }
        startResendCountdown(RESEND_SECONDS); // server vừa gửi mã nên bắt đầu đếm ngược luôn
        Platform.runLater(() -> otpBoxes[0].requestFocus());
    }

    /**
     * Cấu hình 1 ô OTP:
     *  - chỉ nhận 1 chữ số, gõ số mới thì thay số cũ;
     *  - gõ xong tự nhảy sang ô kế tiếp;
     *  - Backspace ở ô trống thì lùi về ô trước;
     *  - dán nguyên "123456" thì tự rải vào 6 ô.
     * VÌ SAO làm vậy: người dùng quen với cách nhập mã này, và dán mã từ email là thao tác rất hay gặp.
     */
    private void setupOtpBox(int index) {
        TextField box = otpBoxes[index];

        UnaryOperator<TextFormatter.Change> filter = change -> {
            String inserted = change.getText();

            if (inserted.isEmpty()) {
                return change; // xóa ký tự
            }
            if (inserted.length() > 1) { // dán nhiều ký tự
                String digits = inserted.replaceAll("\\D", "");
                if (!digits.isEmpty()) {
                    Platform.runLater(() -> fillOtpBoxes(index, digits));
                }
                return null;
            }
            char c = inserted.charAt(0);
            if (c < '0' || c > '9') {
                return null; // không phải chữ số thì bỏ
            }

            // Thay toàn bộ nội dung ô bằng chữ số vừa gõ (ô luôn tối đa 1 ký tự)
            change.setRange(0, change.getControlText().length());
            change.setCaretPosition(1);
            change.setAnchor(1);

            if (!otpFilling && index < otpBoxes.length - 1) {
                Platform.runLater(() -> otpBoxes[index + 1].requestFocus());
            }
            return change;
        };
        box.setTextFormatter(new TextFormatter<>(filter));

        box.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.BACK_SPACE && box.getText().isEmpty() && index > 0) {
                otpBoxes[index - 1].clear();
                otpBoxes[index - 1].requestFocus();
            }
        });
    }

    // Rải chuỗi số vào các ô bắt đầu từ ô start (dùng khi dán mã)
    private void fillOtpBoxes(int start, String digits) {
        otpFilling = true; // báo cho bộ lọc đừng tự nhảy ô trong lúc điền
        try {
            for (int k = 0; k < digits.length() && start + k < otpBoxes.length; k++) {
                otpBoxes[start + k].setText(String.valueOf(digits.charAt(k)));
            }
        } finally {
            otpFilling = false;
        }
        otpBoxes[Math.min(start + digits.length(), otpBoxes.length - 1)].requestFocus();
    }

    private String getOtpCode() {
        StringBuilder code = new StringBuilder();
        for (TextField box : otpBoxes) {
            code.append(box.getText());
        }
        return code.toString();
    }

    private void clearOtpBoxes() {
        for (TextField box : otpBoxes) {
            box.clear();
        }
        otpBoxes[0].requestFocus();
    }



    /** Link "Gửi mã mới". */
    @FXML
    private void onResend() {
        if (busy) return;
        showMessage("");
        String email = RegisterData.email;

        if (RegisterData.isResetPasswordFlow) {
            // Luồng Quên mật khẩu: Gọi lại API forgotPassword để phát lại OTP
            runAsync(() -> AUTH_API.forgotPassword(email), response -> {
                showSuccess("otp.resent");
                clearOtpBoxes();
                startResendCountdown(RESEND_SECONDS);
            });
        } else {
            // Luồng Đăng ký: Gọi API resendOtp như bình thường
            runAsync(() -> AUTH_API.resendOtp(email), response -> {
                showSuccess("otp.resent");
                clearOtpBoxes();
                startResendCountdown(RESEND_SECONDS);
            });
        }
    }

    /**
     * Khóa link "Gửi mã mới" và đếm ngược mỗi giây.
     * VÌ SAO: server chỉ cho xin mã mới sau 60 giây; khóa sẵn ở giao diện để người dùng
     * không bấm rồi mới bị báo lỗi.
     */
    private void startResendCountdown(int seconds) {
        if (resendLink == null || countdownLabel == null) {
            return;
        }
        if (resendTimer != null) {
            resendTimer.stop();
        }

        resendSecondsLeft = seconds;
        resendLink.setDisable(seconds > 0 || busy);
        countdownLabel.setText(seconds == 0 ? "" : String.format(I18n.get("otp.resend.in"), seconds));
        if (seconds == 0) return;

        resendTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            Stage stage = findStage();
            if (stage == null || !stage.isShowing()) {
                resendTimer.stop();
                return;
            }
            if (--resendSecondsLeft <= 0) {
                resendTimer.stop();
                resendLink.setDisable(busy);
                countdownLabel.setText("");
            } else {
                countdownLabel.setText(String.format(I18n.get("otp.resend.in"), resendSecondsLeft));
            }
        }));
        resendTimer.setCycleCount(Timeline.INDEFINITE);
        resendTimer.play();
    }

    // an@gmail.com -> an@***... : hiện 3 ký tự đầu + *** + phần @domain
    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 0) {
            return email == null ? "" : email;
        }
        String name = email.substring(0, at);
        return name.substring(0, Math.min(3, name.length())) + "***" + email.substring(at);
    }
    // ====================================================================
    // 10. TẢI ICON SVG
    // ====================================================================
    private Node passwordIcon(boolean visible) {
        String eye = "M2 12 C4 8 7 6 12 6 C17 6 20 8 22 12 C20 16 17 18 12 18 C7 18 4 16 2 12 Z "
                + "M15 12 A3 3 0 1 1 9 12 A3 3 0 1 1 15 12";
        return vn.edu.auction.client.ui.UiKit.icon(visible ? eye + " M3 3 L21 21" : eye);
    }

}
