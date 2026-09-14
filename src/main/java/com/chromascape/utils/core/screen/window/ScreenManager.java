package com.chromascape.utils.core.screen.window;

import com.chromascape.utils.core.screen.capture.CaptureSource;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Utility class for capturing screen regions and retrieving window bounds. Screen capture utilities
 * are intended to be used with colour contour extraction and template matching.
 *
 * <p>FORK DIVERGENCE — the pixel source is a pluggable {@link CaptureSource} set by the controller
 * at start ({@link #setCaptureSource(CaptureSource)}) rather than a fixed RemoteInput buffer. The
 * public capture API is unchanged; every perception utility keeps calling {@link #captureWindow()}
 * and {@link #captureZone(Rectangle)} exactly as before.
 */
public class ScreenManager {

  private static volatile CaptureSource source;

  /**
   * Captures a {@link Rectangle} region on the client screen, intended to be used when
   * screenshotting zones for template matching and or colour extraction.
   *
   * @param zone The rectangle area in client relative screen co-ordinates
   * @return A {@link BufferedImage} of the captured area
   * @throws RuntimeException if no frame is available
   */
  public static BufferedImage captureZone(Rectangle zone) {
    BufferedImage screen = captureWindow();
    if (screen == null) {
      throw new RuntimeException("Screen could not be captured");
    }
    return screen.getSubimage(zone.x, zone.y, zone.width, zone.height);
  }

  /**
   * Grabs the latest rendered frame of the target application, regardless of if the client is
   * maximised, minimised, partially or fully covered. This is to be used with template matching and
   * {@link com.chromascape.utils.core.screen.topology.ChromaObj} detection.
   *
   * @return A {@link BufferedImage} of the client's screen, or {@code null} if the source has no
   *     frame yet
   * @throws IllegalStateException if no capture source has been set (the controller has not run
   *     {@code init()})
   */
  public static synchronized BufferedImage captureWindow() {
    return requireSource().captureWindow();
  }

  /**
   * Gets the bounds of the frame space the current capture source serves — for RemoteInput that is
   * the RuneLite AWT canvas, for replay it is the fixture size.
   *
   * @return A {@link Rectangle} with origin {@code (0, 0)} and the frame's width and height
   * @throws IllegalStateException if no capture source has been set
   */
  public static Rectangle getWindowBounds() {
    return requireSource().getWindowBounds();
  }

  /**
   * Installs the capture source every subsequent capture reads from.
   *
   * @param captureSource the source, or {@code null} to clear it at shutdown
   */
  public static void setCaptureSource(CaptureSource captureSource) {
    source = captureSource;
  }

  /**
   * Describes the active capture source for the startup banner.
   *
   * @return the source's own description, or {@code "none"} if no source is set
   */
  public static String describeCaptureSource() {
    CaptureSource current = source;
    return current == null ? "none" : current.describe();
  }

  private static CaptureSource requireSource() {
    CaptureSource current = source;
    if (current == null) {
      throw new IllegalStateException(
          "No CaptureSource is set: Controller.init() has not run, or shutdown() cleared it");
    }
    return current;
  }
}
