package com.chromascape.utils.actions;

import com.chromascape.base.BaseScript;
import com.chromascape.utils.core.screen.topology.MatchResult;
import com.chromascape.utils.core.screen.topology.TemplateMatching;
import com.chromascape.utils.core.screen.window.ScreenManager;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Checks whether a template image is present in a single inventory slot.
 *
 * <p>Three demo scripts do the same capture-then-match on one slot: {@code
 * DemoWineScript.checkIfImageInvSlot1} (slot 0), {@code
 * DemoFishingScript.checkIfCorrectInventoryLayout} (slots 26 and 27), and {@code
 * DemoMiningScript.isInventoryFull} (slot 27). All three call {@code ScreenManager.captureZone} on
 * a {@code Rectangle} from {@code controller().zones().getInventorySlots()}, then {@code
 * TemplateMatching.match}, then discard everything but {@code MatchResult.success()}.
 *
 * <p>That last step is exactly what the project's calibration rules say not to do: log the score,
 * don't just read {@code success()}. This utility returns the full {@link MatchResult} rather than
 * a bare {@code boolean} so the caller still has {@code score} and {@code bounds} if it wants them,
 * and it logs score, bounds, slot index, and image path itself so that information exists even if
 * the caller never looks at the returned record.
 *
 * <p><b>Provenance note (2026-09-14):</b> the three {@code Demo*Script} classes cited above were
 * removed from {@code com.chromascape.scripts} on 2026-09-14 (single-script environment decision);
 * recoverable via {@code git log -- src/main/java/com/chromascape/scripts/Demo*.java} on commits at
 * or before {@code 1343a61}.
 */
public final class InventorySlots {

  private static final Logger logger = LogManager.getLogger(InventorySlots.class);

  private InventorySlots() {}

  /**
   * Captures a single inventory slot and template-matches it against {@code imagePath}.
   *
   * @param script the running script, used to reach {@code controller().zones()}
   * @param slotIndex inventory slot index, 0-27
   * @param imagePath absolute classpath resource path of the template image, e.g. {@code
   *     "/images/user/Iron_ore.png"}
   * @param threshold the OpenCV threshold to decide if a match exists; lower is stricter
   * @return the full {@link MatchResult}, including {@code score} and {@code bounds} — not just
   *     {@code success}
   * @throws IllegalArgumentException if {@code slotIndex} is outside 0-27, or if the zone manager
   *     has no rectangle for that slot
   */
  public static MatchResult checkSlot(
      BaseScript script, int slotIndex, String imagePath, double threshold) {
    if (slotIndex < 0 || slotIndex > 27) {
      throw new IllegalArgumentException("slotIndex must be 0-27, was " + slotIndex);
    }

    Rectangle slot = script.controller().zones().getInventorySlots().get(slotIndex);
    if (slot == null || slot.isEmpty()) {
      throw new IllegalArgumentException("Inventory slot " + slotIndex + " has no bounds.");
    }

    BufferedImage slotImage = ScreenManager.captureZone(slot);
    MatchResult result = TemplateMatching.match(imagePath, slotImage, threshold);

    logger.info(
        "Slot {} vs '{}': success={} score={} bounds={}",
        slotIndex,
        imagePath,
        result.success(),
        result.score(),
        result.bounds());

    return result;
  }
}
