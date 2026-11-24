package vn.edu.auction.shared.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import vn.edu.auction.shared.enums.BidStatus;

/** Một lượt đặt giá. Bất biến sau khi tạo, trừ id do database cấp. */
public class BidTransaction extends Entity {

    private final Long auctionId;
    private final Long bidderId;
    private final BigDecimal amount;
    private final LocalDateTime bidTime;
    private final BidStatus status;

    public BidTransaction(Long id, Long auctionId, Long bidderId, BigDecimal amount,
                          LocalDateTime bidTime, BidStatus status) {
        super(id);
        if (auctionId == null || bidderId == null || bidTime == null || status == null) {
            throw new IllegalArgumentException("Thiếu thông tin lượt đặt giá.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Số tiền phải lớn hơn 0.");
        }
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.bidTime = bidTime;
        this.status = status;
    }

    public Long getAuctionId() {
        return auctionId;
    }

    public Long getBidderId() {
        return bidderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getBidTime() {
        return bidTime;
    }

    public BidStatus getStatus() {
        return status;
    }

    public boolean isAccepted() {
        return status == BidStatus.ACCEPTED;
    }

    @Override
    public String printInfo() {
        return String.format("Bid #%s: user %s đặt %s vào phiên %s lúc %s [%s]",
                getId(), bidderId, amount.toPlainString(), auctionId, bidTime, status);
    }
}