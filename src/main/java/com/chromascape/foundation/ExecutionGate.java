package com.chromascape.foundation;

import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The single enforcement point for dry-run mode.
 *
 * <p>Per the project's own checklist (chromascape-checklist.html, "1.8 Observability and
 * operations"): "Every action routes through a single {@code execute(intent)} method that logs the
 * intent and returns early when the flag is set. The enforcement point matters more than the flag:
 * if half your controller calls bypass it, dry-run stops being a safety tool and becomes false
 * confidence."
 *
 * <p>This class is that one point. It does not decide *what* counts as an action — callers do, by
 * routing their {@code controller().mouse()} / {@code controller().keyboard()} calls through {@link
 * #execute(String, Runnable)} instead of invoking them directly. Perception (reading pixels, OCR,
 * template matching) never goes through this gate: a dry run should see exactly what a live run
 * would see, and only suppress the part that touches the client.
 *
 * <p><b>Dry-run is on by default.</b> Live input requires an explicit {@code
 * -Dchromascape.dryRun=false} on the <em>application</em> JVM. A {@code -D} passed to {@code
 * ./gradlew bootRun} reaches the Gradle daemon, not the forked application, unless {@code
 * build.gradle.kts} forwards it — the {@code bootRun} block there does exactly that. The VS Code
 * launch configurations set the property directly on the JVM they start. {@link
 * #setDryRun(boolean)} can also flip it from {@code setup()}.
 */
public final class ExecutionGate {

  private static final Logger logger = LogManager.getLogger(ExecutionGate.class);
  private static final String DRY_RUN_PROPERTY = "chromascape.dryRun";

  /**
   * Dry-run is the DEFAULT. Live input requires an explicit {@code -Dchromascape.dryRun=false} on
   * the application JVM. Any other value, or no value at all, keeps the gate closed.
   */
  private static volatile boolean dryRun =
      !"false".equalsIgnoreCase(System.getProperty(DRY_RUN_PROPERTY, "true"));

  private ExecutionGate() {}

  /**
   * Enables or disables dry-run mode from code, overriding whatever the {@code chromascape.dryRun}
   * system property set at startup.
   *
   * @param enabled {@code true} to suppress actions from this point on, {@code false} to resume
   *     performing them
   */
  public static void setDryRun(boolean enabled) {
    dryRun = enabled;
    logger.info("Dry-run mode {}.", enabled ? "ENABLED" : "disabled");
  }

  /**
   * Reports whether dry-run mode is currently active.
   *
   * @return {@code true} if actions routed through {@link #execute(String, Runnable)} are currently
   *     being suppressed
   */
  public static boolean isDryRun() {
    return dryRun;
  }

  /**
   * Runs a single action through the dry-run gate.
   *
   * <p>Always logs {@code intent} first, so the log answers what the script was about to do
   * regardless of mode. If dry-run is active, {@code action} is never invoked. Otherwise it runs
   * normally.
   *
   * @param intent a human-readable description of what the action is about to do (e.g. {@code
   *     "click ore rock at (412, 233)"}); written to the log every time, live or dry
   * @param action the real action to perform when not in dry-run mode (e.g. a mouse move + click)
   */
  public static void execute(String intent, Runnable action) {
    if (dryRun) {
      logger.info("[DRY RUN] {}", intent);
      return;
    }
    logger.info(intent);
    action.run();
  }

  /**
   * Runs a single action through the dry-run gate, for actions that report a result (e.g. {@code
   * MovingObject}'s red-click verification, which returns whether the click was confirmed).
   *
   * <p>If dry-run is active, {@code action} is never invoked and {@code dryRunResult} is returned
   * in its place, so downstream logic (branching on "did it work") still runs the same code path
   * during a dry run instead of being skipped entirely.
   *
   * @param <T> the result type
   * @param intent a human-readable description of what the action is about to do; written to the
   *     log every time, live or dry
   * @param dryRunResult the value to return without performing the action, when dry-run is active
   * @param action the real action to perform when not in dry-run mode
   * @return {@code action}'s result, or {@code dryRunResult} if suppressed
   */
  public static <T> T execute(String intent, T dryRunResult, Supplier<T> action) {
    if (dryRun) {
      logger.info("[DRY RUN] {}", intent);
      return dryRunResult;
    }
    logger.info(intent);
    return action.get();
  }
}
