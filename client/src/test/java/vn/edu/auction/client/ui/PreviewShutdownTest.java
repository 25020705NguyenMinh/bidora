package vn.edu.auction.client.ui;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.auction.client.Main;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class PreviewShutdownTest {
    @TempDir Path temporary;

    @Test void closingMainWindowExitsPreviewWithAndWithoutChat() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "JavaFX requires a display");
        for (boolean withChat : List.of(false, true)) {
            Path log = temporary.resolve("shutdown-" + withChat + ".log");
            String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                    "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                    PreviewShutdownTest.class.getName(), "--preview"));
            if (withChat) command.add("--with-chat");
            Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Preview stayed alive after close: " + Files.readString(log));
                assertEquals(0, process.exitValue(), Files.readString(log));
                assertTrue(Files.readString(log).contains("PREVIEW_CLOSE_REQUESTED"));
                String startup = Files.readAllLines(log).stream()
                        .filter(line -> line.startsWith("Bidora.")).findFirst().orElseThrow();
                assertTrue(startup.startsWith("Bidora. xem thu cuc bo tai 127.0.0.1:"), startup);
                assertTrue(startup.chars().allMatch(character -> character < 128), startup);
            } finally {
                if (process.isAlive()) process.destroyForcibly().waitFor();
            }
        }
    }

    public static void main(String[] args) { Application.launch(Probe.class, args); }

    public static class Probe extends Main {
        @Override public void start(Stage stage) throws Exception {
            super.start(stage);
            if (getParameters().getRaw().contains("--with-chat")) ChatWindow.toggle(stage);
            PauseTransition close = new PauseTransition(Duration.millis(800));
            close.setOnFinished(event -> {
                System.out.println("PREVIEW_CLOSE_REQUESTED");
                stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
            });
            close.play();
        }
    }
}
