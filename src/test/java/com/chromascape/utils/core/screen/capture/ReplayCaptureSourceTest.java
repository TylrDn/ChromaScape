package com.chromascape.utils.core.screen.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chromascape.utils.core.statistics.StatisticsManager;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayCaptureSourceTest {

  @TempDir Path dir;

  @BeforeEach
  void resetCycles() {
    StatisticsManager.reset();
  }

  static Path writeFrame(Path dir, String name, int w, int h, Color fill) throws IOException {
    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        image.setRGB(x, y, fill.getRGB());
      }
    }
    Path file = dir.resolve(name);
    ImageIO.write(image, "png", file.toFile());
    return file;
  }

  @Test
  void loadsPngsSortedByNameAndReportsBounds() throws IOException {
    writeFrame(dir, "02-second.png", 40, 30, Color.GREEN);
    writeFrame(dir, "01-first.png", 40, 30, Color.RED);
    Files.writeString(dir.resolve("notes.txt"), "ignored");

    ReplayCaptureSource source = new ReplayCaptureSource(dir);

    assertEquals(2, source.frameCount());
    assertEquals(new Rectangle(0, 0, 40, 30), source.getWindowBounds());
    assertEquals("01-first.png", source.currentFrameName());
    assertTrue(source.describe().contains("2 frame(s)"));
  }

  @Test
  void frameFollowsTheCycleCounterAndHoldsTheLastOne() throws IOException {
    writeFrame(dir, "01.png", 4, 4, Color.RED);
    writeFrame(dir, "02.png", 4, 4, Color.GREEN);
    ReplayCaptureSource source = new ReplayCaptureSource(dir);

    // Before the loop starts (cycles == 0) and on cycle 1 the first frame is served.
    assertEquals(Color.RED.getRGB(), source.captureWindow().getRGB(0, 0));
    StatisticsManager.incrementCycles();
    assertEquals(0, source.currentIndex());
    assertEquals(Color.RED.getRGB(), source.captureWindow().getRGB(0, 0));

    StatisticsManager.incrementCycles();
    assertEquals(1, source.currentIndex());
    assertEquals(Color.GREEN.getRGB(), source.captureWindow().getRGB(0, 0));

    StatisticsManager.incrementCycles();
    StatisticsManager.incrementCycles();
    assertEquals(1, source.currentIndex(), "last frame is held for every later cycle");
  }

  @Test
  void everyCaptureIsFreshImage() throws IOException {
    writeFrame(dir, "01.png", 4, 4, Color.RED);
    ReplayCaptureSource source = new ReplayCaptureSource(dir);
    BufferedImage a = source.captureWindow();
    BufferedImage b = source.captureWindow();
    assertNotSame(a, b, "a frame is a photograph; callers must not share one across captures");
    a.setRGB(0, 0, Color.BLUE.getRGB());
    assertEquals(Color.RED.getRGB(), source.captureWindow().getRGB(0, 0));
  }

  @Test
  void missingDirectoryNamesThePropertyToSet() {
    Path missing = dir.resolve("nope");
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> new ReplayCaptureSource(missing));
    assertTrue(e.getMessage().contains(ReplayCaptureSource.FIXTURES_PROPERTY));
  }

  @Test
  void emptyDirectoryIsRejected() {
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> new ReplayCaptureSource(dir));
    assertTrue(e.getMessage().contains("no .png"));
  }

  @Test
  void mixedSizesAreRejectedNamingBothFiles() throws IOException {
    writeFrame(dir, "01.png", 4, 4, Color.RED);
    writeFrame(dir, "02.png", 5, 4, Color.RED);
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> new ReplayCaptureSource(dir));
    assertTrue(e.getMessage().contains("01.png"));
    assertTrue(e.getMessage().contains("02.png"));
  }
}
