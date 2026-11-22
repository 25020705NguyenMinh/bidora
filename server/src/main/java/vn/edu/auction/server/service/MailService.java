package vn.edu.auction.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import vn.edu.auction.server.exception.EmailSendException;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final URI BREVO_ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");

    // VÌ SAO dùng ObjectProvider: nếu chưa cấu hình spring.mail.host thì Spring không tạo
    // JavaMailSender; inject thẳng sẽ làm server không khởi động được.
    // Dùng ObjectProvider thì chưa cấu hình vẫn chạy, chỉ in mã ra console (chế độ dev).
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;
    // Gửi qua HTTPS (cổng 443) khi có API key. Cần cho Render gói free vì cổng SMTP 25/465/587 bị chặn.
    private final String brevoApiKey;
    private final String brevoSenderEmail;
    private final String brevoSenderName;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${app.mail.from}") String from,
                       @Value("${app.mail.brevo.api-key:}") String brevoApiKey,
                       @Value("${app.mail.brevo.sender-email:}") String brevoSenderEmail,
                       @Value("${app.mail.brevo.sender-name:Bidora}") String brevoSenderName) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
        this.brevoApiKey = brevoApiKey == null ? "" : brevoApiKey.trim();
        this.brevoSenderEmail = brevoSenderEmail == null || brevoSenderEmail.isBlank() ? from : brevoSenderEmail.trim();
        this.brevoSenderName = brevoSenderName;
    }

    public void sendOtp(String to, String code, long expirationMinutes) {
        String subject = "[Bidora.] Mã xác minh của bạn";
        String text = "Mã xác minh của bạn là: " + code
                + "\n\nMã có hiệu lực trong " + expirationMinutes + " phút."
                + "\nNếu bạn không thực hiện yêu cầu này, hãy bỏ qua email.";

        if (!brevoApiKey.isEmpty()) {
            sendViaBrevo(to, subject, text);
            return;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();

        if (sender == null) {
            // CHỈ dành cho dev. Khi chạy thật phải cấu hình SMTP để mã không nằm trong log.
            log.warn("[DEV] Chua cau hinh SMTP. Ma OTP cua {} la: {}", to, code);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        try {
            sender.send(message);
        } catch (MailException e) {
            // Ghi chi tiết ở log server, người dùng chỉ nhận câu chung (không lộ thông tin SMTP)
            log.error("Gui email OTP toi {} that bai", to, e);
            throw new EmailSendException(e);
        }
    }

    private void sendViaBrevo(String to, String subject, String text) {
        try {
            String body = json.writeValueAsString(Map.of(
                    "sender", Map.of("name", brevoSenderName, "email", brevoSenderEmail),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "textContent", text));
            HttpRequest request = HttpRequest.newBuilder(BREVO_ENDPOINT)
                    .timeout(Duration.ofSeconds(10))
                    .header("api-key", brevoApiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // Brevo trả lý do (sender chưa xác minh, key sai, hết hạn mức...). Không log api key.
                log.error("Brevo tu choi gui email OTP toi {}: HTTP {} - {}", to, response.statusCode(), response.body());
                throw new EmailSendException(new IllegalStateException("Brevo HTTP " + response.statusCode()));
            }
        } catch (IOException e) {
            log.error("Gui email OTP qua Brevo toi {} that bai", to, e);
            throw new EmailSendException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailSendException(e);
        }
    }
}
