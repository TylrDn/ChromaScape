package com.chromascape.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Controller#retryWithBackoff} — the bounded retry wrapped around pairing
 * RemoteInput with a just-found pid (Controller.pairRemoteInput()) — without a native library,
 * using a fake {@link java.util.function.Supplier} in place of {@code new RemoteInput(pid)}.
 *
 * <p>Backoff durations here are 1ms so the suite stays fast; the behaviour under test is the retry
 * count and control flow, not the actual wait time.
 */
class ControllerInjectionRetryTest {

  @Test
  void succeedsOnFirstAttemptWithoutRetrying() {
    AtomicInteger calls = new AtomicInteger();
    String result =
        Controller.retryWithBackoff(
            "test", 3, Duration.ofMillis(1), () -> "ok-" + calls.incrementAndGet());
    assertEquals("ok-1", result);
    assertEquals(1, calls.get());
  }

  @Test
  void retriesAfterTransientFailuresThenSucceeds() {
    AtomicInteger calls = new AtomicInteger();
    String result =
        Controller.retryWithBackoff(
            "test",
            3,
            Duration.ofMillis(1),
            () -> {
              int attempt = calls.incrementAndGet();
              if (attempt < 3) {
                throw new RuntimeException("Target Not Found with pid: 71077");
              }
              return "ok-" + attempt;
            });
    assertEquals("ok-3", result);
    assertEquals(3, calls.get());
  }

  @Test
  void givesUpAfterMaxAttemptsAndRethrowsTheLastFailure() {
    AtomicInteger calls = new AtomicInteger();
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                Controller.retryWithBackoff(
                    "test",
                    3,
                    Duration.ofMillis(1),
                    () -> {
                      throw new RuntimeException(
                          "Target Not Found with pid: " + calls.incrementAndGet());
                    }));
    // Exactly maxAttempts calls: no attempt after the last one is thrown away, no attempt beyond
    // it.
    assertEquals(3, calls.get());
    assertEquals("Target Not Found with pid: 3", thrown.getMessage());
  }

  @Test
  void singleAttemptBudgetNeverRetries() {
    AtomicInteger calls = new AtomicInteger();
    assertThrows(
        RuntimeException.class,
        () ->
            Controller.retryWithBackoff(
                "test",
                1,
                Duration.ofMillis(1),
                () -> {
                  calls.incrementAndGet();
                  throw new RuntimeException("nope");
                }));
    assertEquals(1, calls.get());
  }
}
