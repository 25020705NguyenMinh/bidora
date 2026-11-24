package vn.edu.auction.shared.model;

import java.math.BigDecimal;
import vn.edu.auction.shared.enums.ItemStatus;

public class Art extends Item {

    private final String artist;
    private final int yearCreated;

    public Art(Long id, Long sellerId, String name, String description,
               BigDecimal startingPrice, ItemStatus status, String artist, int yearCreated) {
        super(id, sellerId, name, description, startingPrice, status);
        if (yearCreated <= 0) {
            throw new IllegalArgumentException("Năm sáng tác không hợp lệ.");
        }
        this.artist = requireText(artist, "Tác giả");
        this.yearCreated = yearCreated;
    }

    @Override
    public String getCategory() {
        return "Art";
    }

    public String getArtist() {
        return artist;
    }

    public int getYearCreated() {
        return yearCreated;
    }

    @Override
    public String printInfo() {
        return super.printInfo() + " | tác giả " + artist + ", sáng tác năm " + yearCreated;
    }
}