package vn.edu.auction.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.auction.server.entity.TradingProfileEntity;

public interface TradingProfileRepository extends JpaRepository<TradingProfileEntity, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select case when p.separateProfiles = true then p.seller.name else p.buyer.name end
            from TradingProfileEntity p where p.userId = :id
            """)
    java.util.Optional<String> sellerName(@org.springframework.data.repository.query.Param("id") Long id);
}
