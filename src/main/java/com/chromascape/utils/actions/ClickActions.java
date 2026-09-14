package com.chromascape.utils.actions;

import com.chromascape.base.BaseScript;
import com.chromascape.foundation.ExecutionGate;
import com.chromascape.utils.core.statistics.StatisticsManager;
import java.awt.Point;

/**
 * Moves the mouse to a point and left-clicks it, the way every demo script does it at least once:
 * {@code DemoWineScript.clickBank/clickImage/clickInvSlot}, {@code
 * DemoFishingScript.clickFishingSpot}, {@code DemoMiningScript.clickOre}, and {@code
 * DemoAgilityScript.clickMarkOfGraceIfPresent} all call {@code controller().mouse().moveTo(...)}
 * immediately followed by {@code controller().mouse().leftClick()}, with the speed argument
 * sometimes a literal and sometimes a parameter, and logging that appears before, after, or not at
 * all depending on the call site.
 *
 * <p><b>Provenance note (2026-09-14):</b> the four {@code Demo*Script} classes cited above were
 * removed from {@code com.chromascape.scripts} on 2026-09-14 (single-script environment decision);
 * their source is recoverable via {@code git log --
 * src/main/java/com/chromascape/scripts/Demo*.java} on commits at or before {@code 1343a61}. The
 * citation stands as the Rule-6 evidence this class was built against.
 *
 * <p>This class fixes three things those call sites didn't do consistently: it logs exactly once,
 * before acting, every time; it routes the actual click through {@link
 * ExecutionGate#execute(String, Runnable)} so dry-run mode suppresses it (per the project
 * checklist's "every action routes through a single {@code execute(intent)}" requirement); and it
 * counts the click via {@link StatisticsManager#incrementInputs()} so the live UI sees it.
 *
 * <p>It deliberately does not decide what to do about a {@code null} point — a caller with no point
 * to click is a caller error, not a runtime condition to recover from at this layer. Scripts that
 * get their point from {@link PointSelector} should null-check it themselves (or go through the
 * base class's guarded click, which halts the script cleanly on a null point instead of throwing).
 */
public final class ClickActions {

  private ClickActions() {}

  /**
   * Moves the mouse to {@code point} and left-clicks it.
   *
   * @param script the running script, used to reach {@code controller().mouse()}
   * @param point the on-screen point to click; must not be {@code null}
   * @param speed the mouse movement speed: {@code "slow"}, {@code "medium"}, or {@code "fast"}
   * @throws IllegalArgumentException if {@code point} is {@code null}
   */
  public static void clickPoint(BaseScript script, Point point, String speed) {
    if (point == null) {
      throw new IllegalArgumentException("point must not be null");
    }

    String intent = String.format("Click at (%d, %d), speed=%s", point.x, point.y, speed);

    ExecutionGate.execute(
        intent,
        () -> {
          script.controller().mouse().moveTo(point, speed);
          script.controller().mouse().leftClick();
          StatisticsManager.incrementInputs();
        });
  }
}
