package vn.edu.auction.shared.model;

import org.junit.jupiter.api.Test;
import vn.edu.auction.shared.enums.*;
import static org.junit.jupiter.api.Assertions.*;

class UserFactoryTest {
    @Test void tradingUsesAccountStatusAndAdministrationIsAdditional() {
        for (UserRole role : UserRole.values()) {
            User user = UserFactory.create(17, "An", "an@example.test", role, UserStatus.ACTIVE);
            assertTrue(user.canActAs(UserRole.BIDDER));
            assertTrue(user.canActAs(UserRole.SELLER));
            assertEquals(role == UserRole.ADMIN, user.canActAs(UserRole.ADMIN));
            for (UserStatus status : UserStatus.values()) {
                if (status == UserStatus.ACTIVE) continue;
                user.setStatus(status);
                for (UserRole activity : UserRole.values()) assertFalse(user.canActAs(activity));
            }
        }
    }
}
