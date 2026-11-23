package vn.edu.auction.server.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.edu.auction.server.entity.UserEntity;
import vn.edu.auction.server.repository.UserRepository;
import vn.edu.auction.shared.enums.*;

@Service
public class AuctionUserService {
    private final UserRepository users;

    public AuctionUserService(UserRepository users) { this.users = users; }

    public java.util.Optional<UserEntity> findById(Long userId) {
        return userId == null ? java.util.Optional.empty() : users.findById(userId);
    }

    public boolean isAdmin(Long userId) {
        return userId != null && users.findById(userId).map(u -> u.getRole() == UserRole.ADMIN).orElse(false);
    }

    public UserEntity requireRole(Long userId, UserRole role) {
        AuctionRules.require(userId != null, HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập.");
        UserEntity user = users.findById(userId).orElse(null);
        AuctionRules.require(user != null && user.getStatus() == UserStatus.ACTIVE,
                HttpStatus.FORBIDDEN, "Tài khoản không hoạt động.");
        AuctionRules.require(user.asDomainUser().canActAs(role), HttpStatus.FORBIDDEN, "Tài khoản không có quyền thực hiện.");
        return user;
    }
}
