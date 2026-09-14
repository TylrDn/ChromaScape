package com.chromascape.utils.core.screen.capture;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * A source of client frames for perception.
 *
 * <p>Every fact this framework has about the game, it derives from a frame served by one of these.
 * The three implementations correspond to the three {@link
 * com.chromascape.foundation.RuntimeProfile} values:
 *
 * <ul>
 *   <li>{@link RemoteInputCaptureSource} — RemoteInput's in-process frame hook (upstream default).
 *   <li>{@link ScreenCaptureKitCaptureSource} — macOS ScreenCaptureKit via the Swift bridge.
 *   <li>{@link ReplayCaptureSource} — recorded PNG frames from disk, no client required.
 * </ul>
 *
 * <p>This interface exists because {@code ScreenManager.captureWindow()} already branched between
 * two frame sources ({@code ScreenManager.java:60-63} and {@code :88-100} before this refactor);
 * the third source made the branch a seam.
 */
public interface CaptureSource extends AutoCloseable {

  /**
   * Returns the most recent frame, or {@code null} if no frame is available yet.
   *
   * <p>A frame is a photograph: callers take one per decision and never cache it across cycles.
   * Implementations return a fresh {@link BufferedImage} on every call.
   *
   * @return the latest frame, or {@code null} if the source has not produced one yet
   */
  BufferedImage captureWindow();

  /**
   * Returns the bounds of the frame space, with origin {@code (0, 0)}. Every zone in the framework
   * is computed relative to this rectangle.
   *
   * @return the frame bounds; width and height are {@code 0} if the source has produced no frame
   */
  Rectangle getWindowBounds();

  /**
   * One line for the startup banner and the log, naming the mechanism behind this source.
   *
   * @return a short human-readable description
   */
  String describe();

  /** Releases whatever the source holds. Must be safe to call more than once. */
  @Override
  void close();
}
