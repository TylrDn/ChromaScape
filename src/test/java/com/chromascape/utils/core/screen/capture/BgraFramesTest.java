package com.chromascape.utils.core.screen.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class BgraFramesTest {

  @Test
  void bgraBytesComeOutAsTheRightColours() {
    // 2x1 frame: pixel 0 is pure red, pixel 1 is pure blue, both written in BGRA order.
    byte[] bgra = {
      0,
      0,
      (byte) 255,
      (byte) 255, // B=0 G=0 R=255 A=255
      (byte) 255,
      0,
      0,
      (byte) 255 // B=255 G=0 R=0 A=255
    };
    BufferedImage image = BgraFrames.toBufferedImage(bgra, 2, 1);
    assertEquals(2, image.getWidth());
    assertEquals(1, image.getHeight());
    assertEquals(Color.RED.getRGB(), image.getRGB(0, 0));
    assertEquals(Color.BLUE.getRGB(), image.getRGB(1, 0));
  }

  @Test
  void wrongLengthIsRejectedWithTheExpectedSizeInTheMessage() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> BgraFrames.toBufferedImage(new byte[7], 2, 1));
    assertEquals(true, e.getMessage().contains("expected 8"));
  }

  @Test
  void nonPositiveDimensionsAreRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> BgraFrames.toBufferedImage(new byte[0], 0, 1));
  }
}
