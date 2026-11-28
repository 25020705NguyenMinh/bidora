package vn.edu.auction.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import vn.edu.auction.server.entity.AuctionEntity;
import vn.edu.auction.server.entity.ItemEntity;
import vn.edu.auction.server.entity.UserEntity;
import vn.edu.auction.server.exception.AuctionBusinessException;
import vn.edu.auction.server.repository.AuctionRepository;
import vn.edu.auction.server.repository.ItemRepository;
import vn.edu.auction.server.repository.UserRepository;
import vn.edu.auction.shared.enums.AuctionStatus;
import vn.edu.auction.shared.enums.ItemStatus;
import vn.edu.auction.shared.enums.UserRole;
import vn.edu.auction.shared.enums.UserStatus;

@SpringBootTest(classes = vn.edu.auction.server.MainApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:auction-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000",
        "spring.datasource.username=sa",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
})
@org.springframework.test.context.ActiveProfiles("test")
class AuctionPaymentServiceTest {

    @Autowired private AuctionPaymentService payments;
    @Autowired private AuctionRepository auctions;
    @Autowired private ItemRepository items;
    @Autowired private UserRepository users;

    private Long sellerId;
    private Long winnerId;
    private Long otherId;
    private Long auctionId;

    @BeforeEach
    void setUp() {
        auctions.deleteAll();
        items.deleteAll();
        users.deleteAll();
        sellerId = saveUser("Seller", "pay-seller@test.local", UserRole.SELLER, "0");
        winnerId = saveUser("Winner", "pay-winner@test.local", UserRole.BIDDER, "1000");
        otherId = saveUser("Other", "pay-other@test.local", UserRole.BIDDER, "1000");

        ItemEntity item = new ItemEntity();
        item.setSellerId(sellerId);
        item.setName("Máy ảnh");
        item.setDescription("");
        item.setStartingPrice(new BigDecimal("100.00"));
        item.setStatus(ItemStatus.SOLD);
        item = items.saveAndFlush(item);

        AuctionEntity auction = new AuctionEntity();
        auction.setItemId(item.getId());
        auction.setCurrentPrice(new BigDecimal("500.00"));
        auction.setHighestBidderId(winnerId);
        auction.setStartTime(LocalDateTime.now().minusHours(2));
        auction.setEndTime(LocalDateTime.now().minusHours(1));
        auction.setStatus(AuctionStatus.FINISHED);
        auctionId = auctions.saveAndFlush(auction).getId();
    }

    private Long saveUser(String name, String email, UserRole role, String balance) {
        UserEntity user = new UserEntity(name, email, "hash", role, UserStatus.ACTIVE);
        user.setWalletBalance(new BigDecimal(balance));
        return users.saveAndFlush(user).getId();
    }

    @Test
    void winnerPaysAndSellerIsCredited() {
        payments.pay(auctionId, winnerId);

        assertEquals(AuctionStatus.PAID, auctions.findById(auctionId).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("500").compareTo(users.findById(winnerId).orElseThrow().getWalletBalance()));
        assertEquals(0, new BigDecimal("500").compareTo(users.findById(sellerId).orElseThrow().getWalletBalance()));
    }

    @Test
    void nonWinnerCannotPay() {
        assertThrows(AuctionBusinessException.class, () -> payments.pay(auctionId, otherId));
        assertEquals(AuctionStatus.FINISHED, auctions.findById(auctionId).orElseThrow().getStatus());
    }

    @Test
    void cannotPayTwice() {
        payments.pay(auctionId, winnerId);
        assertThrows(AuctionBusinessException.class, () -> payments.pay(auctionId, winnerId));
        assertEquals(0, new BigDecimal("500").compareTo(users.findById(winnerId).orElseThrow().getWalletBalance()));
    }

    @Test
    void insufficientBalanceLeavesAuctionFinished() {
        UserEntity winner = users.findById(winnerId).orElseThrow();
        winner.setWalletBalance(new BigDecimal("100"));
        users.saveAndFlush(winner);

        assertThrows(AuctionBusinessException.class, () -> payments.pay(auctionId, winnerId));
        assertEquals(AuctionStatus.FINISHED, auctions.findById(auctionId).orElseThrow().getStatus());
    }
}