package vn.edu.auction.server.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cứ mỗi giây: kích hoạt phiên đến giờ bắt đầu, rồi đóng các phiên hết hạn. */
@Component
public class AuctionScheduler {
    private final AuctionClosingService closingService;
    private final AuctionActivationService activationService;

    public AuctionScheduler(AuctionClosingService closingService, AuctionActivationService activationService) {
        this.closingService = closingService;
        this.activationService = activationService;
    }

    @Scheduled(fixedDelayString = "${app.auction.activate-interval-ms:1000}")
    public void activateDue() {
        activationService.activateDueAuctions();
    }

    @Scheduled(fixedDelayString = "${app.auction.close-interval-ms:1000}")
    public void closeExpired() {
        closingService.closeExpiredAuctions();
    }
}
