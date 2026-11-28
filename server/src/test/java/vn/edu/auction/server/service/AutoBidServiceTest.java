package vn.edu.auction.server.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import vn.edu.auction.server.entity.*;
import vn.edu.auction.server.repository.*;
import vn.edu.auction.shared.dto.AutoBidRequest;
import vn.edu.auction.shared.dto.PlaceBidRequest;
import vn.edu.auction.shared.enums.*;

@SpringBootTest(classes = vn.edu.auction.server.MainApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:auto-bid-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000",
        "spring.datasource.username=sa",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
})
@org.springframework.test.context.ActiveProfiles("test")
class AutoBidServiceTest {
    @Autowired private BidService bidService;
    @Autowired private AutoBidService autoBidService;
    @Autowired private AuctionRepository auctions;
    @Autowired private BidTransactionRepository bids;
    @Autowired private AutoBidRepository autoBids;
    @Autowired private ItemRepository items;
    @Autowired private UserRepository users;

    private Long auctionId;
    private Long a;
    private Long b;

    @BeforeEach
    void setUp() {
        autoBids.deleteAll(); bids.deleteAll(); auctions.deleteAll(); items.deleteAll(); users.deleteAll();
        Long owner = users.saveAndFlush(new UserEntity("Owner", "owner@t.local", "h", UserRole.SELLER, UserStatus.ACTIVE)).getId();
        a = users.saveAndFlush(new UserEntity("A", "a@t.local", "h", UserRole.BIDDER, UserStatus.ACTIVE)).getId();
        b = users.saveAndFlush(new UserEntity("B", "b@t.local", "h", UserRole.BIDDER, UserStatus.ACTIVE)).getId();
        ItemEntity item = new ItemEntity();
        item.setSellerId(owner); item.setName("Auto-bid test"); item.setDescription("");
        item.setStartingPrice(new BigDecimal("100.00")); item.setStatus(ItemStatus.ACTIVE);
        item = items.saveAndFlush(item);
        AuctionEntity auction = new AuctionEntity();
        auction.setItemId(item.getId()); auction.setCurrentPrice(new BigDecimal("100.00"));
        auction.setStatus(AuctionStatus.RUNNING);
        auction.setStartTime(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        auction.setEndTime(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        auctionId = auctions.saveAndFlush(auction).getId();
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private void assertState(String price, Long leader) {
        AuctionEntity au = auctions.findById(auctionId).orElseThrow();
        assertEquals(0, au.getCurrentPrice().compareTo(bd(price)), "giá hiện tại");
        assertEquals(leader, au.getHighestBidderId(), "người dẫn đầu");
    }

    @Test
    void loneAutoBidderStartsAtOneIncrementAndAnswersManualBids() {
        autoBidService.register(auctionId, a, new AutoBidRequest(bd("1000"), bd("50")));
        assertState("150.00", a);
        bidService.placeBid(auctionId, b, new PlaceBidRequest(bd("200")));
        assertState("250.00", a); // 200 + bước 50
    }

    @Test
    void higherMaxWinsAtSecondMaxPlusIncrement() {
        autoBidService.register(auctionId, a, new AutoBidRequest(bd("1000"), bd("50")));
        autoBidService.register(auctionId, b, new AutoBidRequest(bd("800"), bd("20")));
        assertState("850.00", a); // min(1000, 800 + 50)
        List<BidTransactionEntity> history = bids.findByAuctionIdOrderByBidTimeDescIdDesc(auctionId);
        assertTrue(history.stream().anyMatch(x -> x.getBidderId().equals(b) && x.getAmount().compareTo(bd("800")) == 0));
        assertFalse(autoBids.findByAuctionIdAndBidderId(auctionId, b).orElseThrow().isActive());
        assertTrue(autoBids.findByAuctionIdAndBidderId(auctionId, a).orElseThrow().isActive());
    }

    @Test
    void equalMaxGoesToEarlierRegistrationWithSingleBidAtMax() {
        autoBidService.register(auctionId, a, new AutoBidRequest(bd("500"), bd("10")));
        autoBidService.register(auctionId, b, new AutoBidRequest(bd("500"), bd("10")));
        assertState("500.00", a);
        long bidsAt500 = bids.findByAuctionIdOrderByBidTimeDescIdDesc(auctionId).stream()
                .filter(x -> x.getAmount().compareTo(bd("500")) == 0).count();
        assertEquals(1, bidsAt500);
    }

    @Test
    void manualBidAboveMaxDeactivatesAutoBid() {
        autoBidService.register(auctionId, a, new AutoBidRequest(bd("300"), bd("50")));
        bidService.placeBid(auctionId, b, new PlaceBidRequest(bd("400")));
        assertState("400.00", b);
        assertFalse(autoBids.findByAuctionIdAndBidderId(auctionId, a).orElseThrow().isActive());
    }
}