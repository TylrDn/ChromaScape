package com.chromascape.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuntimeProfileTest {

  @Test
  void unsetDefaultsToRemoteInput() {
    assertEquals(RuntimeProfile.REMOTEINPUT, RuntimeProfile.parse(null, null));
    assertEquals(RuntimeProfile.REMOTEINPUT, RuntimeProfile.parse("  ", null));
  }

  @Test
  void legacyCaptureBridgeFlagStillSelectsCaptureKit() {
    assertEquals(RuntimeProfile.CAPTUREKIT, RuntimeProfile.parse(null, "true"));
    assertEquals(RuntimeProfile.REMOTEINPUT, RuntimeProfile.parse(null, "false"));
  }

  @Test
  void explicitProfileWinsOverLegacyFlag() {
    assertEquals(RuntimeProfile.REPLAY, RuntimeProfile.parse("replay", "true"));
    assertEquals(RuntimeProfile.REMOTEINPUT, RuntimeProfile.parse("remoteinput", "true"));
  }

  @Test
  void namesAreCaseAndSeparatorInsensitive() {
    assertEquals(RuntimeProfile.CAPTUREKIT, RuntimeProfile.parse("CaptureKit", null));
    assertEquals(RuntimeProfile.CAPTUREKIT, RuntimeProfile.parse("capture-kit", null));
    assertEquals(RuntimeProfile.REMOTEINPUT, RuntimeProfile.parse("remote_input", null));
  }

  @Test
  void unknownValueNamesTheValidOnes() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> RuntimeProfile.parse("bogus", null));
    assertTrue(e.getMessage().contains("bogus"));
    assertTrue(e.getMessage().contains("replay"));
    assertTrue(e.getMessage().contains("capturekit"));
  }

  @Test
  void onlyReplayRunsWithoutClient() {
    assertFalse(RuntimeProfile.REPLAY.requiresClient());
    assertTrue(RuntimeProfile.REMOTEINPUT.requiresClient());
    assertTrue(RuntimeProfile.CAPTUREKIT.requiresClient());
  }
}
