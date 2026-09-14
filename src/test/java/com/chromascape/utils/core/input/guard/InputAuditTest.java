package com.chromascape.utils.core.input.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chromascape.foundation.ExecutionGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InputAuditTest {

  @AfterEach
  void restoreDryRun() {
    ExecutionGate.setDryRun(true);
  }

  @Test
  void countsFollowTheGate() {
    InputAudit audit = new InputAudit("test");
    ExecutionGate.setDryRun(true);
    assertTrue(audit.suppress("click", false));
    for (int i = 0; i < 250; i++) {
      assertTrue(audit.suppress("move", true), "every path point is suppressed, logged or not");
    }
    assertEquals(251, audit.suppressedCount());
    assertEquals(0, audit.allowedCount());

    ExecutionGate.setDryRun(false);
    assertFalse(audit.suppress("click", false));
    assertFalse(audit.suppress("move", true));
    assertEquals(2, audit.allowedCount());
    assertEquals(251, audit.suppressedCount());
  }
}
