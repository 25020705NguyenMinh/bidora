
package vn.edu.auction.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.auction.server.exception.EmailSendException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class MailService {

    private static final Logger log =
            LoggerFactory.getLogger(MailService.class);

    private static final String BREVO_API_URL =
            "https://api.brevo.com/v3/smtp/email";

    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MailService(
            @Value("${brevo.api-key:}") String apiKey,
            @Value("${brevo.from-email:}") String fromEmail,
            @Value("${brevo.from-name:Tech Auction}") String fromName,
            ObjectMapper objectMapper) {

        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void sendOtp(
            String to,
            String code,
            long expirationMinutes) {

        // Kiểm tra cấu hình trước khi gửi
        if (apiKey.isBlank() || fromEmail.isBlank()) {
            throw new EmailSendException(
                    new IllegalStateException(
                            "Chưa cấu hình BREVO_API_KEY hoặc BREVO_FROM_EMAIL"
                    )
            );
        }

        try {
            // Nội dung email OTP
            String emailText =
                    "Xin chào,\n\n"
                            + "Mã xác minh tài khoản Tech Auction của bạn là: "
                            + code
                            + "\n\nMã có hiệu lực trong "
                            + expirationMinutes + " phút."
                            + "\n\nNếu bạn không thực hiện yêu cầu này, "
                            + "hãy bỏ qua email."
                            + "\n\nTech Auction Team";

            // Tạo dữ liệu JSON theo cấu trúc Brevo API
            Map<String, Object> payload = Map.of(
                    "sender", Map.of(
                            "name", fromName,
                            "email", fromEmail
                    ),
                    "to", List.of(
                            Map.of("email", to)
                    ),
                    "subject", "[Tech Auction] Mã xác minh của bạn",
                    "textContent", emailText
            );

            String json = objectMapper.writeValueAsString(payload);

            // Tạo HTTP POST request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_API_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("accept", "application/json")
                    .header("api-key", apiKey)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            // Gửi request tới Brevo
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            // Brevo thường trả HTTP 201 khi gửi thành công
            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                log.error(
                        "Brevo gửi email thất bại. HTTP status: {}, response: {}",
                        response.statusCode(),
                        response.body()
                );

                throw new EmailSendException(
                        new IllegalStateException(
                                "Brevo API trả về HTTP "
                                        + response.statusCode()
                        )
                );
            }

            log.info(
                    "Brevo đã tiếp nhận yêu cầu gửi OTP tới {}",
                    to
            );

        } catch ( IOException e) {

            log.error("Lỗi khi tạo hoặc gửi request tới Brevo", e);
            throw new EmailSendException(e);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            log.error("Request gửi email bị gián đoạn", e);
            throw new EmailSendException(e);
        }
    }
}
