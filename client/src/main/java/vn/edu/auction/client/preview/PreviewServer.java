package vn.edu.auction.client.preview;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import vn.edu.auction.client.api.BaseApiClient;
import vn.edu.auction.shared.dto.AuctionResponse;
import vn.edu.auction.shared.dto.BidResponse;
import vn.edu.auction.shared.dto.ExtraDto.*;
import vn.edu.auction.shared.dto.ItemResponse;
import vn.edu.auction.shared.enums.*;

/** In-memory account for reviewing the desktop UI through its normal REST calls. */
public final class PreviewServer {
    private final List<ItemResponse> items = new ArrayList<>();
    private final List<AuctionResponse> auctions = new ArrayList<>();
    private final List<BidResponse> bids = new ArrayList<>();
    private final List<WalletTxResponse> transactions = new ArrayList<>();
    private final List<ChatMessageResponse> messages = new ArrayList<>();
    private BigDecimal balance = new BigDecimal("2500000");
    private boolean watching, muted, following;
    private String name = "Tài khoản xem thử", phone = "", address = "";

    private PreviewServer() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        items.add(new ItemResponse(11L, 2L, "Máy ảnh Sony A7 III", "Máy ảnh mirrorless, thân máy và ống kính.",
                new BigDecimal("12000000"), ItemStatus.ACTIVE));
        items.add(new ItemResponse(12L, 2L, "Đồng hồ dây da", "Đồng hồ cơ với dây da màu nâu.",
                new BigDecimal("3500000"), ItemStatus.ACTIVE));
        auctions.add(new AuctionResponse(21L, 11L, items.get(0).getName(), items.get(0).getStartingPrice(),
                items.get(0).getStartingPrice(), null, now.minusHours(1), now.plusDays(2), AuctionStatus.RUNNING));
        auctions.add(new AuctionResponse(22L, 12L, items.get(1).getName(), items.get(1).getStartingPrice(),
                items.get(1).getStartingPrice(), null, now.plusHours(1), now.plusDays(3), AuctionStatus.OPEN));
    }

    public static int start() throws IOException {
        PreviewServer preview = new PreviewServer();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", preview::handle);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "bidora-preview");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        return server.getAddress().getPort();
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        JsonNode body = BaseApiClient.MAPPER.readTree(exchange.getRequestBody());
        Object result;
        int status = 200;
        try {
            if (path.equals("/api/items") && method.equals("GET")) result = items;
            else if (path.startsWith("/api/items/") && method.equals("GET")) {
                long id = Long.parseLong(path.substring("/api/items/".length()));
                result = items.stream().filter(i -> i.getId() == id).findFirst().orElseThrow();
            } else if (path.equals("/api/auctions") && method.equals("GET")) result = auctions;
            else if (path.matches("/api/auctions/\\d+") && method.equals("GET")) {
                long id = Long.parseLong(path.split("/")[3]);
                result = auctions.stream().filter(a -> a.getId() == id).findFirst().orElseThrow();
            }
            else if (path.matches("/api/auctions/\\d+/bids") && method.equals("GET")) result = bids;
            else if (path.matches("/api/auctions/\\d+/bids") && method.equals("POST")) {
                long auctionId = Long.parseLong(path.split("/")[3]);
                AuctionResponse auction = auctions.stream().filter(a -> a.getId() == auctionId).findFirst().orElseThrow();
                BigDecimal amount = body.path("amount").decimalValue();
                if (auction.getStatus() != AuctionStatus.RUNNING || amount.compareTo(auction.getCurrentPrice()) <= 0) {
                    send(exchange, 400, java.util.Map.of("message", "Giá đặt phải cao hơn giá hiện tại của phiên đang diễn ra."));
                    return;
                }
                auction.setCurrentPrice(amount);
                auction.setHighestBidderId(1L);
                BidResponse bid = new BidResponse((long) bids.size() + 1, auctionId, 1L, amount,
                        LocalDateTime.now(ZoneOffset.UTC), BidStatus.ACCEPTED, "Đã đặt giá trong bản xem thử.");
                bids.add(bid);
                result = bid;
            } else if (path.matches("/api/auctions/\\d+/watch") && method.equals("GET")) {
                result = new WatchStateResponse(watching, muted);
            } else if (path.matches("/api/auctions/\\d+/watch") && method.equals("PUT")) {
                watching = body.path("watching").asBoolean();
                muted = body.path("muted").asBoolean();
                result = new WatchStateResponse(watching, muted);
            } else if (path.equals("/api/me/wallet")) result = new WalletResponse(balance);
            else if (path.equals("/api/me/wallet/transactions")) result = transactions.reversed();
            else if (path.equals("/api/me/wallet/deposit") || path.equals("/api/me/wallet/withdraw")) {
                BigDecimal amount = body.path("amount").decimalValue();
                boolean deposit = path.endsWith("deposit");
                if (amount.signum() <= 0 || (!deposit && amount.compareTo(balance) > 0)) {
                    send(exchange, 400, java.util.Map.of("message", "Số tiền không hợp lệ hoặc vượt quá số dư."));
                    return;
                }
                balance = deposit ? balance.add(amount) : balance.subtract(amount);
                transactions.add(new WalletTxResponse((long) transactions.size() + 1,
                        deposit ? "DEPOSIT" : "WITHDRAW", amount, balance, LocalDateTime.now(ZoneOffset.UTC)));
                result = new WalletResponse(balance);
            } else if (path.equals("/api/me/profile") && method.equals("GET")) result = profile();
            else if (path.equals("/api/me/profile") && method.equals("PUT")) {
                name = body.path("fullName").asText(name);
                phone = body.path("phone").asText("");
                address = body.path("address").asText("");
                result = profile();
            } else if (path.equals("/api/me/password")) {
                send(exchange, 400, java.util.Map.of("message", "Bản xem thử không có mật khẩu. Hãy dùng đăng ký khi server hoạt động."));
                return;
            } else if (path.equals("/api/notifications")) result = List.of();
            else if (path.equals("/api/notifications/read-all")) result = java.util.Map.of();
            else if (path.equals("/api/users/directory")) result = List.of(new UserBriefResponse(2L, "Người bán mẫu", UserRole.SELLER));
            else if (path.endsWith("/follow") && method.equals("GET")) result = new FollowStateResponse(following);
            else if (path.endsWith("/follow")) {
                following = method.equals("POST");
                result = java.util.Map.of();
            } else if (path.equals("/api/chat/2") && method.equals("GET")) result = messages;
            else if (path.equals("/api/chat/2") && method.equals("POST")) {
                ChatMessageResponse message = new ChatMessageResponse((long) messages.size() + 1,
                        1L, 2L, body.path("content").asText(), LocalDateTime.now(ZoneOffset.UTC));
                messages.add(message);
                result = message;
            } else {
                result = java.util.Map.of("message", "Chức năng này cần server thật.");
                status = 404;
            }
        } catch (Exception error) {
            result = java.util.Map.of("message", "Không thể xử lý thao tác xem thử.");
            status = 400;
        }
        send(exchange, status, result);
    }

    private ProfileResponse profile() {
        return new ProfileResponse(1L, name, "preview@bidora.local", phone, address, UserRole.BIDDER);
    }

    private static void send(HttpExchange exchange, int status, Object result) throws IOException {
        byte[] bytes = BaseApiClient.MAPPER.writeValueAsBytes(result);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
