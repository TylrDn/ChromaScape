package com.chromascape.foundation.template;

import com.chromascape.foundation.AbstractChromaScript;
import com.chromascape.utils.actions.BoundedWait;
import com.chromascape.utils.actions.PointSelector;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Copy this file into {@code com.chromascape.scripts.<yourFamily>} and rename the class before
 * filling it in. Not itself a runnable script — it lives outside {@code com.chromascape.scripts} on
 * purpose, so it never appears in the web UI sidebar as something clickable.
 *
 * <p>Every {@code TODO} below is deliberately left in a state that fails loudly instead of quietly
 * doing nothing if you forget to fill it in:
 *
 * <ul>
 *   <li>{@link #TARGET_COLOUR} and {@link #TARGET_IMAGE} are invalid placeholder values. Left
 *       unfilled, {@code CalibrationGate} — run automatically before your first cycle — throws
 *       {@code IllegalStateException} naming exactly which one didn't resolve.
 *   <li>{@link #RESET_TILE} is {@code null} by default. {@link #setup()} checks for that explicitly
 *       and halts with a named message. There's no automatic check for a plain {@code Point}
 *       constant the way there is for colours/images, so this script checks it by hand — copy that
 *       pattern for any other required constant you add.
 *   <li>The verification call in {@link #cycle()} is wired to a placeholder condition that is
 *       always {@code false}. Left unfilled, every run times out and halts on the first cycle
 *       instead of silently reporting success.
 * </ul>
 *
 * <p><b>What this cannot catch:</b> deleting the verification call entirely instead of filling it
 * in, or replacing a placeholder with a condition that's technically non-default but still wrong.
 * Structural enforcement stops at "you can't forget this exists" — it can't judge whether what you
 * wrote is correct. That part is still yours; see the fill-in checklist alongside this file.
 */
public class ScriptTemplate extends AbstractChromaScript {

  // --- TODO: fill in before running --------------------------------------------------------

  /** TODO: replace with a real colour name from colours.json, e.g. "Cyan". */
  private static final String TARGET_COLOUR = "TODO_SET_COLOUR_NAME";

  /** TODO: replace with a real classpath image path, e.g. "/images/user/YourItem.png". */
  private static final String TARGET_IMAGE = "/images/TODO_SET_IMAGE_PATH.png";

  /** TODO: replace with a real world tile, or delete the {@link #setup()} check if unused. */
  private static final Point RESET_TILE = null;

  // --- Tunables -----------------------------------------------------------------------------

  private static final int MAX_CLICK_ATTEMPTS = 15;
  private static final int VERIFY_TIMEOUT_SECONDS = 5;
  private static final long VERIFY_POLL_MILLIS = 300;

  @Override
  protected List<String> requiredColours() {
    return List.of(TARGET_COLOUR);
  }

  @Override
  protected List<String> requiredImages() {
    return List.of(TARGET_IMAGE);
  }

  @Override
  protected void setup() {
    if (RESET_TILE == null) {
      haltAndStop("RESET_TILE is not set - fill in the TODO in ScriptTemplate before running.");
    }
  }

  @Override
  protected void cycle() {
    // Perception: find something to act on.
    BufferedImage gameView = controller().zones().getGameView();
    Point target =
        PointSelector.getRandomPointInColour(gameView, TARGET_COLOUR, MAX_CLICK_ATTEMPTS);

    // Action: click it, or halt cleanly if nothing was found. Null-checked for you.
    clickPointOrHalt(target, "medium");

    // Verification: TODO replace this condition with a real check that the click worked (an
    // inventory slot changing, an OCR value changing, a colour disappearing, etc).
    boolean verified =
        BoundedWait.until(this, () -> false, VERIFY_TIMEOUT_SECONDS, VERIFY_POLL_MILLIS);
    if (!verified) {
      haltAndStop(
          "Verification timed out - the click's effect was never confirmed. Replace the "
              + "placeholder condition in cycle() with a real check.");
    }
  }
}
