package vn.edu.auction.shared.model;

import vn.edu.auction.shared.enums.UserRole;
import vn.edu.auction.shared.enums.UserStatus;

public final class UserFactory {
    private UserFactory() {}

    public static User create(long id, String name, String email, UserRole role, UserStatus status) {
        return switch (role) {
            case BIDDER -> new Bidder(id, name, email, status);
            case SELLER -> new Seller(id, name, email, status);
            case ADMIN -> new Admin(id, name, email, status);
        };
    }
}
