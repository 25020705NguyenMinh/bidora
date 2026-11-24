package vn.edu.auction.shared.model;

import java.math.BigDecimal;
import vn.edu.auction.shared.enums.ItemStatus;

/** Sản phẩm đấu giá. Abstract: chỉ tạo được qua các loại cụ thể (Electronics, Art, Vehicle). */
public abstract class Item extends Entity {

    private Long sellerId;
    private String name;
    private String description;
    private BigDecimal startingPrice;
    private ItemStatus status;

    protected Item(Long id, Long sellerId, String name, String description,
                   BigDecimal startingPrice, ItemStatus status) {
        super(id);
        this.sellerId = sellerId;
        this.name = requireText(name, "Tên sản phẩm");
        this.description = description;
        this.startingPrice = requirePositive(startingPrice, "Giá khởi điểm");
        this.status = status;
    }

    /** Tên loại sản phẩm, do lớp con quy định. */
    public abstract String getCategory();

    public Long getSellerId() {
        return sellerId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId;
    }

    public void setName(String name) {
        this.name = requireText(name, "Tên sản phẩm");
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = requirePositive(startingPrice, "Giá khởi điểm");
    }

    public void setStatus(ItemStatus status) {
        this.status = status;
    }

    /** Thông tin chung; lớp con gọi super.printInfo() rồi nối thêm thông tin riêng. */
    @Override
    public String printInfo() {
        return String.format("[%s] #%s %s - giá khởi điểm %s - %s",
                getCategory(), getId(), name, startingPrice.toPlainString(), status);
    }

    protected static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " không được để trống.");
        }
        return value.trim();
    }

    protected static BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " phải lớn hơn 0.");
        }
        return value;
    }
}