package vn.edu.auction.server.repository;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import vn.edu.auction.server.entity.BidTransactionEntity;

public interface BidTransactionRepository extends JpaRepository<BidTransactionEntity, Long> {
    List<BidTransactionEntity> findByAuctionIdOrderByBidTimeDescIdDesc(Long auctionId);
    List<BidTransactionEntity> findByAuctionIdOrderByBidTimeDesc(Long auctionId);

    java.util.Optional<BidTransactionEntity> findFirstByAuctionIdAndStatusOrderByAmountDescIdAsc(
            Long auctionId, vn.edu.auction.shared.enums.BidStatus status);

    long countByAuctionId(Long auctionId);
}
