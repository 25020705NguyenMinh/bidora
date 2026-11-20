package vn.edu.auction.client;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.Property;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tab;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TextInputControl;

/** FXML uses attached properties such as I18n.caption="wallet.balance". */
public final class I18n {
    private static final String BASE = "vn.edu.auction.client.i18n.messages";
    public static final Locale VIETNAMESE = Locale.forLanguageTag("vi");
    private static final ObjectProperty<Locale> locale = new SimpleObjectProperty<>(initialLocale());

    private record Caption(Supplier<String> render, String displayed) {}
    private static final Map<Property<String>, Caption> captions = new WeakHashMap<>();

    private I18n() {}

    private static Locale initialLocale() {
        try {
            return "en".equals(Preferences.userNodeForPackage(I18n.class).get("language", "vi"))
                    ? Locale.ENGLISH : VIETNAMESE;
        } catch (SecurityException ignored) {
            return VIETNAMESE;
        }
    }

    public static Locale getLocale() { return locale.get(); }
    public static boolean isVietnamese() { return VIETNAMESE.equals(getLocale()); }
    public static ResourceBundle getBundle() { return ResourceBundle.getBundle(BASE, getLocale()); }
    public static String get(String key) { return getBundle().getString(key); }
    public static String get(String key, Object... args) {
        return new MessageFormat(get(key), getLocale()).format(args);
    }

    public static void setLocale(Locale selected) {
        Locale next = Locale.ENGLISH.getLanguage().equals(selected.getLanguage()) ? Locale.ENGLISH : VIETNAMESE;
        if (next.equals(getLocale())) return;
        locale.set(next);
        for (Property<String> property : new ArrayList<>(captions.keySet())) {
            Caption caption = captions.get(property);
            if (caption == null) continue;
            if (property.isBound() || !caption.displayed().equals(property.getValue())) captions.remove(property);
            else track(property, caption.render());
        }
        try { Preferences.userNodeForPackage(I18n.class).put("language", next.getLanguage()); }
        catch (SecurityException ignored) { /* The current session still uses the chosen language. */ }
    }

    /** Keep the callback alive for exactly as long as its view. */
    public static void onChange(Node owner, Runnable update) {
        InvalidationListener listener = ignored -> {
            locale.get();
            update.run();
        };
        owner.getProperties().put(listener, listener);
        locale.addListener(new WeakInvalidationListener(listener));
    }

    /** Translate in place. An explicit assignment from a controller supersedes the FXML caption. */
    public static void localize(Property<String> property, String key, Object... args) {
        track(property, () -> args.length == 0 ? get(key) : get(key, args));
    }

    public static void track(Property<String> property, Supplier<String> render) {
        String text = render.get();
        captions.put(property, new Caption(render, text));
        property.setValue(text);
    }

    public static <T extends Labeled> T caption(T control, String key) {
        localize(control.textProperty(), key);
        return control;
    }

    public static MenuItem menuItem(String key) {
        var item = new MenuItem();
        localize(item.textProperty(), key);
        return item;
    }

    public static void setCaption(Object target, String key) {
        if (target instanceof Labeled label) localize(label.textProperty(), key);
        else if (target instanceof Tab tab) localize(tab.textProperty(), key);
        else if (target instanceof TableColumnBase<?, ?> column) localize(column.textProperty(), key);
        else throw new IllegalArgumentException("Unsupported caption on " + target.getClass());
    }

    public static void setPrompt(TextInputControl target, String key) { localize(target.promptTextProperty(), key); }
    public static void setAccessible(Node target, String key) { localize(target.accessibleTextProperty(), key); }
}
