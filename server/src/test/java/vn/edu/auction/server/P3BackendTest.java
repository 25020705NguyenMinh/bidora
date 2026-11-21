package vn.edu.auction.server;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.*;
import org.springframework.transaction.event.*;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.auction.server.config.SecurityConfig;
import vn.edu.auction.server.controller.*;
import vn.edu.auction.server.entity.*;
import vn.edu.auction.server.event.AuctionEventPublisher;
import vn.edu.auction.server.event.BidAcceptedEvent;
import vn.edu.auction.server.event.UserSocketHandler;
import vn.edu.auction.server.exception.*;
import vn.edu.auction.server.repository.*;
import vn.edu.auction.server.security.JwtService;
import vn.edu.auction.server.service.*;
import vn.edu.auction.shared.dto.*;
import vn.edu.auction.shared.enums.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = P3BackendTest.TestApp.class, properties = {
        "spring.datasource.url=${P3_TEST_DB_URL:jdbc:h2:mem:p3;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000}",
        "spring.datasource.username=${P3_TEST_DB_USER:sa}", "spring.datasource.password=${P3_TEST_DB_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.security.oauth2.client.registration.google.client-id=p3-test",
        "spring.security.oauth2.client.registration.google.client-secret=p3-test",
        "MAIL_PASSWORD=p3-test",
})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class P3BackendTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("vn.edu.auction.server.entity")
    @EnableJpaRepositories("vn.edu.auction.server.repository")
    @Import({ItemService.class, AuctionService.class, BidService.class, AuctionUserService.class,
            SocialService.class, UserSocketHandler.class, AuctionEventPublisher.class, AuctionClosingService.class, AuctionActivationService.class,
            ItemController.class, AuctionController.class, BidController.class, TradingProfileService.class,
            TradingProfileController.class, AuthService.class, OtpService.class, MailService.class, AuthController.class,
            AuctionApiExceptionHandler.class, GlobalExceptionHandler.class, SecurityConfig.class, JwtService.class})
    static class TestApp {
        @Bean MutableClock auctionClock() { return new MutableClock(); }
        @Bean EventCollector eventCollector() { return new EventCollector(); }
        @Bean TestMailbox testMailbox() { return new TestMailbox(); }
    }

    static class TestMailbox extends org.springframework.mail.javamail.JavaMailSenderImpl {
        boolean unavailable;
        String code;
        @Override public void send(org.springframework.mail.SimpleMailMessage message) {
            if (unavailable) throw new org.springframework.mail.MailSendException("Test transport unavailable");
            var matcher = java.util.regex.Pattern.compile("[0-9]{6}").matcher(message.getText());
            assertTrue(matcher.find());
            code = matcher.group();
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2030-01-01T12:00:00Z");
        void set(LocalDateTime value) { now = value.toInstant(ZoneOffset.UTC); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static class EventCollector {
        final List<BidAcceptedEvent> events = new CopyOnWriteArrayList<>();
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void accepted(BidAcceptedEvent event) { events.add(event); }
    }

    @Test void productImagePersistsAndInvalidUploadIsRejected() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16, 16,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        String image = Base64.getEncoder().encodeToString(bytes.toByteArray());
        ItemRequest request = new ItemRequest("Watch", "Uploaded photo", BigDecimal.TEN);
        request.setImage(image);
        ItemResponse saved = itemService.createItem(request, seller);
        assertEquals(image, itemService.getItemById(saved.getId(), seller).getImage());
        ItemRequest legacy = new ItemRequest("Watch", "Updated", BigDecimal.TEN);
        assertEquals(image, itemService.updateItem(saved.getId(), legacy, seller).getImage());
        request.setImage("invalid");
        assertThrows(AuctionBusinessException.class, () -> itemService.updateItem(saved.getId(), request, seller));
        assertEquals(image, itemService.getItemById(saved.getId(), seller).getImage());
        request.setImage("");
        assertEquals("", itemService.updateItem(saved.getId(), request, seller).getImage());
    }

    @Autowired ItemService itemService;
    @Autowired AuctionService auctionService;
    @Autowired BidService bidService;
    @Autowired ItemRepository items;
    @Autowired AuctionRepository auctions;
    @Autowired BidTransactionRepository bids;
    @Autowired UserRepository users;
    @Autowired MutableClock clock;
    @Autowired EventCollector collector;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired AuctionClosingService closing;
    @Autowired AuctionActivationService activation;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired TestMailbox mailbox;
    @Autowired TradingProfileService trading;
    Long seller;
    Long bidder;
    Long otherBidder;
    LocalDateTime now;

    @BeforeEach
    void prepare() {
        mailbox.unavailable = false;
        bids.deleteAll(); auctions.deleteAll(); items.deleteAll(); users.deleteAll();
        collector.events.clear();
        now = LocalDateTime.of(2030, 1, 1, 12, 0);
        clock.set(now);
        seller = user("seller", UserRole.SELLER);
        bidder = user("bidder", UserRole.BIDDER);
        otherBidder = user("other", UserRole.BIDDER);
    }

    Long user(String name, UserRole role) {
        return users.save(new UserEntity(name, name + "@test.local", "hash", role, UserStatus.ACTIVE)).getId();
    }

    /** Admin phê duyệt: DRAFT -> ACTIVE (createAuction chỉ nhận sản phẩm đã duyệt). */
    void approve(Long itemId) {
        ItemEntity item = items.findById(itemId).orElseThrow();
        item.setStatus(ItemStatus.ACTIVE);
        items.save(item);
    }

    AuctionResponse auction(int secondsLeft) {
        ItemResponse item = itemService.createItem(new ItemRequest("Camera", "Test item", new BigDecimal("100.00")), seller);
        approve(item.getId());
        return auctionService.createAuction(new AuctionRequest(item.getId(), now.minusMinutes(1), now.plusSeconds(secondsLeft)), seller);
    }

    BidResponse bid(Long id, Long userId, String amount) {
        return bidService.placeBid(id, userId, new PlaceBidRequest(new BigDecimal(amount)));
    }

    @Test void itemCrudAndOwnerChecks() {
        ItemResponse item = itemService.createItem(new ItemRequest("Camera", "", new BigDecimal("100")), seller);
        assertEquals(1, itemService.getAllItems(seller).size());
        Long anotherSeller = user("seller2", UserRole.SELLER);
        assertThrows(AuctionBusinessException.class, () -> itemService.updateItem(item.getId(), new ItemRequest("Other", "", BigDecimal.TEN), anotherSeller));
        assertEquals("Updated", itemService.updateItem(item.getId(), new ItemRequest("Updated", "", BigDecimal.TEN), seller).getName());
        itemService.deleteItem(item.getId(), seller);
        assertTrue(itemService.getAllItems(seller).isEmpty());
        assertThrows(AuctionBusinessException.class, () -> itemService.getItemById(item.getId(), seller));
    }

    @Test void rejectsInvalidMoneyAndNames() {
        for (String amount : List.of("0", "-1", "1.001", "100000000000000000")) {
            assertThrows(AuctionBusinessException.class, () -> itemService.createItem(new ItemRequest("Item", "", new BigDecimal(amount)), seller));
        }
        assertThrows(AuctionBusinessException.class, () -> itemService.createItem(new ItemRequest(" ", "", BigDecimal.TEN), seller));
    }

    @Test void adminTradesUsingTheSameAccountId() {
        Long admin = user("admin", UserRole.ADMIN);
        var own = itemService.createItem(new ItemRequest("Item", "", BigDecimal.TEN), admin);
        approve(own.getId());
        var sale = auctionService.createAuction(new AuctionRequest(own.getId(), now, now.plusHours(1)), admin);
        assertThrows(AuctionBusinessException.class, () -> bid(sale.getId(), admin, "20"));
        assertEquals(admin, bid(auction(300).getId(), admin, "200").getBidderId());
    }

    @Test void ordinaryAccountCanSellAndBidButCannotBidOnOwnItem() {
        ItemResponse item = itemService.createItem(new ItemRequest("Bidder's item", "", BigDecimal.TEN), bidder);
        approve(item.getId());
        AuctionResponse own = auctionService.createAuction(
                new AuctionRequest(item.getId(), now.minusMinutes(1), now.plusMinutes(5)), bidder);
        assertThrows(AuctionBusinessException.class, () -> bid(own.getId(), bidder, "20"));
        bid(own.getId(), seller, "20");
        assertEquals(seller, auctionService.getAuctionById(own.getId()).getHighestBidderId());
        UserEntity disabled = users.findById(otherBidder).orElseThrow();
        disabled.setStatus(UserStatus.INACTIVE); users.save(disabled);
        assertThrows(AuctionBusinessException.class, () -> itemService.createItem(
                new ItemRequest("Blocked", "", BigDecimal.TEN), otherBidder));
    }

    @Test void auctionCreationAndProductProtection() {
        AuctionResponse auction = auction(300);
        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
        assertEquals(1, auctionService.getAllAuctions().size());
        assertEquals(auction.getId(), auctionService.getAuctionById(auction.getId()).getId());
        assertThrows(AuctionBusinessException.class, () -> itemService.deleteItem(auction.getItemId(), seller));
        assertThrows(AuctionBusinessException.class, () -> itemService.updateItem(auction.getItemId(), new ItemRequest("Changed", "", BigDecimal.ONE), seller));
        assertThrows(AuctionBusinessException.class, () -> auctionService.createAuction(new AuctionRequest(auction.getItemId(), now, now.plusMinutes(1)), seller));
    }

    @Test void acceptsHigherBidAndPersistsHistory() {
        AuctionResponse auction = auction(300);
        bid(auction.getId(), bidder, "120");
        bid(auction.getId(), otherBidder, "150");
        AuctionResponse result = auctionService.getAuctionById(auction.getId());
        assertEquals(new BigDecimal("150.00"), result.getCurrentPrice());
        assertEquals(otherBidder, result.getHighestBidderId());
        assertEquals(auction.getEndTime(), result.getEndTime());
        assertEquals(2, bidService.getBidHistory(auction.getId()).size());
        assertEquals(new BigDecimal("150.00"), bidService.getBidHistory(auction.getId()).get(0).getAmount());
        assertEquals(2, collector.events.size());
    }

    @Test void rejectsLowPricesWithoutExtendingOrPublishing() {
        AuctionResponse auction = auction(5);
        assertThrows(AuctionBusinessException.class, () -> bid(auction.getId(), bidder, "100"));
        assertThrows(AuctionBusinessException.class, () -> bid(auction.getId(), bidder, "99"));
        assertEquals(auction.getEndTime(), auctionService.getAuctionById(auction.getId()).getEndTime());
        assertEquals(0, bids.count());
        assertTrue(collector.events.isEmpty());
    }

    @Test void extendsRepeatedlyAtTenSecondBoundary() {
        AuctionResponse auction = auction(10);
        bid(auction.getId(), bidder, "110");
        LocalDateTime firstEnd = auction.getEndTime().plusSeconds(60);
        assertEquals(firstEnd, auctionService.getAuctionById(auction.getId()).getEndTime());
        clock.set(firstEnd.minusSeconds(5));
        bid(auction.getId(), otherBidder, "120");
        assertEquals(firstEnd.plusSeconds(60), auctionService.getAuctionById(auction.getId()).getEndTime());
        assertEquals(firstEnd.plusSeconds(60), collector.events.get(1).endTime());
    }

    @Test void doesNotExtendBeforeWindow() {
        AuctionResponse auction = auction(11);
        bid(auction.getId(), bidder, "110");
        assertEquals(auction.getEndTime(), auctionService.getAuctionById(auction.getId()).getEndTime());
    }

    @Test void secondBidUsesExtendedDeadline() {
        AuctionResponse auction = auction(5);
        bid(auction.getId(), bidder, "110");
        bid(auction.getId(), otherBidder, "120");
        assertEquals(auction.getEndTime().plusSeconds(60), auctionService.getAuctionById(auction.getId()).getEndTime());
    }

    @Test void rejectsExpiredAndFinishedAuctions() {
        AuctionResponse auction = auction(20);
        clock.set(auction.getEndTime());
        assertThrows(AuctionBusinessException.class, () -> bid(auction.getId(), bidder, "110"));
        clock.set(now);
        AuctionEntity entity = auctions.findById(auction.getId()).orElseThrow();
        entity.setStatus(AuctionStatus.FINISHED); auctions.save(entity);
        assertThrows(AuctionBusinessException.class, () -> bid(auction.getId(), bidder, "110"));
        assertEquals(0, bids.count());
    }

    @Test void scheduledAuctionStartsOnFirstEligibleBid() {
        ItemResponse item = itemService.createItem(new ItemRequest("Item", "", BigDecimal.TEN), seller);
        approve(item.getId());
        AuctionResponse auction = auctionService.createAuction(new AuctionRequest(item.getId(), now.plusMinutes(1), now.plusMinutes(5)), seller);
        assertEquals(AuctionStatus.OPEN, auction.getStatus());
        assertThrows(AuctionBusinessException.class, () -> bid(auction.getId(), bidder, "20"));
        clock.set(now.plusMinutes(1));
        bid(auction.getId(), bidder, "20");
        assertEquals(AuctionStatus.RUNNING, auctionService.getAuctionById(auction.getId()).getStatus());
    }

    @Test void schedulerActivatesScheduledAuctionExactlyOnceWhenDue() {
        ItemResponse item = itemService.createItem(new ItemRequest("Item", "", BigDecimal.TEN), seller);
        approve(item.getId());
        AuctionResponse auction = auctionService.createAuction(new AuctionRequest(item.getId(), now.plusMinutes(5), now.plusMinutes(15)), seller);
        assertEquals(AuctionStatus.OPEN, auction.getStatus());
        assertEquals(0, activation.activateDueAuctions()); // chưa tới giờ
        clock.set(now.plusMinutes(5).minusSeconds(1));
        assertEquals(0, activation.activateDueAuctions());
        clock.set(now.plusMinutes(5));
        assertEquals(1, activation.activateDueAuctions()); // đúng giờ
        assertEquals(AuctionStatus.RUNNING, auctionService.getAuctionById(auction.getId()).getStatus());
        assertEquals(0, activation.activateDueAuctions()); // idempotent
        assertEquals(now.plusMinutes(15), auctionService.getAuctionById(auction.getId()).getEndTime());
    }

    @Test void rejectsStartTimeInThePast() {
        ItemResponse item = itemService.createItem(new ItemRequest("Item", "", BigDecimal.TEN), seller);
        approve(item.getId());
        assertThrows(AuctionBusinessException.class, () -> auctionService.createAuction(
                new AuctionRequest(item.getId(), now.minusMinutes(5), now.plusMinutes(5)), seller));
    }

    @Test void rejectsInactiveBidderAndSelfBidding() {
        AuctionResponse auction = auction(300);
        UserEntity user = users.findById(bidder).orElseThrow();
        user.setStatus(UserStatus.BANNED); users.save(user);
        assertThrows(AuctionBusinessException.class, () -> bid(auction.getId(), bidder, "110"));
        UserEntity owner = users.findById(seller).orElseThrow();
        owner.setRole(UserRole.BIDDER); users.save(owner);
        assertThrows(AuctionBusinessException.class, () -> bid(auction.getId(), seller, "110"));
    }

    @Test void rollbackRevertsBidPriceExtensionAndAfterCommitEvent() {
        AuctionResponse auction = auction(5);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            bid(auction.getId(), bidder, "110");
            assertTrue(collector.events.isEmpty());
            throw new IllegalStateException("Simulate failure before commit");
        }));
        AuctionResponse result = auctionService.getAuctionById(auction.getId());
        assertEquals(new BigDecimal("100.00"), result.getCurrentPrice());
        assertNull(result.getHighestBidderId());
        assertEquals(auction.getEndTime(), result.getEndTime());
        assertEquals(0, bids.count());
        assertTrue(collector.events.isEmpty());
    }

    @Test void simultaneousBidsKeepMaximumPriceAndOneLeader() throws Exception {
        AuctionResponse auction = auction(300);
        int requests = 12;
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        AtomicInteger accepted = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 1; i <= requests; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        bid(auction.getId(), index % 2 == 0 ? bidder : otherBidder, String.valueOf(100 + index));
                        accepted.incrementAndGet();
                    } catch (AuctionBusinessException ex) {
                        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatus());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt(); throw new RuntimeException(ex);
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        AuctionResponse result = auctionService.getAuctionById(auction.getId());
        assertEquals(new BigDecimal("112.00"), result.getCurrentPrice());
        assertEquals(bidder, result.getHighestBidderId());
        assertEquals(accepted.get(), bids.count());
        assertEquals(accepted.get(), collector.events.size());
        List<BidResponse> history = bidService.getBidHistory(auction.getId());
        assertEquals(result.getCurrentPrice(), history.get(0).getAmount());
    }

    @Test void guestCanReadVisibleAuctionsButCannotBidOrReadHiddenAuctions() throws Exception {
        AuctionResponse auction = auction(300);
        String url = "/api/auctions/" + auction.getId();
        mvc.perform(get("/api/auctions")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(auction.getId()));
        mvc.perform(get(url)).andExpect(status().isOk());
        mvc.perform(get(url + "/bids")).andExpect(status().isOk());
        mvc.perform(post(url + "/bids").contentType("application/json").content("{\"amount\":200}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(url + "/watch")).andExpect(status().isUnauthorized());
        var hidden = auctions.findById(auction.getId()).orElseThrow();
        hidden.setHidden(true);
        auctions.saveAndFlush(hidden);
        mvc.perform(get("/api/auctions")).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get(url + "/bids")).andExpect(status().isNotFound());
    }

    @Test void apiUsesJwtIdentityAndKeepsExistingGuestRestriction() throws Exception {
        String sellerToken = jwt.generateToken(users.findById(seller).orElseThrow());
        String bidderToken = jwt.generateToken(users.findById(bidder).orElseThrow());
        mvc.perform(get("/api/items")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/items").header("Authorization", "Bearer " + sellerToken)
                .contentType("application/json").content("{\"name\":\"API item\",\"startingPrice\":100,\"sellerId\":999}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sellerId").value(seller));
        AuctionResponse auction = auction(300);
        mvc.perform(post("/api/auctions/" + auction.getId() + "/bids")
                .header("Authorization", "Bearer " + bidderToken).contentType("application/json")
                .content("{\"amount\":200,\"bidderId\":999}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.bidderId").value(bidder));
        mvc.perform(post("/api/auctions/" + auction.getId() + "/bids")
                .header("Authorization", "Bearer " + bidderToken).contentType("application/json").content("{\"amount\":200}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/api/auctions/999999").header("Authorization", "Bearer " + bidderToken))
                .andExpect(status().isNotFound());
    }

    @Test void existingJwtCannotAccessApiAfterAccountIsBanned() throws Exception {
        String token = jwt.generateToken(users.findById(bidder).orElseThrow());
        mvc.perform(get("/api/items").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        UserEntity user = users.findById(bidder).orElseThrow();
        user.setStatus(UserStatus.BANNED); users.save(user);
        mvc.perform(get("/api/items").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test void itemDetailUsesTheSameReviewVisibilityAsTheList() throws Exception {
        ItemResponse item = itemService.createItem(new ItemRequest("Camera", "", BigDecimal.TEN), seller);
        String url = "/api/items/" + item.getId();
        Long admin = user("reviewer", UserRole.ADMIN);
        for (Long viewer : List.of(seller, admin)) {
            String token = jwt.generateToken(users.findById(viewer).orElseThrow());
            mvc.perform(get(url).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(item.getId()));
        }
        String token = jwt.generateToken(users.findById(bidder).orElseThrow());
        mvc.perform(get("/api/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(url).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        approve(item.getId());
        mvc.perform(get(url).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test void onlyMaterialEditsRequireAnotherReview() {
        ItemResponse item = itemService.createItem(new ItemRequest("Camera", "", BigDecimal.TEN), seller);
        approve(item.getId());
        assertEquals(ItemStatus.ACTIVE, itemService.updateItem(item.getId(),
                new ItemRequest(" Camera ", null, new BigDecimal("10.00")), seller).getStatus());
        assertEquals(ItemStatus.DRAFT, itemService.updateItem(item.getId(),
                new ItemRequest("Camera", "", new BigDecimal("20")), seller).getStatus());
        assertThrows(AuctionBusinessException.class, () -> auctionService.createAuction(
                new AuctionRequest(item.getId(), now, now.plusHours(1)), seller));
        approve(item.getId());
        assertEquals(AuctionStatus.RUNNING, auctionService.createAuction(
                new AuctionRequest(item.getId(), now, now.plusHours(1)), seller).getStatus());
    }

    @Test void hidingAnAuctionStopsBiddingAndUnhidingRestoresIt() {
        AuctionResponse auction = auction(300);
        AuctionEntity entity = auctions.findById(auction.getId()).orElseThrow();
        entity.setHidden(true);
        auctions.saveAndFlush(entity);
        var error = assertThrows(AuctionBusinessException.class, () -> bid(auction.getId(), bidder, "150"));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, error.getStatus());
        assertEquals(0, bids.countByAuctionId(auction.getId()));
        assertTrue(collector.events.isEmpty());
        entity.setHidden(false);
        auctions.saveAndFlush(entity);
        bid(auction.getId(), bidder, "150");
        assertEquals(1, bids.countByAuctionId(auction.getId()));
    }

    @Test void registerRetriesAfterMailFailureAndRecoversPasswordWithOtp() throws Exception {
        String info = "{\"email\":\"register@example.test\",\"fullName\":\"An\",\"password\":\"original123\"}";
        mailbox.unavailable = true;
        mvc.perform(post("/api/auth/register").contentType("application/json").content(info))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("X-Bidora-Error", "OTP_DELIVERY_FAILED"));
        assertFalse(users.existsByEmail("register@example.test"));
        mailbox.unavailable = false;
        mvc.perform(post("/api/auth/register").contentType("application/json").content(info))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("BIDDER"));
        String email = "\"email\":\"register@example.test\"";
        mvc.perform(post("/api/auth/verify-email").contentType("application/json")
                .content("{" + email + ",\"otp\":\"" + mailbox.code + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{" + email + "}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content("{" + email + ",\"otp\":\"" + mailbox.code + "\",\"newPassword\":\"changed123\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType("application/json").content(info)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{" + email + ",\"password\":\"changed123\"}"))
                .andExpect(status().isOk());
    }

    @Test void splitProfilesShareOneOwnerAndExposeOnlyTheSelectedPublicCard() throws Exception {
        var common = trading.get(bidder);
        assertEquals(common.publicCard(false), common.publicCard(true));
        var buyerCard = new TradingProfiles.Card("Người mua", "Sưu tầm máy ảnh", "");
        var pixels = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(pixels, "png", png);
        String avatar = Base64.getEncoder().encodeToString(png.toByteArray());
        var sellerCard = new TradingProfiles.Card("Cửa hàng", "Bán máy ảnh", avatar);
        trading.save(bidder, new TradingProfiles(true, buyerCard, sellerCard));
        String url = "/api/users/" + bidder + "/profile/";
        mvc.perform(get(url + "seller")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Cửa hàng"))
                .andExpect(jsonPath("$.avatar").value(avatar)).andExpect(jsonPath("$.email").doesNotExist());
        assertEquals("Người mua", users.directory(seller).stream().filter(u -> u.id().equals(bidder))
                .findFirst().orElseThrow().fullName());
        mvc.perform(get(url + "buyer")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Người mua"));
        mvc.perform(get("/api/me/trading-profiles")).andExpect(status().isUnauthorized());
        var item = itemService.createItem(new ItemRequest("Camera", "", BigDecimal.TEN), bidder);
        approve(item.getId());
        var sale = auctionService.createAuction(new AuctionRequest(item.getId(), now, now.plusHours(1)), bidder);
        assertEquals(bidder, sale.getSellerId());
        assertThrows(AuctionBusinessException.class, () -> bid(sale.getId(), bidder, "20"));
        trading.save(bidder, new TradingProfiles(false, buyerCard, sellerCard));
        mvc.perform(get(url + "seller")).andExpect(jsonPath("$.name").value("Người mua"));
        assertThrows(AuctionBusinessException.class, () -> trading.save(bidder, new TradingProfiles(true, buyerCard,
                new TradingProfiles.Card("Bad", "", "not-an-image"))));
        assertEquals(buyerCard, trading.get(bidder).publicCard(true));
    }

    @Test void rejectsAuctionForUnapprovedItem() {
        ItemResponse item = itemService.createItem(new ItemRequest("Draft", "", BigDecimal.TEN), seller); // DRAFT = chờ duyệt
        assertThrows(AuctionBusinessException.class, () -> auctionService.createAuction(
                new AuctionRequest(item.getId(), now.minusMinutes(1), now.plusMinutes(5)), seller));
        approve(item.getId());
        assertEquals(AuctionStatus.RUNNING, auctionService.createAuction(
                new AuctionRequest(item.getId(), now.minusMinutes(1), now.plusMinutes(5)), seller).getStatus());
    }

    @Test void closesExpiredAuctionAndPicksWinnerOnce() {
        AuctionResponse a = auction(60);
        bid(a.getId(), bidder, "150");
        bid(a.getId(), otherBidder, "200");
        clock.set(now.plusSeconds(61));
        assertEquals(1, closing.closeExpiredAuctions());
        AuctionResponse done = auctionService.getAuctionById(a.getId());
        assertEquals(AuctionStatus.FINISHED, done.getStatus());
        assertEquals(otherBidder, done.getHighestBidderId());
        assertEquals(0, new BigDecimal("200").compareTo(done.getCurrentPrice()));
        assertEquals(ItemStatus.SOLD, items.findById(done.getItemId()).orElseThrow().getStatus());
        assertEquals(0, closing.closeExpiredAuctions()); // đóng lần 2 không có tác dụng
        assertThrows(AuctionBusinessException.class, () -> bid(a.getId(), bidder, "300")); // hết hạn không nhận bid
    }

    @Test void closesAuctionWithoutBidsHasNoWinner() {
        AuctionResponse a = auction(30);
        clock.set(now.plusSeconds(31));
        assertEquals(1, closing.closeExpiredAuctions());
        AuctionResponse done = auctionService.getAuctionById(a.getId());
        assertEquals(AuctionStatus.FINISHED, done.getStatus());
        assertNull(done.getHighestBidderId());
    }

    @Test void doesNotCloseBeforeEndTime() {
        AuctionResponse a = auction(300);
        clock.set(now.plusSeconds(299));
        assertEquals(0, closing.closeExpiredAuctions());
        assertEquals(AuctionStatus.RUNNING, auctionService.getAuctionById(a.getId()).getStatus());
    }
}
