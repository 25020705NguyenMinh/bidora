package vn.edu.auction.shared.model;

import vn.edu.auction.shared.enums.UserRole;
import vn.edu.auction.shared.enums.UserStatus;

public class Admin extends User {

    public Admin(long id, String fullName, String email, UserStatus status) {
        super(id, fullName, email, UserRole.ADMIN, status);
    }

    public Admin() {
        super(null, null, null, UserRole.ADMIN, null);
    }

    @Override public java.util.Set<UserRole> permissions() {
        return java.util.Set.of(UserRole.ADMIN, UserRole.SELLER, UserRole.BIDDER);
    }
    
}
