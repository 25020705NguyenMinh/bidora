package vn.edu.auction.server.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.auction.server.entity.*;
import vn.edu.auction.server.event.BidAcceptedEvent;
import vn.edu.auction.server.repository.*;
import vn.edu.auction.shared.dto.*;
import vn.edu.auction.shared.enums.*;

@Service
@Transactional(readOnly = true)
public class BidService {
    private static final long SNIPE_WINDOW_SECONDS = 10;
    private static final long EXTENSION_SECONDS = 60;

    private final AuctionRepository auctions;
    private final ItemRepository items;
    private final BidTransactionRepository bids;
    private final AuctionUserService users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BidService(AuctionRepository auctions, ItemRepository items, BidTransactionRepository bids,
                      AuctionUserService users, ApplicationEventPublisher events, Clock auctionClock) {
        this.auctions = auctions;
        this.items = items;
        this.bids = bids;
        this.users = users;
        this.events = events;
        this.clock = auctionClock;
    }

    @Transactional
    public BidResponse placeBid(Long auctionId, Long bidderId, PlaceBidRequest request) {
        AuctionEntity auction = auctions.findByIdForUpdate(auctionId).orElse(null);
        AuctionRules.require(auction != null && !auction.isHidden(), HttpStatus.NOT_FOUND, "Không tìm thấy phiên đấu giá.");
        users.requireRole(bidderId, UserRole.BIDDER);
        ItemEntity item = items.findById(auction.getItemId()).orElseThrow();
        AuctionRules.require(!item.getSellerId().equals(bidderId), HttpStatus.FORBIDDEN, "Không được đặt giá sản phẩm của mình.");
        LocalDateTime now = LocalDateTime.now(clock);
        requireOpen(auction, now);
        AuctionRules.require(request != null, HttpStatus.BAD_REQUEST, "Thiếu số tiền đặt giá.");
        BigDecimal amount = AuctionRules.money(request.getAmount());
        AuctionRules.require(amount.compareTo(auction.getCurrentPrice()) > 0, HttpStatus.CONFLICT, "Giá mới phải cao hơn giá hiện tại.");

        if (!now.isBefore(auction.getEndTime().minusSeconds(SNIPE_WINDOW_SECONDS))) {
            auction.setEndTime(auction.getEndTime().plusSeconds(EXTENSION_SECONDS));
        }
        auction.setStatus(AuctionStatus.RUNNING);
        auction.setCurrentPrice(amount);
        auction.setHighestBidderId(bidderId);
        BidTransactionEntity bid = new BidTransactionEntity();
        bid.setAuctionId(auctionId);
        bid.setBidderId(bidderId);
        bid.setAmount(amount);
        bid.setBidTime(now);
        bid.setStatus(BidStatus.ACCEPTED);
        bids.saveAndFlush(bid);
        // P4 should consume this snapshot with @TransactionalEventListener(AFTER_COMMIT).
        events.publishEvent(new BidAcceptedEvent(auctionId, amount, bidderId, auction.getStatus(), auction.getEndTime()));
        return response(bid);
    }

    public List<BidResponse> getBidHistory(Long auctionId) {
        AuctionRules.require(auctions.existsByIdAndHiddenFalse(auctionId), HttpStatus.NOT_FOUND, "Không tìm thấy phiên đấu giá.");
        return bids.findByAuctionIdOrderByBidTimeDescIdDesc(auctionId).stream().map(this::response).toList();
    }

    private void requireOpen(AuctionEntity auction, LocalDateTime now) {
        boolean activeStatus = auction.getStatus() == AuctionStatus.RUNNING || auction.getStatus() == AuctionStatus.OPEN;
        AuctionRules.require(activeStatus && !now.isBefore(auction.getStartTime()) && now.isBefore(auction.getEndTime()),
                HttpStatus.CONFLICT, "Phiên chưa mở hoặc đã kết thúc.");
    }

    private BidResponse response(BidTransactionEntity bid) {
        return new BidResponse(bid.getId(), bid.getAuctionId(), bid.getBidderId(), bid.getAmount(),
                bid.getBidTime(), bid.getStatus(), "Đặt giá thành công.");
    }
}
