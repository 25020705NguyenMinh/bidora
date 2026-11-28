package vn.edu.auction.server.service;

import java.time.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.auction.server.entity.*;
import vn.edu.auction.server.repository.*;
import vn.edu.auction.shared.dto.*;
import vn.edu.auction.shared.enums.*;

@Service
@Transactional(readOnly = true)
public class AuctionService {
    private static final long START_GRACE_SECONDS = 120;

    private final AuctionRepository auctions;
    private final ItemRepository items;
    private final AuctionUserService users;
    private final Clock clock;
    private final SocialService social;

    public AuctionService(AuctionRepository auctions, ItemRepository items, AuctionUserService users,
                          Clock auctionClock, SocialService social) {
        this.auctions = auctions;
        this.items = items;
        this.users = users;
        this.clock = auctionClock;
        this.social = social;
    }

    public List<AuctionResponse> getAllAuctions() {
        // phiên bị admin ẩn thì không hiện trong danh sách
        return auctions.findAll().stream().filter(a -> !a.isHidden()).map(this::response).toList();
    }

    public AuctionResponse getAuctionById(Long id) {
        AuctionEntity auction = auctions.findById(id).orElse(null);
        AuctionRules.require(auction != null && !auction.isHidden(), HttpStatus.NOT_FOUND, "Không tìm thấy phiên đấu giá.");
        return response(auction);
    }

    @Transactional
    public AuctionResponse createAuction(AuctionRequest request, Long sellerId) {
        users.requireRole(sellerId, UserRole.SELLER);
        AuctionRules.require(request != null && request.getItemId() != null && request.getStartTime() != null && request.getEndTime() != null,
                HttpStatus.BAD_REQUEST, "Thiếu sản phẩm hoặc thời gian đấu giá.");
        ItemEntity item = items.findByIdForUpdate(request.getItemId()).orElse(null);
        AuctionRules.require(item != null && item.getStatus() != ItemStatus.REMOVED, HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm.");
        AuctionRules.require(item.getSellerId().equals(sellerId), HttpStatus.FORBIDDEN, "Bạn không sở hữu sản phẩm này.");
        AuctionRules.require(item.getStatus() == ItemStatus.ACTIVE, HttpStatus.CONFLICT,
                "Sản phẩm chưa được quản trị viên phê duyệt.");
        AuctionRules.require(!auctions.existsByItemId(item.getId()), HttpStatus.CONFLICT, "Sản phẩm đã có phiên đấu giá.");
        LocalDateTime now = LocalDateTime.now(clock);
        // Giờ client gửi lên là UTC. Cho lệch tối đa 120s (ô chọn giờ chỉ chính xác tới phút, cộng độ trễ mạng).
        AuctionRules.require(!request.getStartTime().isBefore(now.minusSeconds(START_GRACE_SECONDS)),
                HttpStatus.BAD_REQUEST, "Thời gian bắt đầu không được ở trong quá khứ.");
        AuctionRules.require(request.getEndTime().isAfter(now) && request.getEndTime().isAfter(request.getStartTime()),
                HttpStatus.BAD_REQUEST, "Thời gian kết thúc không hợp lệ.");
        AuctionEntity auction = new AuctionEntity();
        auction.setItemId(item.getId());
        auction.setCurrentPrice(item.getStartingPrice());
        auction.setStartTime(request.getStartTime());
        auction.setEndTime(request.getEndTime());
        // OPEN = sắp diễn ra (scheduler sẽ chuyển RUNNING đúng giờ); startTime <= now => bắt đầu ngay
        auction.setStatus(request.getStartTime().isAfter(now) ? AuctionStatus.OPEN : AuctionStatus.RUNNING);
        AuctionEntity saved = auctions.save(auction);
        social.onNewAuction(saved, item); // báo cho người đang follow người bán
        return response(saved);
    }

    private AuctionResponse response(AuctionEntity auction) {
        ItemEntity item = items.findById(auction.getItemId()).orElseThrow();
        var result = new AuctionResponse(auction.getId(), item.getId(), item.getName(), item.getStartingPrice(),
                auction.getCurrentPrice(), auction.getHighestBidderId(), auction.getStartTime(), auction.getEndTime(), auction.getStatus());
        result.setImage(item.getImage());
        result.setSellerId(item.getSellerId());
        result.setCategory(item.getCategory());
        if (auction.getHighestBidderId() != null) {
            result.setHighestBidderName(users.findById(auction.getHighestBidderId())
                .map(UserEntity::getFullName).orElse(null));
        }
        return result;
    }
}
