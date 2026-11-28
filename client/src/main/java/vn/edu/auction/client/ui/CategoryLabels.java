package vn.edu.auction.client.ui;

import vn.edu.auction.client.I18n;
import vn.edu.auction.shared.enums.ItemCategory;

public final class CategoryLabels {
    private CategoryLabels() {}

    public static String of(ItemCategory category) {
        return I18n.get("category." + (category == null ? ItemCategory.LIFE : category).name());
    }
}
