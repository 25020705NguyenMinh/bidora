package vn.edu.auction.server.entity;

import org.hibernate.annotations.ColumnDefault;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import vn.edu.auction.shared.enums.*;
import java.util.function.Consumer;
import vn.edu.auction.shared.model.Auction;

@Entity
@Table(name = "auctions")
public class AuctionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "item_id", nullable = false)
    private Long itemId;
    @Column(name = "current_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal currentPrice;
    @Column(name = "highest_bidder_id")
    private Long highestBidderId;
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AuctionStatus status;

    @ColumnDefault("false")
    @Column(name = "hidden", nullable = false)
    private boolean hidden = false;

    public AuctionEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public Long getHighestBidderId() { return highestBidderId; }
    public void setHighestBidderId(Long highestBidderId) { this.highestBidderId = highestBidderId; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    /** Dựng đối tượng domain từ dữ liệu đang lưu; mọi quy tắc đấu giá nằm ở {@link Auction}. */
    public Auction toDomain() {
        return new Auction(id, itemId, currentPrice, highestBidderId, startTime, endTime, status);
    }

    /** Ghi lại phần trạng thái có thể thay đổi của domain (giá, người dẫn đầu, hạn chót, trạng thái). */
    public void applyFrom(Auction domain) {
        this.currentPrice = domain.getCurrentPrice();
        this.highestBidderId = domain.getHighestBidderId();
        this.endTime = domain.getEndTime();
        this.status = domain.getStatus();
    }

    /** Chạy một thao tác của domain (start, finish, cancel...) rồi ghi kết quả về entity. */
    public void transition(Consumer<Auction> action) {
        Auction domain = toDomain();
        action.accept(domain);
        applyFrom(domain);
    }
}
