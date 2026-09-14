package com.chromascape.utils.core.input.guard;

import com.chromascape.foundation.ExecutionGate;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The device-level backstop under {@link ExecutionGate}.
 *
 * <p>{@code ExecutionGate} is the intended single enforcement point for dry-run: scripts route
 * actions through it, and in dry-run those actions are never invoked. It can only guard calls that
 * go through it. This class sits at the other end of the stack — inside the input device — and
 * answers one question for every input call that arrives: <em>should this reach the client?</em>
 *
 * <ul>
 *   <li>Dry-run active: the call is <b>suppressed</b> and logged at {@code WARN} as {@code
 *       UNGATED}, because a gated call would never have got this far. Every such line in a dry-run
 *       log is a script bypassing the gate — the exact defect the project's Rule 4a names as worse
 *       than no gate at all.
 *   <li>Dry-run off: the call is <b>allowed</b> and logged at {@code INFO} (clicks, keys, scroll,
 *       text) or {@code DEBUG} (individual mouse-path moves, which arrive dozens per click).
 * </ul>
 *
 * <p>Shared by {@link GuardedRemoteInput} (real device, macOS) and {@link HeadlessRemoteInput} (no
 * device, replay profile) — the two occurrences that justify it existing.
 */
public final class InputAudit {

  private static final Logger logger = LogManager.getLogger(InputAudit.class);

  /** How many suppressed path-move points share one WARN line; see {@link #suppress}. */
  private static final int PATH_MOVE_WARN_EVERY = 100;

  private final String deviceName;
  private final AtomicInteger suppressed = new AtomicInteger();
  private final AtomicInteger allowed = new AtomicInteger();

  /**
   * Creates an audit for one device.
   *
   * @param deviceName a short tag for log lines, e.g. {@code "headless"}
   */
  public InputAudit(String deviceName) {
    this.deviceName = deviceName;
  }

  /**
   * Records an input call and decides whether it may proceed.
   *
   * @param call a description of the call, e.g. {@code "holdMouse(left) at (412, 233)"}
   * @param isPathMove {@code true} for per-point mouse moves, which log at {@code DEBUG} when
   *     allowed so a single humanised move does not flood the log
   * @return {@code true} if the call must be suppressed (dry-run is active), {@code false} if it
   *     may reach the device
   */
  public boolean suppress(String call, boolean isPathMove) {
    if (ExecutionGate.isDryRun()) {
      int n = suppressed.incrementAndGet();
      // One humanised mouse move is dozens of path points. The first suppressed move of a burst
      // and every PATH_MOVE_WARN_EVERY-th after it are WARN; the rest are DEBUG but still counted.
      if (isPathMove && (n % PATH_MOVE_WARN_EVERY) != 1) {
        logger.debug("[{}] UNGATED (suppressed, #{}): {}", deviceName, n, call);
        return true;
      }
      logger.warn(
          "[{}] UNGATED input suppressed by dry-run (#{}): {} — this call bypassed ExecutionGate",
          deviceName,
          n,
          call);
      return true;
    }
    allowed.incrementAndGet();
    if (isPathMove) {
      logger.debug("[{}] {}", deviceName, call);
    } else {
      logger.info("[{}] {}", deviceName, call);
    }
    return false;
  }

  /**
   * Number of calls suppressed because dry-run was active.
   *
   * @return the suppressed count since construction
   */
  public int suppressedCount() {
    return suppressed.get();
  }

  /**
   * Number of calls allowed through to the device.
   *
   * @return the allowed count since construction
   */
  public int allowedCount() {
    return allowed.get();
  }
}
