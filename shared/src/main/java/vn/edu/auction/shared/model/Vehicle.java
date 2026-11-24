package vn.edu.auction.shared.model;

import java.math.BigDecimal;
import vn.edu.auction.shared.enums.ItemStatus;

public class Vehicle extends Item {

    private final String brand;
    private final int manufactureYear;
    private final int mileageKm;

    public Vehicle(Long id, Long sellerId, String name, String description,
                   BigDecimal startingPrice, ItemStatus status,
                   String brand, int manufactureYear, int mileageKm) {
        super(id, sellerId, name, description, startingPrice, status);
        if (manufactureYear <= 0 || mileageKm < 0) {
            throw new IllegalArgumentException("Năm sản xuất hoặc số km không hợp lệ.");
        }
        this.brand = requireText(brand, "Hãng xe");
        this.manufactureYear = manufactureYear;
        this.mileageKm = mileageKm;
    }

    @Override
    public String getCategory() {
        return "Vehicle";
    }

    public String getBrand() {
        return brand;
    }

    public int getManufactureYear() {
        return manufactureYear;
    }

    public int getMileageKm() {
        return mileageKm;
    }

    @Override
    public String printInfo() {
        return super.printInfo() + " | hãng " + brand + ", đời " + manufactureYear + ", " + mileageKm + " km";
    }
}