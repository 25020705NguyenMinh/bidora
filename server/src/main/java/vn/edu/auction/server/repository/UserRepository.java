package vn.edu.auction.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.auction.server.entity.UserEntity;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select new vn.edu.auction.shared.dto.ExtraDto$UserBriefResponse(u.id, coalesce(p.buyer.name, u.fullName), u.role)
            from UserEntity u left join TradingProfileEntity p on p.userId = u.id
            where u.status = vn.edu.auction.shared.enums.UserStatus.ACTIVE and u.id <> :id
            order by u.id
            """)
    java.util.List<vn.edu.auction.shared.dto.ExtraDto.UserBriefResponse> directory(
            @org.springframework.data.repository.query.Param("id") Long id);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
