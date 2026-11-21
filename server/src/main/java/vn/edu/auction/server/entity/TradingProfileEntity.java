package vn.edu.auction.server.entity;

import jakarta.persistence.*;
import vn.edu.auction.shared.dto.TradingProfiles;

@Entity
@Table(name = "trading_profiles")
public class TradingProfileEntity {
    @Id
    public Long userId;

    private boolean separateProfiles;

    @Embeddable
    public record CardFields(String name, String bio, String avatar) {
        CardFields(TradingProfiles.Card card) { this(card.name(), card.bio(), card.avatar()); }
        TradingProfiles.Card card() { return new TradingProfiles.Card(name, bio, avatar); }
    }

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "name", column = @Column(name = "buyer_name", length = 100)),
        @AttributeOverride(name = "bio", column = @Column(name = "buyer_bio", length = 500)),
        @AttributeOverride(name = "avatar", column = @Column(name = "buyer_avatar", length = 400000))
    })
    private CardFields buyer;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "name", column = @Column(name = "seller_name", length = 100)),
        @AttributeOverride(name = "bio", column = @Column(name = "seller_bio", length = 500)),
        @AttributeOverride(name = "avatar", column = @Column(name = "seller_avatar", length = 400000))
    })
    private CardFields seller;

    public TradingProfiles profiles() { return new TradingProfiles(separateProfiles, buyer.card(), seller.card()); }
    public void setProfiles(TradingProfiles profiles) {
        separateProfiles = profiles.separate();
        buyer = new CardFields(profiles.buyer());
        seller = new CardFields(profiles.seller());
    }
}
