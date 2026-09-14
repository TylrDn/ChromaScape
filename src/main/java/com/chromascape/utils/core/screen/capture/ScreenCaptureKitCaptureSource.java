package com.chromascape.utils.core.screen.capture;

import com.chromascape.utils.core.screen.capturekit.ScreenCaptureBridge;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Frames from macOS ScreenCaptureKit via the Swift {@link ScreenCaptureBridge}.
 *
 * <p>The stream delivers frames asynchronously, so there is a window after {@code CSC_StartCapture}
 * returns during which no frame exists yet. {@link #getWindowBounds()} reports {@code 0x0} in that
 * window; callers that need real bounds first (the zone manager does) should use {@link
 * #awaitFirstFrame(Duration)}.
 *
 * <p><b>Unverified against a live client:</b> ScreenCaptureKit captures the OS window including
 * chrome, at backing-store (Retina, 2x) resolution, whereas RemoteInput reports the Java canvas at
 * logical resolution. Zone and click coordinates derived from this source may not line up with
 * RemoteInput's input coordinate space until that offset and scale are measured and corrected.
 */
public final class ScreenCaptureKitCaptureSource implements CaptureSource {

  private static final Logger logger = LogManager.getLogger(ScreenCaptureKitCaptureSource.class);
  private static final long FIRST_FRAME_POLL_MILLIS = 50;

  private final ScreenCaptureBridge bridge;
  private volatile Rectangle lastBounds = new Rectangle(0, 0, 0, 0);

  /**
   * Creates a source over a bridge whose capture has already started.
   *
   * @param bridge the started bridge; closed by {@link #close()}
   */
  public ScreenCaptureKitCaptureSource(ScreenCaptureBridge bridge) {
    this.bridge = bridge;
  }

  @Override
  public BufferedImage captureWindow() {
    ScreenCaptureBridge.CaptureFrame frame = bridge.getImageBuffer();
    if (frame == null || frame.width() <= 0 || frame.height() <= 0) {
      return null;
    }
    lastBounds = new Rectangle(0, 0, frame.width(), frame.height());
    int size = frame.width() * frame.height() * 4;
    return BgraFrames.toBufferedImage(
        frame.buffer().getByteArray(0, size), frame.width(), frame.height());
  }

  /**
   * Blocks until the stream has delivered at least one frame, or the deadline passes.
   *
   * @param timeout how long to wait in total
   * @return {@code true} if a frame arrived and {@link #getWindowBounds()} is now real, {@code
   *     false} if the deadline passed with no frame
   */
  public boolean awaitFirstFrame(Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (captureWindow() != null) {
        logger.info(
            "ScreenCaptureKit delivered first frame: {}x{}", lastBounds.width, lastBounds.height);
        return true;
      }
      try {
        Thread.sleep(FIRST_FRAME_POLL_MILLIS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    logger.error("ScreenCaptureKit stream delivered no frame within {}", timeout);
    return false;
  }

  @Override
  public Rectangle getWindowBounds() {
    return new Rectangle(lastBounds);
  }

  /**
   * Refuses a live run whose clicks would land in the wrong place.
   *
   * <p>Perception produces points in <em>frame</em> space (this source's bounds); the input device
   * consumes points in <em>input</em> space (RemoteInput's canvas). When the two sizes differ — as
   * they do today, 2x Retina window versus logical canvas — every live click is scaled and offset
   * wrong, and a dry run cannot reveal it because a dry run never clicks. So the mismatch is
   * checked at start: in dry-run it is logged at {@code WARN}; with dry-run off it is fatal.
   *
   * @param frameSpace this source's bounds after the first frame
   * @param inputSpace the input device's target dimensions
   * @param dryRun whether {@code ExecutionGate} is currently suppressing input
   * @throws IllegalStateException if the spaces differ and dry-run is off
   */
  public static void requireInputSpaceMatch(
      Rectangle frameSpace, Rectangle inputSpace, boolean dryRun) {
    boolean matches =
        frameSpace.width == inputSpace.width && frameSpace.height == inputSpace.height;
    if (matches) {
      logger.info(
          "capturekit frame space {}x{} matches input space", frameSpace.width, frameSpace.height);
      return;
    }
    String detail =
        "capturekit frame space is "
            + frameSpace.width
            + "x"
            + frameSpace.height
            + " but RemoteInput's input space is "
            + inputSpace.width
            + "x"
            + inputSpace.height
            + "; clicks computed from frames would land at the wrong scale/offset"
            + " (see docs/reference/live-readiness.md, Retina / HiDPI)";
    if (dryRun) {
      logger.warn("{} - allowed because dry-run is on", detail);
      return;
    }
    throw new IllegalStateException("LIVE RUN REFUSED: " + detail);
  }

  @Override
  public String describe() {
    return "capturekit (macOS ScreenCaptureKit via ScreenCaptureBridge)";
  }

  @Override
  public void close() {
    bridge.close();
  }
}
