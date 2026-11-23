package vn.edu.auction.client.api;

import vn.edu.auction.client.I18n;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import vn.edu.auction.client.api.AuthApiClient.ApiException;
import vn.edu.auction.client.model.SessionData;

/**
 * Lớp cha dùng chung cho mọi API client: BASE_URL, HttpClient, Jackson, JWT và xử lý lỗi.

 */
public abstract class BaseApiClient {
    protected static final String BASE_URL = trimSlash(System.getProperty("auction.server.url",
            System.getenv().getOrDefault("AUCTION_SERVER_URL", "https://baitaplon-ltnc-nhom10vip.onrender.com")));
    public static String serverUrl() { return BASE_URL; }
    protected static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** type == null: không cần đọc body (DELETE, 204...). */
    protected <T> T call(String method, String path, Object body, TypeReference<T> type) throws ApiException {
        return call(method, path, body, type, REQUEST_TIMEOUT);
    }

    protected <T> T call(String method, String path, Object body, TypeReference<T> type, Duration timeout) throws ApiException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE_URL + path))
            .timeout(timeout).header("Accept", "application/json");    //mặc định trả về JSON, phải nhận timeout vì server có thể chậm
        String token = SessionData.getInstance().getToken();
        if (token != null && !token.isBlank()) b.header("Authorization", "Bearer " + token);

        try {
            if (body != null) {
                b.header("Content-Type", "application/json").method(method,
                        HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = res.statusCode();
            if (status >= 200 && status < 300) {
                if (type == null || res.body() == null || res.body().isBlank()) return null;
                return MAPPER.readValue(res.body(), type);
            }
            throw toApiException(status, res.body());
        } catch (HttpTimeoutException e) {
            throw new ApiException(ApiException.Type.TIMEOUT, 0, null, e);
        } catch (JsonProcessingException e) {
            throw new ApiException(ApiException.Type.UNKNOWN, 0, null, e);
        } catch (IOException e) {
            throw new ApiException(ApiException.Type.NETWORK, 0, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ApiException.Type.UNKNOWN, 0, null, e);
        }
    }

    private ApiException toApiException(int status, String body) {
        ApiException.Type type = switch (status) {
            case 400 -> ApiException.Type.BAD_REQUEST;
            case 401 -> ApiException.Type.UNAUTHORIZED;
            case 403 -> ApiException.Type.FORBIDDEN;
            case 404 -> ApiException.Type.NOT_FOUND; 
            case 409 -> ApiException.Type.CONFLICT;
            case 429 -> ApiException.Type.RATE_LIMITED;
            default -> status >= 500 ? ApiException.Type.SERVER : ApiException.Type.UNKNOWN;
        };
        String msg = null;
        if (status >= 400 && status < 500) { // 5xx không lấy message để khỏi lộ chi tiết nội bộ
            try {
                String m = MAPPER.readTree(body).path("message").asText(null);
                msg = (m == null || m.isBlank()) ? null : m;
            } catch (Exception ignored) {
                // body không phải JSON => dùng câu mặc định
            }
        }
        return new ApiException(type, status, msg, null);
    }

    /** Chuyển lỗi thành câu dễ hiểu để hiện lên label, không bao giờ in stack trace. */
    public static String describe(Throwable t) {
        if (!(t instanceof ApiException e)) return I18n.get("error.unexpected");
        // 404: bỏ qua chữ thô của server (vd. "Not Found") và báo câu dễ hiểu
        if (e.getType() == ApiException.Type.NOT_FOUND) return I18n.get("error.notFound");
        if (I18n.isVietnamese() && e.getServerMessage() != null) return e.getServerMessage();
        return switch (e.getType()) {
            case NETWORK -> I18n.get("error.offline");
            case TIMEOUT -> I18n.get("error.slow");
            case UNAUTHORIZED -> I18n.get("error.session");
            case FORBIDDEN -> I18n.get("error.denied");
            case BAD_REQUEST -> I18n.get("error.data");
            case CONFLICT -> I18n.get("error.conflict");
            case RATE_LIMITED -> I18n.get("error.throttle");
            case SERVER -> I18n.get("error.service");
            default -> I18n.get("error.retry");
        };
    }

    private static String trimSlash(String s) { return s.endsWith("/") ? s.substring(0, s.length() - 1) : s; }
}
