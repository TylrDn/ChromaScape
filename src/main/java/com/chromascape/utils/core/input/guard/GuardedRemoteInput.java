package com.chromascape.utils.core.input.guard;

import com.chromascape.utils.core.input.remoteinput.MouseButton;
import com.chromascape.utils.core.input.remoteinput.RemoteInput;
import com.sun.jna.Pointer;
import java.awt.Point;
import java.awt.Rectangle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Wraps a real {@link RemoteInput} and refuses to send input while dry-run is active.
 *
 * <p>This is the backstop that makes a dry run trustworthy even when a script bypasses {@link
 * com.chromascape.foundation.ExecutionGate}: the mouse and keyboard utilities are handed this
 * wrapper instead of the paired device, so an ungated {@code controller().mouse().leftClick()} is
 * suppressed here and logged as {@code UNGATED} rather than reaching the client. Perception calls —
 * frame buffers, dimensions, mouse position, held-state queries — pass straight through, so a dry
 * run still sees exactly what a live run would.
 *
 * <p>With dry-run off, every call is delegated unchanged. The wrapper does not close the delegate;
 * the controller owns that.
 */
public final class GuardedRemoteInput extends RemoteInput {

  private static final Logger logger = LogManager.getLogger(GuardedRemoteInput.class);

  private final RemoteInput delegate;
  private final InputAudit audit = new InputAudit("device");

  /**
   * Wraps an already-paired device.
   *
   * @param delegate the real device; must not be {@code null}
   */
  public GuardedRemoteInput(RemoteInput delegate) {
    super();
    if (delegate == null) {
      throw new IllegalArgumentException("delegate must not be null");
    }
    this.delegate = delegate;
  }

  /**
   * The audit counting calls that reached this wrapper.
   *
   * @return the audit
   */
  public InputAudit audit() {
    return audit;
  }

  @Override
  public Pointer getImageBuffer() {
    return delegate.getImageBuffer();
  }

  @Override
  public Pointer getDebugImageBuffer() {
    return delegate.getDebugImageBuffer();
  }

  @Override
  public Point getMousePosition() {
    return delegate.getMousePosition();
  }

  @Override
  public Rectangle getTargetDimensions() {
    return delegate.getTargetDimensions();
  }

  @Override
  public void holdKey(int javaKeyCode) {
    if (!audit.suppress("holdKey(" + javaKeyCode + ")", false)) {
      delegate.holdKey(javaKeyCode);
    }
  }

  @Override
  public boolean isKeyHeld(int javaKeyCode) {
    return delegate.isKeyHeld(javaKeyCode);
  }

  @Override
  public void releaseKey(int javaKeyCode) {
    if (!audit.suppress("releaseKey(" + javaKeyCode + ")", false)) {
      delegate.releaseKey(javaKeyCode);
    }
  }

  @Override
  public void holdMouse(MouseButton button) {
    if (!audit.suppress("holdMouse(" + button + ")", false)) {
      delegate.holdMouse(button);
    }
  }

  @Override
  public boolean isMouseHeld(MouseButton button) {
    return delegate.isMouseHeld(button);
  }

  @Override
  public void releaseMouse(MouseButton button) {
    if (!audit.suppress("releaseMouse(" + button + ")", false)) {
      delegate.releaseMouse(button);
    }
  }

  @Override
  public void moveMouse(Point location) {
    if (!audit.suppress("moveMouse to (" + location.x + ", " + location.y + ")", true)) {
      delegate.moveMouse(location);
    }
  }

  @Override
  public void scrollMouse(int notches) {
    if (!audit.suppress("scrollMouse(" + notches + ")", false)) {
      delegate.scrollMouse(notches);
    }
  }

  @Override
  public void sendString(String string, int keyWait, int keyModWait) {
    if (!audit.suppress("sendString(" + string.length() + " chars)", false)) {
      delegate.sendString(string, keyWait, keyModWait);
    }
  }

  /** Logs the audit totals. The delegate is owned and closed by the controller. */
  @Override
  public void close() {
    logger.info(
        "Input guard closed: {} call(s) allowed, {} suppressed as UNGATED",
        audit.allowedCount(),
        audit.suppressedCount());
  }
}
