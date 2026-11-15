package vn.edu.auction.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.auction.server.config.AuctionTimeConfig;
import vn.edu.auction.server.config.SecurityConfig;
import vn.edu.auction.server.controller.AdminController;
import vn.edu.auction.server.entity.UserEntity;
import vn.edu.auction.server.event.AuctionEventPublisher;
import vn.edu.auction.server.event.UserSocketHandler;
import vn.edu.auction.server.exception.AuctionApiExceptionHandler;
import vn.edu.auction.server.exception.GlobalExceptionHandler;
import vn.edu.auction.server.repository.NotificationRepository;
import vn.edu.auction.server.repository.UserRepository;
import vn.edu.auction.server.security.JwtService;
import vn.edu.auction.server.service.AdminService;
import vn.edu.auction.server.service.SocialService;
import vn.edu.auction.shared.enums.UserRole;
import vn.edu.auction.shared.enums.UserStatus;

/** Kiểm thử chức năng "admin nâng quyền tài khoản khác lên admin" (issue #27). */
@SpringBootTest(classes = AdminPromotionTest.TestApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:adminpromo;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "MAIL_PASSWORD=test",
        //thêm 2 dòng này vào nếu test không chạy được: đơn giản là điều hướng MySQL sang H2 để test, vì test k cần MySQL
        "spring.datasource.driver-class-name=org.h2.Driver",    //dùng drive H2 cho test thay vì dùng my
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",  //hibernate sinh câu sql theo cú pháp của H2 thay vì dùng MySQL.
})
@AutoConfigureMockMvc
class AdminPromotionTest {

    /** Context tối thiểu: chỉ nạp những bean mà AdminController cần. */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("vn.edu.auction.server.entity")
    @EnableJpaRepositories("vn.edu.auction.server.repository")
    @Import({AdminService.class, SocialService.class, UserSocketHandler.class,
            AuctionEventPublisher.class, AdminController.class, AuctionApiExceptionHandler.class,
            GlobalExceptionHandler.class, SecurityConfig.class, JwtService.class, AuctionTimeConfig.class})
    static class TestApp {
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired JwtService jwt;

    UserEntity admin;
    UserEntity bidder;

    @BeforeEach
    void prepare() {
        notifications.deleteAll();
        users.deleteAll();
        admin = save("admin", UserRole.ADMIN, UserStatus.ACTIVE);
        bidder = save("bidder", UserRole.BIDDER, UserStatus.ACTIVE);
    }

    private UserEntity save(String name, UserRole role, UserStatus status) {
        return users.save(new UserEntity(name, name + "@test.local", "hash", role, status));
    }

    /** Gửi POST /promote bằng JWT của {@code caller}. */
    private ResultActions promote(Long targetId, UserEntity caller) throws Exception {
        return mvc.perform(post("/api/admin/users/" + targetId + "/promote")
                .header("Authorization", "Bearer " + jwt.generateToken(caller)));
    }

    @Test
    void adminCanPromoteActiveUserAndNewAdminGainsAccessImmediately() throws Exception {
        String bidderToken = jwt.generateToken(bidder); // token cấp TRƯỚC khi được nâng quyền
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + bidderToken))
                .andExpect(status().isForbidden());

        promote(bidder.getId(), admin).andExpect(status().isNoContent());

        assertEquals(UserRole.ADMIN, users.findById(bidder.getId()).orElseThrow().getRole());
        assertEquals(1, notifications.count()); // người được nâng nhận thông báo
        // Server đọc role từ DB mỗi request => token cũ dùng được ngay
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + bidderToken))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminCannotPromoteAnyone() throws Exception {
        UserEntity other = save("other", UserRole.BIDDER, UserStatus.ACTIVE);
        promote(other.getId(), bidder).andExpect(status().isForbidden());
        assertEquals(UserRole.BIDDER, users.findById(other.getId()).orElseThrow().getRole());
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mvc.perform(post("/api/admin/users/" + bidder.getId() + "/promote"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cannotPromoteAccountThatIsAlreadyAdmin() throws Exception {
        UserEntity other = save("admin2", UserRole.ADMIN, UserStatus.ACTIVE);
        promote(other.getId(), admin).andExpect(status().isConflict());
    }

    @Test
    void cannotPromoteUnverifiedOrBannedAccount() throws Exception {
        UserEntity unverified = save("unverified", UserRole.BIDDER, UserStatus.INACTIVE);
        UserEntity banned = save("banned", UserRole.SELLER, UserStatus.BANNED);
        promote(unverified.getId(), admin).andExpect(status().isConflict());
        promote(banned.getId(), admin).andExpect(status().isConflict());
        assertEquals(UserRole.BIDDER, users.findById(unverified.getId()).orElseThrow().getRole());
        assertEquals(UserRole.SELLER, users.findById(banned.getId()).orElseThrow().getRole());
    }

    @Test
    void promotingUnknownUserReturnsNotFound() throws Exception {
        promote(999_999L, admin).andExpect(status().isNotFound());
    }
}