package vn.edu.auction.shared.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import vn.edu.auction.shared.enums.AuctionStatus;
import vn.edu.auction.shared.enums.BidStatus;

/**
 * Phiên đấu giá: tự bảo vệ quy tắc của mình (đặt giá hợp lệ, gia hạn chống sniping,
 * chuyển trạng thái OPEN -> RUNNING -> FINISHED -> PAID / CANCELED).
 * Không có setter cho giá, người dẫn đầu và trạng thái: chỉ đổi qua các phương thức bên dưới.
 */
public class Auction extends Entity {

    public static final long SNIPE_WINDOW_SECONDS = 10;
    public static final long EXTENSION_SECONDS = 60;

    private final Long itemId;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal currentPrice;
    private Long highestBidderId;
    private AuctionStatus status;

    /** Tạo phiên mới: trạng thái OPEN, chưa có người dẫn đầu. */
    public Auction(Long id, Long itemId, BigDecimal startingPrice,
                   LocalDateTime startTime, LocalDateTime endTime) {
        this(id, itemId, startingPrice, null, startTime, endTime, AuctionStatus.OPEN);
    }

    /** Dựng lại phiên đã lưu (ví dụ đọc từ database). */
    public Auction(Long id, Long itemId, BigDecimal currentPrice, Long highestBidderId,
                   LocalDateTime startTime, LocalDateTime endTime, AuctionStatus status) {
        super(id);
        if (itemId == null || startTime == null || endTime == null || status == null) {
            throw new IllegalArgumentException("Thiếu thông tin phiên đấu giá.");
        }
        if (currentPrice == null || currentPrice.signum() <= 0) {
            throw new IllegalArgumentException("Giá phải lớn hơn 0.");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
        this.itemId = itemId;
        this.currentPrice = currentPrice;
        this.highestBidderId = highestBidderId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public Long getItemId() {
        return itemId;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public Long getHighestBidderId() {
        return highestBidderId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public boolean hasWinner() {
        return highestBidderId != null;
    }

    /** Phiên đang nhận giá tại thời điểm now? */
    public boolean isAcceptingBids(LocalDateTime now) {
        boolean activeStatus = status == AuctionStatus.OPEN || status == AuctionStatus.RUNNING;
        return activeStatus && !now.isBefore(startTime) && now.isBefore(endTime);
    }

    /**
     * Đặt giá. Hợp lệ thì cập nhật giá, người dẫn đầu, trạng thái RUNNING, gia hạn nếu đặt phút chót
     * và trả về lượt đặt giá ACCEPTED; không hợp lệ thì ném {@link BidRejectedException}
     * và phiên không thay đổi.
     */
    public BidTransaction placeBid(Long bidderId, BigDecimal amount, LocalDateTime now) {
        if (bidderId == null || amount == null || now == null) {
            throw new BidRejectedException("Thiếu thông tin đặt giá.");
        }
        if (!isAcceptingBids(now)) {
            throw new BidRejectedException("Phiên chưa mở hoặc đã kết thúc.");
        }
        if (amount.compareTo(currentPrice) <= 0) {
            throw new BidRejectedException("Giá mới phải cao hơn giá hiện tại.");
        }
        if (!now.isBefore(endTime.minusSeconds(SNIPE_WINDOW_SECONDS))) {
            endTime = endTime.plusSeconds(EXTENSION_SECONDS);
        }
        status = AuctionStatus.RUNNING;
        currentPrice = amount;
        highestBidderId = bidderId;
        return new BidTransaction(null, getId(), bidderId, amount, now, BidStatus.ACCEPTED);
    }

    /** OPEN -> RUNNING (ví dụ khi đến giờ bắt đầu). */
    public void start() {
        requireStatus(AuctionStatus.OPEN);
        status = AuctionStatus.RUNNING;
    }

    /** Kết thúc phiên khi đã hết giờ. Không có bid thì không có người thắng. */
    public void finish(LocalDateTime now) {
        if (status != AuctionStatus.OPEN && status != AuctionStatus.RUNNING) {
            throw new IllegalStateException("Không thể kết thúc phiên ở trạng thái " + status + ".");
        }
        if (now.isBefore(endTime)) {
            throw new IllegalStateException("Phiên chưa hết giờ.");
        }
        status = AuctionStatus.FINISHED;
    }

    /** FINISHED -> PAID, chỉ khi có người thắng. */
    public void markPaid() {
        requireStatus(AuctionStatus.FINISHED);
        if (!hasWinner()) {
            throw new IllegalStateException("Phiên không có người thắng nên không thể thanh toán.");
        }
        status = AuctionStatus.PAID;
    }

    /** Hủy phiên; không hủy được phiên đã thanh toán hoặc đã hủy. */
    public void cancel() {
        if (status == AuctionStatus.PAID || status == AuctionStatus.CANCELED) {
            throw new IllegalStateException("Không thể hủy phiên ở trạng thái " + status + ".");
        }
        status = AuctionStatus.CANCELED;
    }

    private void requireStatus(AuctionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Cần trạng thái " + expected + " nhưng đang là " + status + ".");
        }
    }

    @Override
    public String printInfo() {
        return String.format("Auction #%s (item %s) [%s] giá hiện tại %s, dẫn đầu: %s, kết thúc %s",
                getId(), itemId, status, currentPrice.toPlainString(),
                highestBidderId == null ? "chưa có" : highestBidderId, endTime);
    }
}