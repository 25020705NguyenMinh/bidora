package vn.edu.auction.server.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.auction.server.entity.AutoBidEntity;

public interface AutoBidRepository extends JpaRepository<AutoBidEntity, Long> {
    List<AutoBidEntity> findByAuctionIdAndActiveTrue(Long auctionId);
    Optional<AutoBidEntity> findByAuctionIdAndBidderId(Long auctionId, Long bidderId);
}