package vn.edu.auction.client.ui;

import com.sun.net.httpserver.HttpServer;
import java.awt.GraphicsEnvironment;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import vn.edu.auction.client.I18n;
import vn.edu.auction.client.auth.RegisterData;
import vn.edu.auction.client.model.SessionData;
import vn.edu.auction.shared.dto.AuthResponse;
import vn.edu.auction.shared.enums.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class AccountUiTest {
    private static HttpServer api;
    private static String oldUrl;
    private static final Map<String, String> requests = new ConcurrentHashMap<>();
    private static Locale originalLanguage;
    private static boolean originalTheme;
    private static boolean started;

    @BeforeAll static void startJavaFx() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "JavaFX needs a display (CI uses Xvfb)");
        api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/api/", ex -> {
            String path = ex.getRequestURI().getPath();
            requests.put(path, new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String json = switch (path) {
                case "/api/auctions", "/api/items", "/api/me/notifications", "/api/users/directory" -> "[]";
                case "/api/me/trading-profiles" -> "{\"separate\":true,\"buyer\":{\"name\":\"Buyer An\",\"bio\":\"\",\"avatar\":\"\"},\"seller\":{\"name\":\"Seller An\",\"bio\":\"\",\"avatar\":\"\"}}";
                case "/api/auth/register" -> "{\"id\":1,\"role\":\"BIDDER\"}";
                case "/api/me/profile" -> "{\"id\":1,\"fullName\":\"An\",\"email\":\"an@example.test\",\"role\":\"BIDDER\"}";
                default -> "{\"message\":\"OK\"}";
            };
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        api.start();
        oldUrl = System.setProperty("auction.server.url", "http://127.0.0.1:" + api.getAddress().getPort());
        Platform.startup(() -> Platform.setImplicitExit(false));
        started = true;
        originalLanguage = I18n.getLocale();
        originalTheme = UiTheme.isDark();
        fx(() -> { I18n.setLocale(I18n.VIETNAMESE); return null; });
    }

    @AfterAll static void stopJavaFx() throws Exception {
        if (!started) return;
        fx(() -> {
            List.copyOf(Window.getWindows()).forEach(Window::hide);
            UiTheme.setDark(originalTheme);
            I18n.setLocale(originalLanguage);
            return null;
        });
        Platform.exit();
        api.stop(0);
        if (oldUrl == null) System.clearProperty("auction.server.url");
        else System.setProperty("auction.server.url", oldUrl);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(8, TimeUnit.SECONDS);
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            if (fx(condition)) return;
            Thread.sleep(40);
        } while (System.nanoTime() < deadline);
        fail("Timed out waiting for JavaFX navigation");
    }

    private static Stage window(String title) {
        return Window.getWindows().stream().filter(Stage.class::isInstance).map(Stage.class::cast)
                .filter(s -> s.isShowing() && title.equals(s.getTitle())).findFirst().orElse(null);
    }

    @Test void marketSwitchesLanguageAndThemeWithoutMovingNavigationOrClearingSearch() throws Exception {
        try {
            Stage market = fx(() -> {
                I18n.setLocale(I18n.VIETNAMESE);
                Parent root = FXMLLoader.load(getClass().getResource("/vn/edu/auction/client/fxml/browse/browse-view.fxml"), I18n.getBundle());
                Stage stage = new Stage();
                stage.setX(-3000);
                Scene scene = new Scene(root, 1120, 720);
                UiTheme.apply(scene);
                stage.setScene(scene);
                stage.show();
                return stage;
            });
            await(() -> !market.getScene().lookup("#auctionSearch").isDisabled());
            fx(() -> {
                Parent root = market.getScene().getRoot();
                var query = (TextField) root.lookup("#auctionSearch");
                query.setText("Fujifilm X100");
                ((Button) root.lookup("#languageButton")).fire();
                assertEquals("Search by item name", query.getPromptText());
                assertEquals("Fujifilm X100", query.getText());
                assertEquals("Auctions", ((TabPane) root.lookup("#browseTabs")).getTabs().getFirst().getText());
                assertEquals("Cameras & lenses", ((Label) root.lookup("#title")).getText());
                assertEquals("Browse auctions", ((Button) root.lookup("#action")).getText());
                assertEquals("Sign in", ((Button) root.lookup(".account-login")).getText());
                root.applyCss();
                root.layout();
                ToggleButton toggle = (ToggleButton) root.lookup("#themeButton");
                Node account = root.lookup("#userArea");
                var position = account.localToScene(account.getLayoutBounds());
                var bounds = toggle.localToScene(toggle.getLayoutBounds());
                toggle.fire();
                root.applyCss();
                root.layout();
                assertEquals(bounds, toggle.localToScene(toggle.getLayoutBounds()));
                assertEquals(position, account.localToScene(account.getLayoutBounds()));
                assertSame(root, market.getScene().getRoot());
                assertEquals(1, overlays(root));
                toggle.fire();
                assertEquals(1, overlays(root));
                return null;
            });
            await(() -> overlays(market.getScene().getRoot()) == 0);
            fx(() -> { market.close(); return null; });
        } finally {
            fx(() -> { I18n.setLocale(I18n.VIETNAMESE); return null; });
        }
    }

    @Test void sellerCanSwitchLanguagesWithAnUnfinishedListing() throws Exception {
        fx(() -> {
            Parent view = FXMLLoader.load(getClass().getResource("/vn/edu/auction/client/fxml/item-management-view.fxml"), I18n.getBundle());
            Scene scene = new Scene(view);
            UiTheme.apply(scene);
            view.applyCss();
            view.layout();
            var name = (TextField) view.lookup("#nameField");
            var description = (TextArea) view.lookup("#descriptionArea");
            var table = (TableView<?>) view.lookup("#itemTable");
            name.setText("Đồng hồ của Minh");
            description.setText("Keep my punctuation!");
            try {
                ((Button) view.lookup("#languageButton")).fire();
                assertEquals("Sell an item", ((Label) view.lookup("#titleLabel")).getText());
                assertEquals("ITEM", table.getColumns().get(2).getText());
                assertEquals("e.g. Fujifilm camera", name.getPromptText());
                assertEquals("Đồng hồ của Minh", name.getText());
                assertEquals("Keep my punctuation!", description.getText());
            } finally {
                I18n.setLocale(I18n.VIETNAMESE);
            }
            return null;
        });
    }

    @Test void animatedDialogKeepsTheSelectedResult() throws Exception {
        Dialog<ButtonType> dialog = fx(() -> {
            Dialog<ButtonType> d = new Dialog<>();
            d.getDialogPane().getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);
            UiTheme.apply(d.getDialogPane());
            UiMotion.install(d);
            d.show();
            return d;
        });
        fx(() -> {
            ((Button) dialog.getDialogPane().lookupButton(ButtonType.YES)).fire();
            return null;
        });
        await(() -> !dialog.isShowing());
        assertEquals(ButtonType.YES, fx(dialog::getResult));
    }

    @Test void splitProfileAndLanguageSwitchKeepNavigationInPlace() throws Exception {
        Stage market = fx(() -> {
            SessionData.getInstance().setSession(new AuthResponse(1L, "An", "an@example.test", "local-token", UserRole.BIDDER));
            Parent root = FXMLLoader.load(getClass().getResource("/vn/edu/auction/client/fxml/browse/browse-view.fxml"), I18n.getBundle());
            Stage stage = new Stage();
            stage.setX(-3000);
            Scene scene = new Scene(root, 1120, 760);
            UiTheme.apply(scene);
            stage.setScene(scene);
            stage.show();
            return stage;
        });
        try {
            await(() -> {
                var menu = (MenuButton) market.getScene().getRoot().lookup(".menu-button");
                return menu != null && menu.getText().equals("Buyer An") && market.getScene().getRoot().getScaleX() == 1;
            });
            fx(() -> {
                Parent root = market.getScene().getRoot();
                var menu = (MenuButton) root.lookup(".menu-button");
                root.applyCss(); root.layout();
                var toggle = root.lookup("#themeButton");
                var position = toggle.localToScene(toggle.getLayoutBounds());
                I18n.setLocale(Locale.ENGLISH);
                root.applyCss(); root.layout();
                assertEquals(position, toggle.localToScene(toggle.getLayoutBounds()));
                var swap = menu.getItems().stream().filter(item -> "switchTradingProfile".equals(item.getId())).findFirst().orElseThrow();
                assertTrue(swap.isVisible());
                swap.fire();
                root.applyCss(); root.layout();
                assertEquals("Seller An", menu.getText());
                assertEquals(position, toggle.localToScene(toggle.getLayoutBounds()));
                assertEquals("local-token", SessionData.getInstance().getToken());
                swap.fire();
                assertEquals("Buyer An", menu.getText());
                return null;
            });
        } finally {
            fx(() -> {
                market.hide();
                SessionData.getInstance().clearSession();
                I18n.setLocale(I18n.VIETNAMESE);
                return null;
            });
        }
    }

    @Test void closingWindowFadesNativeSurfaceAndKeepsContentOpaque() throws Exception {
        Stage stage = fx(() -> {
            var root = new javafx.scene.layout.VBox(new Label("Window"));
            Stage window = new Stage();
            Scene scene = new Scene(root, 240, 160);
            UiTheme.apply(scene);
            window.setScene(scene);
            window.setX(-3000);
            window.show();
            return window;
        });
        await(() -> stage.getScene().getRoot().getScaleX() == 1);
        fx(() -> {
            var root = (javafx.scene.layout.Region) stage.getScene().getRoot();
            assertEquals(root.getBackground().getFills().getFirst().getFill(), stage.getScene().getFill());
            UiMotion.closeWindow(root, stage::hide);
            assertEquals(1, root.getOpacity());
            return null;
        });
        await(() -> !stage.isShowing());
    }

    @Test void textAreaAndProfileTabsUseThemedSkinSurfaces() throws Exception {
        boolean original = fx(UiTheme::isDark);
        Stage owner = fx(() -> {
            SessionData.getInstance().setSession(new AuthResponse(1L, "An", "an@example.test", "local-token", UserRole.BIDDER));
            Parent root = FXMLLoader.load(getClass().getResource("/vn/edu/auction/client/fxml/item-management-view.fxml"), I18n.getBundle());
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1120, 820));
            stage.setX(-3000);
            UiTheme.apply(stage.getScene());
            stage.show();
            TradingProfileDialog.edit(stage);
            return stage;
        });
        try {
            await(() -> Window.getWindows().stream().anyMatch(window -> window != owner && window.isShowing()
                    && window.getScene().lookup(".profile-tabs") != null));
            fx(() -> {
                for (boolean dark : List.of(false, true)) {
                    UiTheme.setDark(dark);
                    Parent root = owner.getScene().getRoot();
                    root.applyCss();
                    root.layout();
                    var area = (TextArea) root.lookup("#descriptionArea");
                    var content = (javafx.scene.layout.Region) area.lookup(".content");
                    assertTransparent(content);
                    assertTransparent((javafx.scene.layout.Region) area.lookup(".viewport"));
                    Window profile = Window.getWindows().stream().filter(window -> window != owner && window.isShowing()
                            && window.getScene().lookup(".profile-tabs") != null).findFirst().orElseThrow();
                    profile.getScene().getRoot().applyCss();
                    profile.getScene().getRoot().layout();
                    var tabs = (TabPane) profile.getScene().lookup(".profile-tabs");
                    assertTransparent((javafx.scene.layout.Region) tabs.lookup(".tab"));
                    assertEquals(tabs.getTabs().size(), 2);
                    var check = (CheckBox) profile.getScene().lookup(".profile-separation");
                    assertTrue(check.isSelected());
                    var mark = (javafx.scene.layout.Region) check.lookup(".box");
                    assertFalse(mark.getBackground().getFills().isEmpty());
                }
                return null;
            });
        } finally {
            fx(() -> {
                List.copyOf(Window.getWindows()).stream().filter(window -> window instanceof Stage stage
                        && stage.getOwner() == owner).forEach(Window::hide);
                owner.hide();
                UiTheme.setDark(original);
                SessionData.getInstance().clearSession();
                return null;
            });
        }
    }

    @Test void chatOpeningAnimatesTheNativeWindowWithoutFadingItsSurface() throws Exception {
        Stage owner = fx(() -> {
            SessionData.getInstance().setSession(new AuthResponse(1L, "An", "an@example.test", "local-test-token", UserRole.BIDDER));
            Stage window = new Stage();
            window.setX(-3000);
            Scene scene = new Scene(new javafx.scene.layout.VBox(), 800, 640);
            UiTheme.apply(scene);
            window.setScene(scene);
            window.show();
            return window;
        });
        try {
            Stage chat = fx(() -> {
                ChatWindow.toggle(owner);
                Stage opened = window(I18n.get("chat.title"));
                assertNotNull(opened);
                assertEquals(1, opened.getScene().getRoot().getOpacity());
                assertEquals(0, opened.getOpacity());
                var body = opened.getScene().getRoot().lookup(".chat-body");
                assertEquals(.97, body.getScaleX());
                return opened;
            });
            await(() -> chat.getOpacity() == 1 && chat.getScene().getRoot().lookup(".chat-body").getScaleX() == 1);
            fx(() -> {
                ChatWindow.toggle(owner);
                return null;
            });
            await(() -> !chat.isShowing());
        } finally {
            fx(() -> {
                ChatWindow.close();
                owner.hide();
                SessionData.getInstance().clearSession();
                return null;
            });
        }
    }

    private static void assertTransparent(javafx.scene.layout.Region region) {
        assertNotNull(region);
        if (region.getBackground() != null) {
            assertTrue(region.getBackground().getFills().stream().allMatch(fill ->
                    fill.getFill() instanceof javafx.scene.paint.Color color && color.getOpacity() == 0));
        }
    }

    @Test void guestsCanOpenChatAndNotificationSignInPromptsWithoutPrivateRequests() throws Exception {
        try {
            Stage owner = fx(() -> {
                SessionData.getInstance().clearSession();
                I18n.setLocale(Locale.ENGLISH);
                requests.clear();
                var bar = new javafx.scene.layout.HBox();
                UserArea.render(bar, () -> {});
                Stage stage = new Stage();
                stage.setX(-3000);
                Scene scene = new Scene(bar, 560, 90);
                UiTheme.apply(scene);
                stage.setScene(scene);
                stage.show();
                assertNotNull(bar.lookup("#notificationsButton"));
                assertNotNull(bar.lookup("#guestChat"));
                ((Button) bar.lookup("#guestChat")).fire();
                return stage;
            });
            await(() -> window(I18n.get("chat.title")) != null);
            fx(() -> {
                Stage chat = window(I18n.get("chat.title"));
                assertNotNull(chat.getScene().getRoot().lookup(".primary-button"));
                assertNull(chat.getScene().getRoot().lookup(".chat-composer"));
                ((Button) ((DialogPane) chat.getScene().getRoot()).lookupButton(ButtonType.CLOSE)).fire();
                return null;
            });
            await(() -> window(I18n.get("chat.title")) == null);
            fx(() -> {
                ((Button) owner.getScene().lookup("#notificationsButton")).fire();
                return null;
            });
            await(() -> window(I18n.get("notice.title")) != null);
            fx(() -> {
                Stage notice = window(I18n.get("notice.title"));
                ((Button) notice.getScene().getRoot().lookup(".primary-button")).fire();
                return null;
            });
            await(() -> window(I18n.get("title.login")) != null);
            assertFalse(requests.containsKey("/api/me/notifications"));
            assertFalse(requests.containsKey("/api/users/directory"));
        } finally {
            fx(() -> {
                List.copyOf(Window.getWindows()).forEach(Window::hide);
                SessionData.getInstance().clearSession();
                I18n.setLocale(I18n.VIETNAMESE);
                return null;
            });
        }
    }

    private static long overlays(Parent root) {
        return root.getChildrenUnmodifiable().stream()
                .filter(n -> n instanceof javafx.scene.image.ImageView && !n.isManaged()).count();
    }

    @Test void registerOpensOtpAndProfileRecoveryReturnsToTheSameSession() throws Exception {
        try {
            Stage main = fx(() -> {
                RegisterData.email = "an@example.test";
                Parent root = FXMLLoader.load(getClass().getResource("/vn/edu/auction/client/fxml/auth/register-view.fxml"), I18n.getBundle());
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                stage.setX(-3000);
                stage.show();
                ((TextField)root.lookup("#firstNameField")).setText("Nguyen");
                ((TextField)root.lookup("#lastNameField")).setText("An");
                ((TextField)root.lookup("#passwordField")).setText("local-test");
                @SuppressWarnings("unchecked")
                ComboBox<String> language = (ComboBox<String>) root.lookup("#languageComboBox");
                language.setValue("English");
                assertSame(root, stage.getScene().getRoot());
                assertEquals("Create an account", ((Button) root.lookup("#registerButton")).getText());
                assertEquals("local-test", ((TextField) root.lookup("#passwordField")).getText());
                assertEquals("Nguyen", ((TextField) root.lookup("#firstNameField")).getText());
                I18n.setLocale(I18n.VIETNAMESE);
                ((Button)root.lookup("#registerButton")).fire();
                return stage;
            });
            await(() -> main.getScene().lookup("#otp1") != null);
            assertTrue(requests.get("/api/auth/register").contains("an@example.test"));
            fx(() -> {
                RegisterData.reset();
                SessionData.getInstance().setSession(new AuthResponse(1L, "An", "an@example.test", "local-test-token", UserRole.BIDDER));
                ProfileDialog.show(main, () -> {});
                return null;
            });
            await(() -> {
                Stage p = window("Thông tin tài khoản");
                if (p == null) return false;
                var link = p.getScene().getRoot().lookupAll(".hyperlink").stream().map(Hyperlink.class::cast)
                        .filter(b -> b.getText().equals("Quên mật khẩu?")).findFirst();
                if (link.isEmpty() || link.get().isDisabled()) return false;
                link.get().fire();
                return true;
            });
            Stage reset = fx(() -> window(I18n.get("title.reset")));
            assertNotNull(reset);
            fx(() -> {
                var email = (TextField)reset.getScene().lookup("#emailField");
                assertEquals("an@example.test", email.getText());
                assertFalse(email.isEditable());
                ((Button)reset.getScene().lookup("#submitButton")).fire();
                return null;
            });
            await(() -> reset.getScene().lookup("#otp1") != null);
            fx(() -> {
                for (int i = 1; i <= 6; i++) ((TextField)reset.getScene().lookup("#otp" + i)).setText(String.valueOf(i));
                ((Button)reset.getScene().lookup("#continueButton")).fire();
                return null;
            });
            await(() -> reset.getScene().lookup("#confirmPasswordField") != null);
            fx(() -> {
                ((TextField)reset.getScene().lookup("#passwordField")).setText("changed-test");
                ((TextField)reset.getScene().lookup("#confirmPasswordField")).setText("changed-test");
                ((Button)reset.getScene().lookup("#submitButton")).fire();
                return null;
            });
            await(() -> !reset.isShowing() && window("Thông tin tài khoản") != null);
            assertTrue(requests.get("/api/auth/reset-password").contains("123456"));
            assertEquals("local-test-token", SessionData.getInstance().getToken());
        } finally {
            fx(() -> {
                List.copyOf(Window.getWindows()).forEach(Window::hide);
                SessionData.getInstance().clearSession();
                RegisterData.reset();
                return null;
            });
        }
    }
}
