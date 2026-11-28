package vn.edu.auction.shared.dto;

import vn.edu.auction.shared.enums.AuctionStatus;
import vn.edu.auction.shared.enums.ItemCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AuctionResponse {
    private String image;
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    private String highestBidderName;
    public String getHighestBidderName() { return highestBidderName; }
    public void setHighestBidderName(String highestBidderName) { this.highestBidderName = highestBidderName; }

    private Long id;
    private Long itemId;
    private Long sellerId;
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    private String itemName;
    private BigDecimal startingPrice;
    private BigDecimal currentPrice;
    private Long highestBidderId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;

    private ItemCategory category;
    public ItemCategory getCategory() { return category; }
    public void setCategory(ItemCategory category) { this.category = category; }

    public AuctionResponse(Long id, Long itemId, String itemName,
                           BigDecimal startingPrice, BigDecimal currentPrice,
                           Long highestBidderId, LocalDateTime startTime,
                           LocalDateTime endTime, AuctionStatus status) {
        this.id = id;
        this.itemId = itemId;
        this.itemName = itemName;
        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.highestBidderId = highestBidderId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public AuctionResponse(){}

    public Long getId(){return id;}
    public Long getItemId(){return itemId;}
    public String getItemName(){return itemName;}
    public BigDecimal getStartingPrice(){return startingPrice;}
    public BigDecimal getCurrentPrice(){return currentPrice;}
    public Long getHighestBidderId(){return highestBidderId;}
    public LocalDateTime getStartTime(){return startTime;}
    public LocalDateTime getEndTime(){return endTime;}
    public AuctionStatus getStatus(){return status;}

    public void setId(Long newId){this.id = newId;}
    public void setItemId(Long newItemId){this.itemId = newItemId;}
    public void setItemName(String newItemName){this.itemName = newItemName;}
    public void setStartingPrice(BigDecimal newStartingPrice){this.startingPrice = newStartingPrice;}
    public void setCurrentPrice(BigDecimal newCurrentPrice){this.currentPrice = newCurrentPrice;}
    public void setHighestBidderId(Long newHighestBidderId){this.highestBidderId = newHighestBidderId;}
    public void setStartTime(LocalDateTime newStartTime){this.startTime = newStartTime;}
    public void setEndTime(LocalDateTime newEndTime){this.endTime = newEndTime;}
    public void setStatus(AuctionStatus newStatus){this.status = newStatus;}
    
}
