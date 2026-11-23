package vn.edu.auction.shared.model;

import java.util.Set;
import vn.edu.auction.shared.enums.UserRole;
import vn.edu.auction.shared.enums.UserStatus;

public abstract class User extends Entity {

    protected static final Set<UserRole> TRADING = Set.of(UserRole.BIDDER, UserRole.SELLER);

    private String fullName;
    private String email;
    private final UserRole role;
    private UserStatus status;

    protected User(Long id, String fullName, String email, UserRole role, UserStatus status) {
        super(id);
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.status = status;
    }

    /** Quyền mà loại người dùng này có; mỗi lớp con quyết định. */
    public abstract Set<UserRole> permissions();

    public boolean canActAs(UserRole permission) {
        return status == UserStatus.ACTIVE && permissions().contains(permission);
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    @Override
    public String printInfo() {
        return String.format("#%s %s <%s> [%s]", getId(), fullName, email, status);
    }
}