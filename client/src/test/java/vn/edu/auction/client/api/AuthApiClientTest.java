package vn.edu.auction.client.api;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import vn.edu.auction.shared.dto.RegisterRequest;
import static org.junit.jupiter.api.Assertions.*;

class AuthApiClientTest {
    @Test void registrationDistinguishesMailFailureFromAnUnavailableServer() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var mailFailure = new java.util.concurrent.atomic.AtomicBoolean(true);
        server.createContext("/api/auth/register", ex -> {
            if (mailFailure.get()) ex.getResponseHeaders().add("X-Bidora-Error", "OTP_DELIVERY_FAILED");
            ex.sendResponseHeaders(503, -1);
            ex.close();
        });
        server.start();
        try {
            var api = new AuthApiClient("http://127.0.0.1:" + server.getAddress().getPort());
            var request = new RegisterRequest("An", "an@example.test", "test-pass", null);
            assertEquals(AuthApiClient.ApiException.Type.OTP_DELIVERY,
                    assertThrows(AuthApiClient.ApiException.class, () -> api.register(request)).getType());
            mailFailure.set(false);
            assertEquals(AuthApiClient.ApiException.Type.SERVER,
                    assertThrows(AuthApiClient.ApiException.class, () -> api.register(request)).getType());
        } finally { server.stop(0); }
    }
}
