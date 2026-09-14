package com.chromascape.utils.core.screen.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.chromascape.utils.core.screen.capture.ReplayCaptureSource;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScreenManagerTest {

  @TempDir Path dir;

  @AfterEach
  void clearSource() {
    ScreenManager.setCaptureSource(null);
  }

  @Test
  void withoutSourceEveryCaptureFailsLoudly() {
    ScreenManager.setCaptureSource(null);
    assertThrows(IllegalStateException.class, ScreenManager::captureWindow);
    assertThrows(IllegalStateException.class, ScreenManager::getWindowBounds);
    assertEquals("none", ScreenManager.describeCaptureSource());
  }

  @Test
  void delegatesCaptureBoundsAndZonesToTheSource() throws IOException {
    BufferedImage frame = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < 10; y++) {
      for (int x = 0; x < 20; x++) {
        frame.setRGB(x, y, Color.MAGENTA.getRGB());
      }
    }
    ImageIO.write(frame, "png", dir.resolve("01.png").toFile());
    ScreenManager.setCaptureSource(new ReplayCaptureSource(dir));

    assertEquals(new Rectangle(0, 0, 20, 10), ScreenManager.getWindowBounds());
    BufferedImage zone = ScreenManager.captureZone(new Rectangle(5, 2, 4, 3));
    assertEquals(4, zone.getWidth());
    assertEquals(3, zone.getHeight());
    assertEquals(Color.MAGENTA.getRGB(), zone.getRGB(0, 0));
    assertEquals(true, ScreenManager.describeCaptureSource().startsWith("replay"));
  }
}
