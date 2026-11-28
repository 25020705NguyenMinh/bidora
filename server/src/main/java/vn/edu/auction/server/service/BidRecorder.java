package vn.edu.auction.server.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.auction.server.entity.AuctionEntity;
import vn.edu.auction.server.entity.BidTransactionEntity;
import vn.edu.auction.server.exception.AuctionBusinessException;
import vn.edu.auction.server.repository.BidTransactionRepository;
import vn.edu.auction.shared.model.Auction;
import vn.edu.auction.shared.model.BidRejectedException;
import vn.edu.auction.shared.model.BidTransaction;

/** Đặt giá theo quy tắc của {@link Auction}, cập nhật phiên rồi lưu lượt đặt giá. */
@Component
public class BidRecorder {

    private final BidTransactionRepository bids;

    public BidRecorder(BidTransactionRepository bids) {
        this.bids = bids;
    }

    /** Chỉ gọi khi đang giữ khóa phiên (findByIdForUpdate) và trong transaction. */
    BidTransactionEntity place(AuctionEntity auction, Long bidderId, BigDecimal amount,
                               LocalDateTime now) {
        Auction domain = auction.toDomain();
        BidTransaction accepted;
        try {
            accepted = domain.placeBid(bidderId, amount, now);
        } catch (BidRejectedException e) {
            throw new AuctionBusinessException(HttpStatus.CONFLICT, e.getMessage());
        }
        auction.applyFrom(domain);
        return bids.saveAndFlush(BidTransactionEntity.from(accepted));
    }
}