package vn.edu.auction.shared.model;

import java.util.Set;
import vn.edu.auction.shared.enums.UserRole;
import vn.edu.auction.shared.enums.UserStatus;

public class Bidder extends User {

    public Bidder(Long id, String fullName, String email, UserStatus status) {
        super(id, fullName, email, UserRole.BIDDER, status);
    }

    public Bidder() {
        this(null, null, null, null);
    }

    @Override
    public Set<UserRole> permissions() {
        return TRADING;
    }

    @Override
    public String printInfo() {
        return "Bidder " + super.printInfo() + " - tham gia đặt giá";
    }
}