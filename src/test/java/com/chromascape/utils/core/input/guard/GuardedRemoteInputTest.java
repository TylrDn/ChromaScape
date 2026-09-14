package com.chromascape.utils.core.input.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chromascape.foundation.ExecutionGate;
import com.chromascape.utils.core.input.remoteinput.MouseButton;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Uses a live-mode {@link HeadlessRemoteInput} as the delegate, so "reached the device" is
 * observable as virtual state without any native library.
 */
class GuardedRemoteInputTest {

  @AfterEach
  void restoreDryRun() {
    ExecutionGate.setDryRun(true);
  }

  @Test
  void rejectsNullDelegate() {
    assertThrows(IllegalArgumentException.class, () -> new GuardedRemoteInput(null));
  }

  @Test
  void dryRunNeverReachesTheDelegateButPerceptionPassesThrough() {
    ExecutionGate.setDryRun(true);
    HeadlessRemoteInput real = new HeadlessRemoteInput(new Rectangle(0, 0, 200, 100));
    GuardedRemoteInput guard = new GuardedRemoteInput(real);

    guard.moveMouse(new Point(50, 50));
    guard.holdMouse(MouseButton.left);
    guard.releaseMouse(MouseButton.left);
    guard.holdKey(KeyEvent.VK_SPACE);
    guard.releaseKey(KeyEvent.VK_SPACE);
    guard.scrollMouse(-1);
    guard.sendString("x", 1, 1);

    assertEquals(7, guard.audit().suppressedCount());
    assertEquals(0, real.audit().allowedCount(), "nothing may reach the real device in dry-run");
    assertEquals(new Point(0, 0), guard.getMousePosition(), "perception is delegated");
    assertEquals(new Rectangle(0, 0, 200, 100), guard.getTargetDimensions());
  }

  @Test
  void liveModeDelegatesEverything() {
    ExecutionGate.setDryRun(false);
    HeadlessRemoteInput real = new HeadlessRemoteInput(new Rectangle(0, 0, 200, 100));
    GuardedRemoteInput guard = new GuardedRemoteInput(real);

    guard.moveMouse(new Point(50, 50));
    guard.holdMouse(MouseButton.right);
    guard.holdKey(KeyEvent.VK_SPACE);

    assertEquals(new Point(50, 50), real.getMousePosition());
    assertTrue(real.isMouseHeld(MouseButton.right));
    assertTrue(guard.isMouseHeld(MouseButton.right), "held-state queries are delegated");
    assertTrue(real.isKeyHeld(KeyEvent.VK_SPACE));
    assertEquals(3, guard.audit().allowedCount());
    assertEquals(0, guard.audit().suppressedCount());
  }
}
