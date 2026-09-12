package com.chromascape.utils.actions;

import com.chromascape.base.BaseScript;
import java.time.LocalDateTime;
import java.util.function.BooleanSupplier;

/**
 * A bounded "wait until a condition becomes true" loop, with a deadline the caller can tell apart
 * from success.
 *
 * <p>Every demo script hand-rolls this same shape at least once: {@code DemoFishingScript}'s {@code
 * waitUntilStoppedMoving} and {@code waitUntilStoppedFishing}, and {@code DemoAgilityScript}'s
 * {@code waitUntilXpChange} and {@code waitForObstacleToAppear} all build a {@code LocalDateTime}
 * deadline, then loop polling a condition with {@code waitMillis} between checks. In every one of
 * those four methods, the loop returns {@code void} — whether it exited because the condition
 * became true or because time ran out is indistinguishable to the caller. This utility returns a
 * {@code boolean} instead, so a call site can actually branch on "did the thing happen, or did I
 * give up."
 */
public final class BoundedWait {

  private BoundedWait() {}

  /**
   * Polls {@code condition} until it returns {@code true} or {@code timeoutSeconds} elapses.
   *
   * <p>Checks {@code condition} before ever waiting, so an already-true condition returns
   * immediately. Otherwise sleeps {@code pollMillis} between checks via {@link
   * BaseScript#waitMillis(long)}, which throws {@code ScriptStoppedException} on interrupt, and
   * additionally calls {@link BaseScript#checkInterrupted()} on every iteration, since this is a
   * hand-written loop and the project convention requires it be interrupt-checked explicitly rather
   * than relying solely on the wait call.
   *
   * @param script the running script (kept for call-site consistency with the rest of {@code
   *     utils.actions}, e.g. {@code ItemDropper.dropAll(this, ...)})
   * @param condition the state check to poll; must not block for long, since it runs once per poll
   *     on the script thread
   * @param timeoutSeconds the maximum time to wait before giving up
   * @param pollMillis how long to sleep between checks
   * @return {@code true} if {@code condition} became true within the timeout, {@code false} if the
   *     deadline was reached first
   */
  public static boolean until(
      BaseScript script, BooleanSupplier condition, long timeoutSeconds, long pollMillis) {
    LocalDateTime deadline = LocalDateTime.now().plusSeconds(timeoutSeconds);

    while (true) {
      BaseScript.checkInterrupted();

      if (condition.getAsBoolean()) {
        return true;
      }

      if (!LocalDateTime.now().isBefore(deadline)) {
        return false;
      }

      BaseScript.waitMillis(pollMillis);
    }
  }
}
