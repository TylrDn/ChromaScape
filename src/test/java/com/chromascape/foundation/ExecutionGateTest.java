package com.chromascape.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExecutionGateTest {

  @AfterEach
  void restoreDefault() {
    ExecutionGate.setDryRun(true);
  }

  @Test
  void dryRunIsTheDefaultWhenThePropertyIsUnset() {
    // Only meaningful when the runner did not set the property (an IDE launch config might).
    assumeTrue(System.getProperty("chromascape.dryRun") == null);
    assertTrue(ExecutionGate.isDryRun(), "an unconfigured JVM must be in dry-run");
  }

  @Test
  void dryRunSuppressesRunnableActions() {
    AtomicInteger ran = new AtomicInteger();
    ExecutionGate.setDryRun(true);
    ExecutionGate.execute("test action", ran::incrementAndGet);
    assertEquals(0, ran.get());
  }

  @Test
  void dryRunReturnsTheSyntheticResultForSupplierActions() {
    AtomicInteger ran = new AtomicInteger();
    ExecutionGate.setDryRun(true);
    boolean result =
        ExecutionGate.execute(
            "test action",
            true,
            () -> {
              ran.incrementAndGet();
              return false;
            });
    assertTrue(result, "dry-run must hand back the synthetic result");
    assertEquals(0, ran.get());
  }

  @Test
  void liveModeRunsActionsAndReturnsTheirResult() {
    AtomicInteger ran = new AtomicInteger();
    ExecutionGate.setDryRun(false);
    ExecutionGate.execute("test action", ran::incrementAndGet);
    boolean result = ExecutionGate.execute("test action", true, () -> false);
    assertEquals(1, ran.get());
    assertFalse(result);
  }
}
