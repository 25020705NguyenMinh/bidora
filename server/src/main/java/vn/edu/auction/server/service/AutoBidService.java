package vn.edu.auction.server.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.auction.server.entity.*;
import vn.edu.auction.server.event.BidAcceptedEvent;
import vn.edu.auction.server.repository.*;
import vn.edu.auction.shared.dto.AutoBidRequest;
import vn.edu.auction.shared.dto.AutoBidResponse;
import vn.edu.auction.shared.enums.*;

@Service
@Transactional
public class AutoBidService {


    /** Ưu tiên: maxBid cao hơn -> đăng ký sớm hơn -> id nhỏ hơn. */
    private static final Comparator<AutoBidEntity> PRIORITY =
            Comparator.comparing(AutoBidEntity::getMaxBid).reversed()
                    .thenComparing(AutoBidEntity::getCreatedAt)
                    .thenComparing(AutoBidEntity::getId);

    private final AuctionRepository auctions;
    private final ItemRepository items;
    private final BidRecorder recorder;
    private final AutoBidRepository autoBids;
    private final AuctionUserService users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AutoBidService(AuctionRepository auctions, ItemRepository items, BidRecorder recorder,
                          AutoBidRepository autoBids, AuctionUserService users,
                          ApplicationEventPublisher events, Clock auctionClock) {
        this.auctions = auctions;
        this.items = items;
        this.recorder = recorder;
        this.autoBids = autoBids;
        this.users = users;
        this.events = events;
        this.clock = auctionClock;
    }

    /** Đăng ký hoặc cập nhật auto-bid của người dùng cho 1 phiên. */
    public AutoBidResponse register(Long auctionId, Long bidderId, AutoBidRequest request) {
        AuctionEntity auction = auctions.findByIdForUpdate(auctionId).orElse(null);
        AuctionRules.require(auction != null && !auction.isHidden(), HttpStatus.NOT_FOUND, "Không tìm thấy phiên đấu giá.");
        users.requireRole(bidderId, UserRole.BIDDER);
        ItemEntity item = items.findById(auction.getItemId()).orElseThrow();
        AuctionRules.require(!item.getSellerId().equals(bidderId), HttpStatus.FORBIDDEN,
                "Không được đấu giá tự động sản phẩm của mình.");
        LocalDateTime now = LocalDateTime.now(clock);
        AuctionRules.require(auction.toDomain().isAcceptingBids(now), HttpStatus.CONFLICT,
                "Phiên chưa mở hoặc đã kết thúc.");
        AuctionRules.require(request != null, HttpStatus.BAD_REQUEST, "Thiếu giá tối đa hoặc bước giá.");
        BigDecimal max = AuctionRules.money(request.getMaxBid());
        BigDecimal inc = AuctionRules.money(request.getIncrement());
        AuctionRules.require(max.compareTo(auction.getCurrentPrice()) > 0, HttpStatus.CONFLICT,
                "Giá tối đa phải cao hơn giá hiện tại.");

        AutoBidEntity ab = autoBids.findByAuctionIdAndBidderId(auctionId, bidderId).orElseGet(AutoBidEntity::new);
        ab.setAuctionId(auctionId);
        ab.setBidderId(bidderId);
        ab.setMaxBid(max);
        ab.setIncrement(inc);
        ab.setCreatedAt(now); // đăng ký lại = tính là đăng ký mới khi phân xử hòa
        ab.setActive(true);
        ab = autoBids.saveAndFlush(ab);

        if (resolve(auction, now)) {
            events.publishEvent(new BidAcceptedEvent(auctionId, auction.getCurrentPrice(),
                    auction.getHighestBidderId(), auction.getStatus(), auction.getEndTime()));
        }
        return response(ab);
    }

    /** Tắt auto-bid của mình. */
    public void cancel(Long auctionId, Long bidderId) {
        auctions.findByIdForUpdate(auctionId).orElse(null); // khóa phiên để không đua với resolve()
        AutoBidEntity ab = autoBids.findByAuctionIdAndBidderId(auctionId, bidderId).orElse(null);
        AuctionRules.require(ab != null && ab.isActive(), HttpStatus.NOT_FOUND,
                "Bạn chưa bật đấu giá tự động cho phiên này.");
        ab.setActive(false);
    }

    /** Trả về null nếu chưa có / đã tắt (client coi là "chưa bật"). */
    @Transactional(readOnly = true)
    public AutoBidResponse getMine(Long auctionId, Long bidderId) {
        return autoBids.findByAuctionIdAndBidderId(auctionId, bidderId)
                .filter(AutoBidEntity::isActive).map(this::response).orElse(null);
    }

    /**
     * Phải gọi khi ĐÃ giữ khóa phiên (findByIdForUpdate) và đang trong transaction.
     * Trả về true nếu có bid tự động được tạo (caller phát event sau đó).
     */
    public boolean resolve(AuctionEntity auction, LocalDateTime now) {
        List<AutoBidEntity> all = autoBids.findByAuctionIdAndActiveTrue(auction.getId());
        if (all.isEmpty()) return false;

        BigDecimal current = auction.getCurrentPrice();
        PriorityQueue<AutoBidEntity> queue = new PriorityQueue<>(PRIORITY);
        for (AutoBidEntity ab : all) {
            if (ab.getMaxBid().compareTo(current) > 0) queue.add(ab);
            else ab.setActive(false); // trần đã bị giá hiện tại vượt qua
        }

        AutoBidEntity top = queue.poll();
        if (top == null) return false;
        AutoBidEntity second = queue.poll();

        if (second == null) {
            // Chỉ còn 1 auto-bid hợp lệ
            if (top.getBidderId().equals(auction.getHighestBidderId())) return false; // đã dẫn đầu rồi
            BigDecimal price = top.getMaxBid().min(current.add(top.getIncrement()));
            placeAutoBid(auction, top.getBidderId(), price, now);
        } else {
            // "second" đẩy giá lên trần, "top" vượt qua thêm đúng 1 bước giá của mình (tối đa = trần của top)
            BigDecimal price = top.getMaxBid().min(second.getMaxBid().add(top.getIncrement()));
            // Hòa trần: price == second.max => chỉ ghi bid của top (người đăng ký sớm), tránh 2 bid cùng số tiền
            if (second.getMaxBid().compareTo(price) < 0) {
                placeAutoBid(auction, second.getBidderId(), second.getMaxBid(), now);
            }
            placeAutoBid(auction, top.getBidderId(), price, now);
        }

        BigDecimal finalPrice = auction.getCurrentPrice();
        for (AutoBidEntity ab : all) {
            if (ab.getMaxBid().compareTo(finalPrice) <= 0) ab.setActive(false); // hết trần
        }
        return true;
    }

    private void placeAutoBid(AuctionEntity auction, Long bidderId, BigDecimal amount, LocalDateTime now) {
        recorder.place(auction, bidderId, amount, now);
    }


    private AutoBidResponse response(AutoBidEntity ab) {
        return new AutoBidResponse(ab.getId(), ab.getAuctionId(), ab.getBidderId(), ab.getMaxBid(),
                ab.getIncrement(), ab.isActive(), ab.getCreatedAt());
    }
}
