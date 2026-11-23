package vn.edu.auction.server.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.auction.server.entity.TradingProfileEntity;
import vn.edu.auction.server.entity.UserEntity;
import vn.edu.auction.server.repository.TradingProfileRepository;
import vn.edu.auction.server.repository.UserRepository;
import vn.edu.auction.shared.dto.TradingProfiles;
import vn.edu.auction.shared.dto.TradingProfiles.Card;
import vn.edu.auction.shared.enums.UserStatus;

@Service
@Transactional(readOnly = true)
public class TradingProfileService {
    private final UserRepository users;
    private final TradingProfileRepository profiles;

    public TradingProfileService(UserRepository users, TradingProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    public TradingProfiles get(Long id) {
        UserEntity user = users.findById(id).orElse(null);
        AuctionRules.require(user != null && user.getStatus() == UserStatus.ACTIVE,
                HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ.");
        return profiles.findById(id).map(TradingProfileEntity::profiles).orElseGet(() -> {
            Card common = new Card(user.getFullName(), "", "");
            return new TradingProfiles(false, common, common);
        });
    }

    @Transactional
    public TradingProfiles save(Long id, TradingProfiles request) {
        AuctionRules.require(request != null, HttpStatus.BAD_REQUEST, "Thiếu hồ sơ.");
        var buyer = validate(request.buyer());
        var seller = validate(request.seller());
        var user = users.findByIdForUpdate(id).orElse(null);
        AuctionRules.require(user != null && user.getStatus() == UserStatus.ACTIVE,
                HttpStatus.FORBIDDEN, "Tài khoản chưa hoạt động.");
        var entity = profiles.findById(id).orElseGet(TradingProfileEntity::new);
        entity.userId = id;
        entity.setProfiles(new TradingProfiles(request.separate(), buyer, seller));
        return profiles.save(entity).profiles();
    }

    private Card validate(Card card) {
        AuctionRules.require(card != null && card.name() != null && !card.name().isBlank()
                && card.name().strip().length() <= 100, HttpStatus.BAD_REQUEST, "Tên hiển thị cần từ 1 đến 100 ký tự.");
        String bio = card.bio() == null ? "" : card.bio().strip();
        AuctionRules.require(bio.length() <= 500, HttpStatus.BAD_REQUEST, "Giới thiệu tối đa 500 ký tự.");
        String avatar = card.avatar() == null ? "" : card.avatar();
        AuctionRules.require(avatar.isEmpty() || validAvatar(avatar), HttpStatus.BAD_REQUEST, "Ảnh đại diện không hợp lệ.");
        return new Card(card.name().strip(), bio, avatar);
    }

    private boolean validAvatar(String value) {
        if (value.length() > 400_000) return false;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value)))) {
            var readers = ImageIO.getImageReadersByFormatName("PNG");
            var reader = readers.next();
            try {
                reader.setInput(input);
                return reader.getWidth(0) <= 256 && reader.getHeight(0) <= 256 && reader.read(0) != null;
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }
}
