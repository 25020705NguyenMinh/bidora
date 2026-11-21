package vn.edu.auction.server.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.auction.server.entity.AuctionEntity;
import vn.edu.auction.server.event.AuctionEventPublisher;
import vn.edu.auction.server.repository.AuctionRepository;
import vn.edu.auction.shared.enums.AuctionStatus;

/**
 * Tự kích hoạt các phiên đã lên lịch: tới giờ bắt đầu thì OPEN -> RUNNING và phát AUCTION_STARTED.
 * Thời lượng không cần tính lại vì endTime đã được lưu = startTime + thời lượng lúc tạo phiên.
 */
@Service
public class AuctionActivationService {
    private static final Logger log = LoggerFactory.getLogger(AuctionActivationService.class);

    private final AuctionRepository auctions;
    private final AuctionEventPublisher publisher;
    private final Clock clock;
    private final TransactionTemplate tx;

    public AuctionActivationService(AuctionRepository auctions, AuctionEventPublisher publisher,
                                    Clock auctionClock, PlatformTransactionManager txManager) {
        this.auctions = auctions;
        this.publisher = publisher;
        this.clock = auctionClock;
        this.tx = new TransactionTemplate(txManager); // mỗi phiên 1 transaction riêng
    }

    /** Trả về số phiên thực sự vừa được kích hoạt. Chạy lại sau khi restart sẽ bù các phiên bị lỡ. */
    public int activateDueAuctions() {
        List<Long> ids = auctions.findDueToStartIds(LocalDateTime.now(clock));
        int started = 0;
        for (Long id : ids) {
            try {
                if (activate(id)) started++;
            } catch (RuntimeException e) {
                log.error("Kich hoat phien {} that bai, se thu lai o luot sau", id, e);
            }
        }
        return started;
    }

    /** Idempotent: gọi nhiều lần (hoặc từ nhiều instance) chỉ lần đầu trả về true. */
    public boolean activate(Long auctionId) {
        return Boolean.TRUE.equals(tx.execute(status -> doActivate(auctionId)));
    }

    private boolean doActivate(Long auctionId) {
        // Khóa ghi: bid / đóng phiên / instance khác không chen vào cùng lúc
        AuctionEntity auction = auctions.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null || auction.getStatus() != AuctionStatus.OPEN) return false; // đã có người xử lý
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(auction.getStartTime()) || !now.isBefore(auction.getEndTime())) return false;
        auction.setStatus(AuctionStatus.RUNNING);
        auctions.save(auction);
        publisher.publishAuctionStarted(auction); // chỉ thật sự gửi sau khi commit
        return true;
    }
}
