package com.chromascape.utils.core.screen.capture;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * Converts raw native frame buffers into {@link BufferedImage}s.
 *
 * <p>Both native capture paths (RemoteInput and ScreenCaptureKit) hand back a tightly packed {@code
 * width * height * 4} byte array in BGRA order. This helper wraps that array without copying it,
 * presenting the B, G, R bands as an opaque sRGB image; the alpha byte is ignored. Moved here from
 * {@code ScreenManager} so that both sources share exactly one conversion.
 */
public final class BgraFrames {

  private BgraFrames() {}

  /**
   * Wraps a BGRA byte array as an opaque RGB {@link BufferedImage}.
   *
   * @param pixels pixel data in {@code [B, G, R, A]} order, exactly {@code width * height * 4}
   *     bytes
   * @param width frame width in pixels, greater than zero
   * @param height frame height in pixels, greater than zero
   * @return an image backed by {@code pixels}; mutating the array mutates the image
   * @throws IllegalArgumentException if the array length does not match the dimensions
   */
  public static BufferedImage toBufferedImage(byte[] pixels, int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException(
          "Frame dimensions must be positive: " + width + "x" + height);
    }
    int expected = width * height * 4;
    if (pixels.length != expected) {
      throw new IllegalArgumentException(
          "BGRA buffer is "
              + pixels.length
              + " bytes, expected "
              + expected
              + " for "
              + width
              + "x"
              + height);
    }
    DataBufferByte buffer = new DataBufferByte(pixels, pixels.length);
    WritableRaster raster =
        Raster.createInterleavedRaster(
            buffer, width, height, width * 4, 4, new int[] {2, 1, 0}, null);

    ColorModel cm =
        new ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB),
            new int[] {8, 8, 8},
            false,
            false,
            Transparency.OPAQUE,
            DataBuffer.TYPE_BYTE);

    return new BufferedImage(cm, raster, false, null);
  }
}
