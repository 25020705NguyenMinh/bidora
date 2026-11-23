package vn.edu.auction.shared.model;

import java.math.BigDecimal;
import vn.edu.auction.shared.enums.ItemStatus;

public class Electronics extends Item {

    private final String brand;
    private final int warrantyMonths;

    public Electronics(Long id, Long sellerId, String name, String description,
                       BigDecimal startingPrice, ItemStatus status, String brand, int warrantyMonths) {
        super(id, sellerId, name, description, startingPrice, status);
        if (warrantyMonths < 0) {
            throw new IllegalArgumentException("Thời gian bảo hành không được âm.");
        }
        this.brand = requireText(brand, "Hãng");
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public String getCategory() {
        return "Electronics";
    }

    public String getBrand() {
        return brand;
    }

    public int getWarrantyMonths() {
        return warrantyMonths;
    }

    @Override
    public String printInfo() {
        return super.printInfo() + " | hãng " + brand + ", bảo hành " + warrantyMonths + " tháng";
    }
}