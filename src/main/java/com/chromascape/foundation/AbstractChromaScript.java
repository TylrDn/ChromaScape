package com.chromascape.foundation;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.actions.ClickActions;
import com.chromascape.utils.core.runtime.exception.ScriptStoppedException;
import com.chromascape.utils.core.screen.window.ScreenManager;
import java.awt.Point;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Intermediate base class between {@code BaseScript} and every script this project writes.
 *
 * <p>Read for this class: {@code BaseScript.java} (confirmed {@code onFirstCycle()} is {@code
 * protected}, non-final, and concrete with an empty body — not abstract, so a script that forgets
 * to override {@code cycle()} previously compiled, ran, and looped doing nothing forever), and
 * {@code ScriptStoppedException.java} (confirmed it extends {@code RuntimeException} — unchecked,
 * so it propagates through {@link #haltAndStop(String)} with no {@code throws} clause, and
 * confirmed it has exactly one constructor, no-arg, with a fixed message — it cannot carry a
 * caller-supplied reason, which is why {@link #haltAndStop(String)} logs the reason separately
 * before throwing it).
 *
 * <h2>What is deliberately NOT here</h2>
 *
 * <p>{@code cycle()} is re-declared {@code abstract} below (a one-line, zero-risk change per the
 * confirmed fact above), but it is not final, and there is no forced {@code observe/decide/act/
 * verify} template driving it. The four demo scripts have four genuinely different cycle shapes — a
 * fixed ten-step linear sequence, a precondition-gate-then-reactive-checks script, a
 * priority-branching script with its own early returns, and one that's actually close to a clean
 * loop. Forcing all of them through one template would flatten real control-flow differences into
 * hooks that correspond to nothing in at least two of the four. That's the speculative abstraction
 * this session's rules forbid, so {@code cycle()} stays open.
 *
 * <h2>What is here, and why</h2>
 *
 * <ul>
 *   <li>{@link #onFirstCycle()} is {@code final}. It runs the calibration gate, logs whether
 *       dry-run mode is on, then calls {@link #setup()}. A subclass cannot skip calibration by
 *       forgetting a {@code super} call, because it can no longer override this method at all.
 *   <li>{@link #haltAndStop(String)} is the one sanctioned way a script ends itself on
 *       unrecoverable state. Every demo's copy-pasted {@code logger.error(...); stop();} falls
 *       through and keeps executing with bad data in every occurrence but one — {@code stop()} only
 *       requests a stop, it does not throw. This method logs, requests the stop, and then throws
 *       immediately, so there is no "next line" left to fall through to.
 *   <li>{@link #clickPointOrHalt(Point, String)} pairs a null check with {@link
 *       #haltAndStop(String)} so a null {@code PointSelector} result can't reach {@code
 *       ClickActions} and can't be silently clicked on.
 * </ul>
 */
public abstract class AbstractChromaScript extends BaseScript {

  private final Logger logger = LogManager.getLogger(this.getClass());

  /** No-argument constructor, required so every subclass keeps its own no-argument constructor. */
  protected AbstractChromaScript() {}

  /**
   * Colour names this script depends on, checked by the calibration gate before {@link #setup()}
   * runs. Default is empty; override to declare any names looked up via {@code
   * ColourInstances.getByName(String)} elsewhere in the script.
   *
   * <p>Abstract rather than a default empty list on purpose: a script that depends on colours but
   * never overrides this would otherwise pass calibration trivially, with no signal that nothing
   * was actually checked. Returning {@code List.of()} is fine — it just has to be a deliberate,
   * visible choice, not an inherited no-op.
   *
   * @return colour names to verify at calibration time
   */
  protected abstract List<String> requiredColours();

  /**
   * Template image classpath paths this script depends on, checked by the calibration gate before
   * {@link #setup()} runs. Override to declare any paths passed to {@code TemplateMatching.match}
   * elsewhere in the script.
   *
   * <p>Abstract for the same reason as {@link #requiredColours()}: silently checking nothing must
   * not look identical to deliberately having nothing to check.
   *
   * @return absolute classpath resource paths to verify at calibration time
   */
  protected abstract List<String> requiredImages();

  /**
   * Runs once, after calibration passes and before the first {@link #cycle()}. Default is a no-op;
   * override for one-time setup (custom zones, initial state) instead of guarding it behind a flag
   * inside {@link #cycle()}.
   */
  protected void setup() {
    // override this
  }

  /**
   * {@inheritDoc}
   *
   * <p>Final. Runs the calibration gate against {@link #requiredColours()} and {@link
   * #requiredImages()}, logs whether dry-run mode is active and whether Discord alerts are
   * configured, then calls {@link #setup()}. A subclass extending {@link AbstractChromaScript}
   * cannot override this method to skip any of that — it can only override {@link #setup()}.
   */
  @Override
  protected final void onFirstCycle() {
    logger.info("Dry-run mode: {}", ExecutionGate.isDryRun() ? "ENABLED" : "disabled");
    logger.info("Capture source: {}", ScreenManager.describeCaptureSource());
    logger.info(
        "Discord alerts: {}",
        Files.exists(Path.of("secrets.properties"))
            ? "configured"
            : "NOT CONFIGURED — DiscordNotification.send() will silently no-op");
    CalibrationGate.check(requiredColours(), requiredImages());
    setup();
  }

  /**
   * The core logic of the script, called once per loop iteration by {@code BaseScript.run()}.
   *
   * <p>Re-declared {@code abstract} here (it is concrete with an empty body on {@code BaseScript})
   * so that a script which forgets to override it fails to compile instead of silently looping
   * doing nothing.
   */
  @Override
  protected abstract void cycle();

  /**
   * Logs {@code reason} as an error, requests the script stop, then immediately throws {@link
   * ScriptStoppedException} so execution cannot fall through and use whatever bad state triggered
   * the halt. {@code stop()} alone only sets a flag and interrupts the script thread — it does not
   * throw — which is exactly the gap that let every demo but one fall through a null-point check
   * straight into a click on that null point.
   *
   * @param reason a specific, logged explanation of what made the script unable to continue
   * @throws ScriptStoppedException always
   */
  protected final void haltAndStop(String reason) {
    logger.error(reason);
    stop();
    throw new ScriptStoppedException();
  }

  /**
   * Clicks {@code point} at the given speed, or halts the script if {@code point} is {@code null}.
   *
   * <p>Intended for {@code PointSelector} results, which return {@code null} on no match. Collapses
   * the null-check-then-{@code stop()} shape every demo repeats (Table 1) and the fall-through bug
   * three of four occurrences of it share (Table 2) into one call that cannot reproduce either.
   *
   * @param point the point to click, or {@code null} if nothing was found to click
   * @param speed mouse movement speed: {@code "slow"}, {@code "medium"}, or {@code "fast"}
   */
  protected final void clickPointOrHalt(Point point, String speed) {
    if (point == null) {
      haltAndStop("clickPointOrHalt: point was null, nothing to click");
      return;
    }
    ClickActions.clickPoint(this, point, speed);
  }
}
