package com.chromascape.utils.core.screen.capture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

/** The bridge itself is macOS-only; only the coordinate-space rule is testable here. */
class ScreenCaptureKitCaptureSourceTest {

  private static final Rectangle RETINA_WINDOW = new Rectangle(0, 0, 1512, 982);
  private static final Rectangle LOGICAL_CANVAS = new Rectangle(0, 0, 765, 503);

  @Test
  void matchingSpacesAreAllowedInEitherMode() {
    assertDoesNotThrow(
        () ->
            ScreenCaptureKitCaptureSource.requireInputSpaceMatch(
                LOGICAL_CANVAS, LOGICAL_CANVAS, true));
    assertDoesNotThrow(
        () ->
            ScreenCaptureKitCaptureSource.requireInputSpaceMatch(
                LOGICAL_CANVAS, LOGICAL_CANVAS, false));
  }

  @Test
  void mismatchIsOnlyWarningInDryRun() {
    assertDoesNotThrow(
        () ->
            ScreenCaptureKitCaptureSource.requireInputSpaceMatch(
                RETINA_WINDOW, LOGICAL_CANVAS, true));
  }

  @Test
  void mismatchRefusesLiveRunNamingBothSizes() {
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () ->
                ScreenCaptureKitCaptureSource.requireInputSpaceMatch(
                    RETINA_WINDOW, LOGICAL_CANVAS, false));
    assertTrue(e.getMessage().startsWith("LIVE RUN REFUSED"));
    assertTrue(e.getMessage().contains("1512x982"));
    assertTrue(e.getMessage().contains("765x503"));
  }
}
