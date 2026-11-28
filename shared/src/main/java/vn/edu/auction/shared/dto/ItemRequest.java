package vn.edu.auction.shared.dto;

import java.math.BigDecimal;
import vn.edu.auction.shared.enums.ItemCategory;

public class ItemRequest {
    private String image;
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }


    private String name;
    private String description;
    private BigDecimal startingPrice;

    private ItemCategory category;
    public ItemCategory getCategory() { return category; }
    public void setCategory(ItemCategory category) { this.category = category; }

    public ItemRequest(String name, String description, BigDecimal startingPrice){
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
    }

    public ItemRequest(){}

    public String getName(){return name;}
    public String getDescription(){return description;}
    public BigDecimal getStartingPrice(){return startingPrice;}

    public void setName(String newName){this.name = newName;}
    public void setDescription(String newDescription){this.description = newDescription;}
    public void setStartingPrice(BigDecimal newStartingPrice){this.startingPrice = newStartingPrice;}

}
