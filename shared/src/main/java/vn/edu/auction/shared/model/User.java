package vn.edu.auction.shared.model;

import vn.edu.auction.shared.enums.UserRole;
import vn.edu.auction.shared.enums.UserStatus;

public abstract class User {
    protected static final java.util.Set<UserRole> TRADING = java.util.Set.of(UserRole.BIDDER, UserRole.SELLER);
    public abstract java.util.Set<UserRole> permissions();

    public boolean canActAs(UserRole permission) {
        return status == UserStatus.ACTIVE && permissions().contains(permission);
    }

    private Long id;
    private String fullName;
    private String email;
    private final UserRole role;
    private UserStatus status;

    public User(Long id, String fullName, String email, UserRole role, UserStatus status){
        this.id = id;
        this.fullName = fullName;
        this.email = email; 
        this.role = role;
        this.status = status;
    }

    //dự phòng tự khởi tạo

    public Long getId(){return id;}
    public String getFullName(){return fullName;}
    public String getEmail(){return email;}
    public UserRole getRole(){return role;}
    public UserStatus getStatus(){return status;}

    public void setId(Long newId){this.id = newId;}
    public void setFullName(String newFullName){this.fullName = newFullName;}
    public void setEmail(String newEmail){this.email = newEmail;}
    public void setStatus(UserStatus newStatus){this.status = newStatus;}

}
