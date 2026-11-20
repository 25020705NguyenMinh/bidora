package vn.edu.auction.client.preview;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import vn.edu.auction.client.api.BaseApiClient;
import vn.edu.auction.shared.dto.AuctionResponse;
import vn.edu.auction.shared.dto.BidResponse;
import vn.edu.auction.shared.dto.ExtraDto.*;
import vn.edu.auction.shared.dto.ItemResponse;
import vn.edu.auction.shared.dto.TradingProfiles;
import vn.edu.auction.shared.enums.*;

/** In-memory account for reviewing the desktop UI through its normal REST calls. */
public final class PreviewServer {
    private static HttpServer runningServer;
    private static ExecutorService workers;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(PreviewServer::stop, "preview-shutdown"));
    }
    private final List<ItemResponse> items = new ArrayList<>();
    private final List<AuctionResponse> auctions = new ArrayList<>();
    private final List<BidResponse> bids = new ArrayList<>();
    private final List<WalletTxResponse> transactions = new ArrayList<>();
    private final List<ChatMessageResponse> messages = new ArrayList<>();
    private BigDecimal balance = new BigDecimal("2500000");
    private boolean watching, muted, following;
    private String name = "Tài khoản xem thử", phone = "", address = "";
    private TradingProfiles profiles;

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
        auctions.forEach(a -> a.setSellerId(2L));
    }

    public static synchronized int start() throws IOException {
        if (runningServer != null) return runningServer.getAddress().getPort();
        PreviewServer preview = new PreviewServer();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", preview::handle);
        ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "bidora-preview");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        try {
            server.start();
        } catch (RuntimeException ex) {
            server.stop(0);
            executor.shutdownNow();
            throw ex;
        }
        runningServer = server;
        workers = executor;
        return server.getAddress().getPort();
    }

    public static synchronized void stop() {
        if (runningServer != null) {
            runningServer.stop(0);
            runningServer = null;
        }
        if (workers != null) {
            workers.shutdownNow();
            workers = null;
        }
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        JsonNode body = BaseApiClient.MAPPER.readTree(exchange.getRequestBody());
        Object result;
        int status = 200;
        try {
            if (path.equals("/api/items") && method.equals("POST")) {
                ItemResponse item = new ItemResponse((long) items.size() + 100, 1L, body.path("name").asText(),
                        body.path("description").asText(), body.path("startingPrice").decimalValue(), ItemStatus.DRAFT);
                item.setImage(body.path("image").asText(""));
                items.add(item);
                result = item;
            } else if (path.matches("/api/items/\\d+") && method.equals("PUT")) {
                long id = Long.parseLong(path.split("/")[3]);
                ItemResponse item = items.stream().filter(i -> i.getId().equals(id)).findFirst().orElseThrow();
                item.setName(body.path("name").asText());
                item.setDescription(body.path("description").asText());
                item.setStartingPrice(body.path("startingPrice").decimalValue());
                item.setImage(body.path("image").asText(""));
                result = item;
            } else if (path.equals("/api/admin/items") && method.equals("GET")) result = items;
            else if (path.matches("/api/admin/items/\\d+/approve") && method.equals("POST")) {
                long id = Long.parseLong(path.split("/")[4]);
                items.stream().filter(i -> i.getId() == id).findFirst().ifPresent(i -> i.setStatus(ItemStatus.ACTIVE));
                status = 204;
                result = java.util.Map.of();
            } else if (path.matches("/api/admin/items/\\d+/reject") && method.equals("POST")) {
                long id = Long.parseLong(path.split("/")[4]);
                items.stream().filter(i -> i.getId() == id).findFirst().ifPresent(i -> i.setStatus(ItemStatus.REMOVED));
                status = 204;
                result = java.util.Map.of();
            } else if (path.matches("/api/admin/items/\\d+") && method.equals("DELETE")) {
                long id = Long.parseLong(path.split("/")[4]);
                items.stream().filter(i -> i.getId() == id).findFirst().ifPresent(i -> i.setStatus(ItemStatus.REMOVED));
                status = 204;
                result = java.util.Map.of();
            } else if (path.equals("/api/items") && method.equals("GET")) result = items;
            else if (path.startsWith("/api/items/") && method.equals("GET")) {
                long id = Long.parseLong(path.substring("/api/items/".length()));
                result = items.stream().filter(i -> i.getId() == id).findFirst().orElseThrow();
            } else if (path.equals("/api/auctions") && method.equals("POST")) {
                long itemId = body.path("itemId").asLong();
                ItemResponse item = items.stream().filter(i -> i.getId().equals(itemId)).findFirst().orElseThrow();
                if (item.getStatus() != ItemStatus.ACTIVE) {
                    send(exchange, 409, java.util.Map.of("message", "Product must be approved."));
                    return;
                }
                AuctionResponse auction = new AuctionResponse((long) auctions.size() + 100, itemId, item.getName(),
                        item.getStartingPrice(), item.getStartingPrice(), null,
                        LocalDateTime.parse(body.path("startTime").asText()),
                        LocalDateTime.parse(body.path("endTime").asText()), AuctionStatus.RUNNING);
                auction.setImage(item.getImage());
                auction.setSellerId(item.getSellerId());
                auctions.add(auction);
                result = auction;
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
            } else if (path.equals("/api/me/trading-profiles") && method.equals("PUT")) {
                profiles = BaseApiClient.MAPPER.treeToValue(body, TradingProfiles.class);
                result = profiles;
            } else if (path.equals("/api/me/trading-profiles") && method.equals("GET")) result = myTradingProfiles();
            else if (path.matches("/api/users/[12]/profile/(buyer|seller)") && method.equals("GET")) {
                result = path.startsWith("/api/users/1/") ? myTradingProfiles().publicCard(path.endsWith("/seller"))
                        : new TradingProfiles.Card("Người bán mẫu", "", "");
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

    private TradingProfiles myTradingProfiles() {
        if (profiles != null) return profiles;
        var common = new TradingProfiles.Card(name, "", "");
        return new TradingProfiles(false, common, common);
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
