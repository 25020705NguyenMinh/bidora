package vn.edu.auction.server.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.auction.server.entity.AuctionEntity;
import vn.edu.auction.server.entity.BidTransactionEntity;
import vn.edu.auction.server.entity.ItemEntity;
import vn.edu.auction.server.entity.UserEntity;
import vn.edu.auction.server.event.BidAcceptedEvent;
import vn.edu.auction.server.repository.AuctionRepository;
import vn.edu.auction.server.repository.BidTransactionRepository;
import vn.edu.auction.server.repository.ItemRepository;
import vn.edu.auction.shared.dto.BidResponse;
import vn.edu.auction.shared.dto.PlaceBidRequest;
import vn.edu.auction.shared.enums.UserRole;

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
    private final BidRecorder recorder;

    public BidService(AuctionRepository auctions, ItemRepository items, BidTransactionRepository bids,
                      AuctionUserService users, ApplicationEventPublisher events, Clock auctionClock,
                      AutoBidService autoBids, BidRecorder recorder) {
        this.auctions = auctions;
        this.items = items;
        this.bids = bids;
        this.users = users;
        this.events = events;
        this.clock = auctionClock;
        this.autoBids = autoBids;
        this.recorder = recorder;
    }

    @Transactional
    public BidResponse placeBid(Long auctionId, Long bidderId, PlaceBidRequest request) {
        AuctionEntity auction = auctions.findByIdForUpdate(auctionId).orElse(null);
        AuctionRules.require(auction != null && !auction.isHidden(),
                HttpStatus.NOT_FOUND, "Không tìm thấy phiên đấu giá.");
        users.requireRole(bidderId, UserRole.BIDDER);
        ItemEntity item = items.findById(auction.getItemId()).orElseThrow();
        AuctionRules.require(!item.getSellerId().equals(bidderId),
                HttpStatus.FORBIDDEN, "Không được đặt giá sản phẩm của mình.");
        AuctionRules.require(request != null, HttpStatus.BAD_REQUEST, "Thiếu số tiền đặt giá.");
        BigDecimal amount = AuctionRules.money(request.getAmount());
        LocalDateTime now = LocalDateTime.now(clock);

        BidTransactionEntity bid = recorder.place(auction, bidderId, amount, now);
        // Auto-bid phản ứng ngay trong cùng transaction + cùng khóa phiên
        autoBids.resolve(auction, now);
        // Phát 1 event với trạng thái CUỐI (sau khi auto-bid chạy xong), không dùng amount của bid tay
        events.publishEvent(new BidAcceptedEvent(auctionId, auction.getCurrentPrice(),
                auction.getHighestBidderId(), auction.getStatus(), auction.getEndTime()));
        return response(bid);
    }

    public List<BidResponse> getBidHistory(Long auctionId) {
        AuctionRules.require(auctions.existsByIdAndHiddenFalse(auctionId),
                HttpStatus.NOT_FOUND, "Không tìm thấy phiên đấu giá.");
        Map<Long, String> names = new HashMap<>(); // mỗi người chỉ tra 1 lần
        return bids.findByAuctionIdOrderByBidTimeDescIdDesc(auctionId).stream().map(b -> {
            BidResponse r = response(b);
            r.setBidderName(names.computeIfAbsent(b.getBidderId(),
                    id -> users.findById(id).map(UserEntity::getFullName).orElse(null)));
            return r;
        }).toList();
    }

    private BidResponse response(BidTransactionEntity bid) {
        return new BidResponse(bid.getId(), bid.getAuctionId(), bid.getBidderId(), bid.getAmount(),
                bid.getBidTime(), bid.getStatus(), "Đặt giá thành công.");
    }
}