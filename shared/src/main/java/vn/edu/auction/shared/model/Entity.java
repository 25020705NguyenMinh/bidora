package vn.edu.auction.shared.model;

/** Lớp cơ sở của mọi đối tượng nghiệp vụ: có id và biết tự mô tả mình. */
public abstract class Entity {

    private Long id;

    protected Entity() {
    }

    protected Entity(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /** Mỗi lớp con tự mô tả theo cách riêng (đa hình). */
    public abstract String printInfo();

    @Override
    public String toString() {
        return printInfo();
    }
}