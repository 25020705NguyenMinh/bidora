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
import vn.edu.auction.shared.model.Auction;
import vn.edu.auction.shared.model.BidRejectedException;
import vn.edu.auction.server.exception.AuctionBusinessException;


@Service
@Transactional(readOnly = true)
public class BidService {

    private final AuctionRepository auctions;
    private final ItemRepository items;
    private final BidTransactionRepository bids;
    private final AuctionUserService users;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AutoBidService autoBids;

    public BidService(AuctionRepository auctions, ItemRepository items, BidTransactionRepository bids,
                      AuctionUserService users, ApplicationEventPublisher events, Clock auctionClock,AutoBidService autoBids) {
        this.auctions = auctions;
        this.items = items;
        this.bids = bids;
        this.users = users;
        this.events = events;
        this.clock = auctionClock;
        this.autoBids = autoBids;
    }

    @Transactional
    public BidResponse placeBid(Long auctionId, Long bidderId, PlaceBidRequest request) {
        AuctionEntity auction = auctions.findByIdForUpdate(auctionId).orElse(null);
        AuctionRules.require(auction != null && !auction.isHidden(), HttpStatus.NOT_FOUND, "Không tìm thấy phiên đấu giá.");
        users.requireRole(bidderId, UserRole.BIDDER);
        ItemEntity item = items.findById(auction.getItemId()).orElseThrow();
        AuctionRules.require(!item.getSellerId().equals(bidderId), HttpStatus.FORBIDDEN, "Không được đặt giá sản phẩm của mình.");
        LocalDateTime now = LocalDateTime.now(clock);
        
        AuctionRules.require(request != null, HttpStatus.BAD_REQUEST, "Thiếu số tiền đặt giá.");
        BigDecimal amount = AuctionRules.money(request.getAmount());

        Auction domain = new Auction(auction.getId(), auction.getItemId(), auction.getCurrentPrice(),
                auction.getHighestBidderId(), auction.getStartTime(), auction.getEndTime(), auction.getStatus());
        try {
            domain.placeBid(bidderId, amount, now);
        } catch (BidRejectedException e) {
            throw new AuctionBusinessException(HttpStatus.CONFLICT, e.getMessage());
        }
        auction.setEndTime(domain.getEndTime());
        auction.setStatus(domain.getStatus());
        auction.setCurrentPrice(domain.getCurrentPrice());
        auction.setHighestBidderId(domain.getHighestBidderId());

        BidTransactionEntity bid = new BidTransactionEntity();
        bid.setAuctionId(auctionId);
        bid.setBidderId(bidderId);
        bid.setAmount(amount);
        bid.setBidTime(now);
        bid.setStatus(BidStatus.ACCEPTED);
        bids.saveAndFlush(bid);
        // Auto-bid phản ứng ngay trong cùng transaction + cùng khóa phiên
        autoBids.resolve(auction, now);
        // P4 should consume this snapshot with @TransactionalEventListener(AFTER_COMMIT).
        // Phát 1 event với trạng thái CUỐI (sau khi auto-bid chạy xong), không dùng amount của bid tay
        events.publishEvent(new BidAcceptedEvent(auctionId, auction.getCurrentPrice(),
            auction.getHighestBidderId(), auction.getStatus(), auction.getEndTime()));
        return response(bid);
    }

    public List<BidResponse> getBidHistory(Long auctionId) {
        AuctionRules.require(auctions.existsByIdAndHiddenFalse(auctionId), HttpStatus.NOT_FOUND, "Không tìm thấy phiên đấu giá.");
        return bids.findByAuctionIdOrderByBidTimeDescIdDesc(auctionId).stream().map(this::response).toList();
    }

    

    private BidResponse response(BidTransactionEntity bid) {
        return new BidResponse(bid.getId(), bid.getAuctionId(), bid.getBidderId(), bid.getAmount(),
                bid.getBidTime(), bid.getStatus(), "Đặt giá thành công.");
    }
}
