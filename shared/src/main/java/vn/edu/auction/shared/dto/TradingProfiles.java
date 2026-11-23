package vn.edu.auction.shared.dto;

/** The buyer profile also serves as the shared profile when separate is false. */
public record TradingProfiles(boolean separate, Card buyer, Card seller) {
    public record Card(String name, String bio, String avatar) {}
    public Card publicCard(boolean selling) { return selling && separate ? seller : buyer; }
}
