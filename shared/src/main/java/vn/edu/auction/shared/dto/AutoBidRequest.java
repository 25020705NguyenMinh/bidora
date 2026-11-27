package vn.edu.auction.shared.dto;

import java.math.BigDecimal;

public class AutoBidRequest {
    private BigDecimal maxBid;
    private BigDecimal increment;

    public AutoBidRequest() {}

    public AutoBidRequest(BigDecimal maxBid, BigDecimal increment) {
        this.maxBid = maxBid;
        this.increment = increment;
    }

    public BigDecimal getMaxBid() { return maxBid; }
    public void setMaxBid(BigDecimal maxBid) { this.maxBid = maxBid; }

    public BigDecimal getIncrement() { return increment; }
    public void setIncrement(BigDecimal increment) { this.increment = increment; }
}