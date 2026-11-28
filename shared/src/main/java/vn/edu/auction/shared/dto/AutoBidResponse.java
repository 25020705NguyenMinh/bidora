package vn.edu.auction.shared.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AutoBidResponse {
    private Long id;
    private Long auctionId;
    private Long bidderId;
    private BigDecimal maxBid;
    private BigDecimal increment;
    private boolean active;
    private LocalDateTime createdAt;

    public AutoBidResponse() {}

    public AutoBidResponse(Long id, Long auctionId, Long bidderId, BigDecimal maxBid,
                           BigDecimal increment, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.active = active;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAuctionId() { return auctionId; }
    public void setAuctionId(Long auctionId) { this.auctionId = auctionId; }
    public Long getBidderId() { return bidderId; }
    public void setBidderId(Long bidderId) { this.bidderId = bidderId; }
    public BigDecimal getMaxBid() { return maxBid; }
    public void setMaxBid(BigDecimal maxBid) { this.maxBid = maxBid; }
    public BigDecimal getIncrement() { return increment; }
    public void setIncrement(BigDecimal increment) { this.increment = increment; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}