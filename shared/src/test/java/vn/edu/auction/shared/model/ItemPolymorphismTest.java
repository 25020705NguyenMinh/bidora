package vn.edu.auction.shared.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.auction.shared.enums.ItemStatus;

class ItemPolymorphismTest {
    @Test
    void eachSubclassDescribesItselfDifferently() {
        List<Item> items = List.of(
                new Electronics(1L, 9L, "iPhone", "cũ", new BigDecimal("100"), ItemStatus.ACTIVE, "Apple", 12),
                new Art(2L, 9L, "Tranh", "sơn dầu", new BigDecimal("500"), ItemStatus.ACTIVE, "Bùi Xuân Phái", 1960),
                new Vehicle(3L, 9L, "Xe", "", new BigDecimal("900"), ItemStatus.ACTIVE, "Honda", 2020, 15000));
        assertTrue(items.get(0).printInfo().contains("bảo hành 12"));
        assertTrue(items.get(1).printInfo().contains("Bùi Xuân Phái"));
        assertTrue(items.get(2).printInfo().contains("15000 km"));
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () ->
                new Art(1L, 1L, " ", "", BigDecimal.ONE, ItemStatus.DRAFT, "x", 1900));
        assertThrows(IllegalArgumentException.class, () ->
                new Art(1L, 1L, "t", "", BigDecimal.ZERO, ItemStatus.DRAFT, "x", 1900));
    }
}