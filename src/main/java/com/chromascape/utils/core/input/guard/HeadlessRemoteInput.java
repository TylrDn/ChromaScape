package com.chromascape.utils.core.input.guard;

import com.chromascape.utils.core.input.remoteinput.MouseButton;
import com.chromascape.utils.core.input.remoteinput.RemoteInput;
import com.sun.jna.Pointer;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link RemoteInput} with no native library and no target process.
 *
 * <p>Used by the {@code replay} runtime profile so that {@code Controller.init()} can build the
 * mouse and keyboard utilities without a RuneLite process to pair with. It keeps a virtual mouse
 * position and the set of held keys and buttons, so the framework's own state tracking behaves as
 * it does against a real device; nothing leaves the JVM.
 *
 * <p>Every input call goes through an {@link InputAudit}: suppressed and logged as {@code UNGATED}
 * under dry-run, recorded and logged otherwise. Perception entry points ({@link #getImageBuffer()},
 * {@link #getDebugImageBuffer()}) throw, because in this profile frames come from {@link
 * com.chromascape.utils.core.screen.capture.ReplayCaptureSource}, never from the device.
 */
public final class HeadlessRemoteInput extends RemoteInput {

  private static final Logger logger = LogManager.getLogger(HeadlessRemoteInput.class);

  private final Rectangle bounds;
  private final InputAudit audit = new InputAudit("headless");
  private final Set<Integer> heldKeys = Collections.synchronizedSet(new HashSet<>());
  private final Set<MouseButton> heldButtons =
      Collections.synchronizedSet(EnumSet.noneOf(MouseButton.class));
  private volatile Point mouse = new Point(0, 0);

  /**
   * Creates a headless device whose window is {@code bounds}.
   *
   * <p>The mouse starts at {@code (0, 0)}. ({@code VirtualMouseUtils} treats that as "never paired"
   * but then re-reads the device position anyway, so a replay run starts at the origin; the first
   * gated move takes it from there.)
   *
   * @param bounds the frame bounds; width and height must be positive
   */
  public HeadlessRemoteInput(Rectangle bounds) {
    super();
    if (bounds.width <= 0 || bounds.height <= 0) {
      throw new IllegalArgumentException("Headless bounds must be positive: " + bounds);
    }
    this.bounds = new Rectangle(0, 0, bounds.width, bounds.height);
    logger.info("Headless input device created for {}x{} frame space", bounds.width, bounds.height);
  }

  /**
   * The audit counting calls that reached this device.
   *
   * @return the audit
   */
  public InputAudit audit() {
    return audit;
  }

  @Override
  public Pointer getImageBuffer() {
    throw new UnsupportedOperationException(
        "Headless device has no frame buffer; frames come from the CaptureSource");
  }

  @Override
  public Pointer getDebugImageBuffer() {
    throw new UnsupportedOperationException(
        "Headless device has no debug frame buffer; frames come from the CaptureSource");
  }

  @Override
  public Point getMousePosition() {
    return new Point(mouse);
  }

  @Override
  public Rectangle getTargetDimensions() {
    return new Rectangle(bounds);
  }

  @Override
  public void holdKey(int javaKeyCode) {
    if (!audit.suppress("holdKey(" + javaKeyCode + ")", false)) {
      heldKeys.add(javaKeyCode);
    }
  }

  @Override
  public boolean isKeyHeld(int javaKeyCode) {
    return heldKeys.contains(javaKeyCode);
  }

  @Override
  public void releaseKey(int javaKeyCode) {
    if (!audit.suppress("releaseKey(" + javaKeyCode + ")", false)) {
      heldKeys.remove(javaKeyCode);
    }
  }

  @Override
  public void holdMouse(MouseButton button) {
    if (!audit.suppress("holdMouse(" + button + ") at " + describe(mouse), false)) {
      heldButtons.add(button);
    }
  }

  @Override
  public boolean isMouseHeld(MouseButton button) {
    return heldButtons.contains(button);
  }

  @Override
  public void releaseMouse(MouseButton button) {
    if (!audit.suppress("releaseMouse(" + button + ") at " + describe(mouse), false)) {
      heldButtons.remove(button);
    }
  }

  @Override
  public void moveMouse(Point location) {
    if (!audit.suppress("moveMouse to " + describe(location), true)) {
      mouse = new Point(location);
    }
  }

  @Override
  public void scrollMouse(int notches) {
    audit.suppress("scrollMouse(" + notches + ") at " + describe(mouse), false);
  }

  @Override
  public void sendString(String string, int keyWait, int keyModWait) {
    audit.suppress("sendString(" + string.length() + " chars)", false);
  }

  @Override
  public void close() {
    logger.info(
        "Headless input device closed: {} call(s) allowed, {} suppressed as UNGATED",
        audit.allowedCount(),
        audit.suppressedCount());
  }

  private static String describe(Point p) {
    return "(" + p.x + ", " + p.y + ")";
  }
}
