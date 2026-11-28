package vn.edu.auction.server.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "auto_bids",
        uniqueConstraints = @UniqueConstraint(columnNames = {"auction_id", "bidder_id"}))
public class AutoBidEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "auction_id", nullable = false)
    private Long auctionId;
    @Column(name = "bidder_id", nullable = false)
    private Long bidderId;
    @Column(name = "max_bid", precision = 19, scale = 2, nullable = false)
    private BigDecimal maxBid;
    @Column(name = "bid_increment", precision = 19, scale = 2, nullable = false)
    private BigDecimal increment;
    /** Thời điểm đăng ký / cập nhật gần nhất: dùng để phân xử khi hai người cùng maxBid. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public AutoBidEntity() {}

    public Long getId() { return id; }
    public Long getAuctionId() { return auctionId; }
    public void setAuctionId(Long auctionId) { this.auctionId = auctionId; }
    public Long getBidderId() { return bidderId; }
    public void setBidderId(Long bidderId) { this.bidderId = bidderId; }
    public BigDecimal getMaxBid() { return maxBid; }
    public void setMaxBid(BigDecimal maxBid) { this.maxBid = maxBid; }
    public BigDecimal getIncrement() { return increment; }
    public void setIncrement(BigDecimal increment) { this.increment = increment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}