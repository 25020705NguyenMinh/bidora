package vn.edu.auction.shared.model;

import java.util.Set;
import vn.edu.auction.shared.enums.UserRole;
import vn.edu.auction.shared.enums.UserStatus;

public class Admin extends User {

    public Admin(Long id, String fullName, String email, UserStatus status) {
        super(id, fullName, email, UserRole.ADMIN, status);
    }

    public Admin() {
        this(null, null, null, null);
    }

    @Override
    public Set<UserRole> permissions() {
        return Set.of(UserRole.ADMIN, UserRole.SELLER, UserRole.BIDDER);
    }

    @Override
    public String printInfo() {
        return "Admin " + super.printInfo() + " - quản trị hệ thống";
    }
}