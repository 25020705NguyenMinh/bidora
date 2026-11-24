package vn.edu.auction.shared.model;

/** Lượt đặt giá bị từ chối vì vi phạm quy tắc đấu giá (giá thấp, phiên đã đóng...). */
public class BidRejectedException extends RuntimeException {

    public BidRejectedException(String message) {
        super(message);
    }
}