package vn.edu.auction.server.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.auction.server.entity.AuctionEntity;
import vn.edu.auction.server.entity.ItemEntity;
import vn.edu.auction.server.entity.UserEntity;
import vn.edu.auction.server.entity.WalletTransactionEntity;
import vn.edu.auction.server.repository.AuctionRepository;
import vn.edu.auction.server.repository.ItemRepository;
import vn.edu.auction.server.repository.UserRepository;
import vn.edu.auction.server.repository.WalletTransactionRepository;
import vn.edu.auction.shared.dto.ExtraDto.WalletResponse;
import vn.edu.auction.shared.enums.AuctionStatus;
import vn.edu.auction.shared.model.Auction;

/** Người thắng thanh toán phiên: FINISHED -> PAID, chuyển tiền ví từ người thắng sang người bán. */
@Service
@Transactional
public class AuctionPaymentService {

    private final AuctionRepository auctions;
    private final ItemRepository items;
    private final UserRepository users;
    private final WalletTransactionRepository wallets;
    private final SocialService social;
    private final Clock clock;

    public AuctionPaymentService(AuctionRepository auctions, ItemRepository items, UserRepository users,
                                 WalletTransactionRepository wallets, SocialService social,
                                 Clock auctionClock) {
        this.auctions = auctions;
        this.items = items;
        this.users = users;
        this.wallets = wallets;
        this.social = social;
        this.clock = auctionClock;
    }

    public WalletResponse pay(Long auctionId, Long buyerId) {
        AuctionEntity auction = auctions.findByIdForUpdate(auctionId).orElse(null);
        AuctionRules.require(auction != null && !auction.isHidden(),
                HttpStatus.NOT_FOUND, "Không tìm thấy phiên đấu giá.");
        AuctionRules.require(auction.getStatus() == AuctionStatus.FINISHED,
                HttpStatus.CONFLICT, "Phiên chưa kết thúc hoặc đã được xử lý.");
        AuctionRules.require(buyerId.equals(auction.getHighestBidderId()),
                HttpStatus.FORBIDDEN, "Chỉ người thắng cuộc mới được thanh toán.");

        ItemEntity item = items.findById(auction.getItemId()).orElseThrow();
        BigDecimal price = auction.getCurrentPrice();
        Long sellerId = item.getSellerId();

        // Khóa hai ví theo thứ tự id tăng dần để hai giao dịch ngược chiều không deadlock
        UserEntity buyer;
        UserEntity seller;
        if (buyerId < sellerId) {
            buyer = lockUser(buyerId);
            seller = lockUser(sellerId);
        } else {
            seller = lockUser(sellerId);
            buyer = lockUser(buyerId);
        }
        AuctionRules.require(buyer.getWalletBalance().compareTo(price) >= 0,
                HttpStatus.CONFLICT, "Số dư ví không đủ để thanh toán.");

        LocalDateTime now = LocalDateTime.now(clock);
        move(buyer, price.negate(), "AUCTION_PAYMENT", now);
        move(seller, price, "AUCTION_INCOME", now);
        auction.transition(Auction::markPaid);
        social.notify(sellerId, "AUCTION_PAID",
                "Phiên \"" + item.getName() + "\" đã được thanh toán.", auction.getId());
        return new WalletResponse(buyer.getWalletBalance());
    }

    private UserEntity lockUser(Long id) {
        UserEntity user = users.findByIdForUpdate(id).orElse(null);
        AuctionRules.require(user != null, HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản.");
        return user;
    }

    private void move(UserEntity user, BigDecimal delta, String type, LocalDateTime now) {
        BigDecimal after = user.getWalletBalance().add(delta);
        user.setWalletBalance(after);
        WalletTransactionEntity tx = new WalletTransactionEntity();
        tx.userId = user.getId();
        tx.type = type;
        tx.amount = delta.abs();
        tx.balanceAfter = after;
        tx.createdAt = now;
        wallets.save(tx);
    }
}