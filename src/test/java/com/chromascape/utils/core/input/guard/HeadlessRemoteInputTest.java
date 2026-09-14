package com.chromascape.utils.core.input.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chromascape.foundation.ExecutionGate;
import com.chromascape.utils.core.input.remoteinput.MouseButton;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HeadlessRemoteInputTest {

  @AfterEach
  void restoreDryRun() {
    ExecutionGate.setDryRun(true);
  }

  @Test
  void startsAtOriginWithTheGivenBounds() {
    HeadlessRemoteInput device = new HeadlessRemoteInput(new Rectangle(0, 0, 765, 503));
    assertEquals(new Rectangle(0, 0, 765, 503), device.getTargetDimensions());
    assertEquals(new Point(0, 0), device.getMousePosition());
  }

  @Test
  void rejectsEmptyBounds() {
    assertThrows(
        IllegalArgumentException.class, () -> new HeadlessRemoteInput(new Rectangle(0, 0, 0, 10)));
  }

  @Test
  void dryRunSuppressesEveryInputAndCountsItAsUngated() {
    ExecutionGate.setDryRun(true);
    HeadlessRemoteInput device = new HeadlessRemoteInput(new Rectangle(0, 0, 100, 100));

    device.moveMouse(new Point(10, 20));
    device.holdMouse(MouseButton.left);
    device.holdKey(KeyEvent.VK_SHIFT);
    device.sendString("abc", 1, 1);
    device.scrollMouse(1);

    assertEquals(new Point(0, 0), device.getMousePosition(), "mouse must not have moved");
    assertFalse(device.isMouseHeld(MouseButton.left));
    assertFalse(device.isKeyHeld(KeyEvent.VK_SHIFT));
    assertEquals(5, device.audit().suppressedCount());
    assertEquals(0, device.audit().allowedCount());
  }

  @Test
  void liveModeTracksVirtualState() {
    ExecutionGate.setDryRun(false);
    HeadlessRemoteInput device = new HeadlessRemoteInput(new Rectangle(0, 0, 100, 100));

    device.moveMouse(new Point(10, 20));
    device.holdMouse(MouseButton.left);
    device.holdKey(KeyEvent.VK_SHIFT);
    assertEquals(new Point(10, 20), device.getMousePosition());
    assertTrue(device.isMouseHeld(MouseButton.left));
    assertTrue(device.isKeyHeld(KeyEvent.VK_SHIFT));

    device.releaseMouse(MouseButton.left);
    device.releaseKey(KeyEvent.VK_SHIFT);
    assertFalse(device.isMouseHeld(MouseButton.left));
    assertFalse(device.isKeyHeld(KeyEvent.VK_SHIFT));

    assertEquals(5, device.audit().allowedCount());
    assertEquals(0, device.audit().suppressedCount());
  }

  @Test
  void hasNoFrameBuffer() {
    HeadlessRemoteInput device = new HeadlessRemoteInput(new Rectangle(0, 0, 10, 10));
    assertThrows(UnsupportedOperationException.class, device::getImageBuffer);
    assertThrows(UnsupportedOperationException.class, device::getDebugImageBuffer);
  }
}
