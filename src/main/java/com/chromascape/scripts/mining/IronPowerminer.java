package com.chromascape.scripts.mining;

import com.chromascape.foundation.AbstractChromaScript;
import com.chromascape.foundation.ExecutionGate;
import com.chromascape.utils.actions.Diagnostics;
import com.chromascape.utils.actions.Idler;
import com.chromascape.utils.actions.InventorySlots;
import com.chromascape.utils.actions.ItemDropper;
import com.chromascape.utils.actions.MovingObject;
import com.chromascape.utils.core.screen.colour.ColourInstances;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.core.screen.topology.MatchResult;
import com.chromascape.utils.core.statistics.StatisticsManager;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Iron powerminer: mines the highlighted iron rock and drops the ore instead of banking, on repeat.
 *
 * <p><b>Requires a real template image at {@code src/main/resources/images/user/Iron_ore.png}</b> —
 * iron ore as it appears in an inventory slot, captured as-is. It does not stack, so there is no
 * quantity number over it and no crop needed — the 10px-crop convention used elsewhere in this
 * project (e.g. the Fishing demo's feather sprite) applies only to stacked or banked items with a
 * number superimposed on them. That file does not exist in this checkout ({@code images/user/}
 * currently holds only {@code .gitkeep}); {@link com.chromascape.foundation.CalibrationGate} will
 * refuse to start this script with a named "not found on classpath" error until you add it.
 * Grabbing that sprite, and confirming {@code "Cyan"} still highlights iron rocks for you in the
 * live client, is yours to do — picking colours and sprites stays with the human per the project's
 * own division of labour.
 *
 * <p>{@code "Cyan"} is already defined in {@code colours/colours.json}, matching the colour every
 * demo script uses for its click target, so no colour calibration work is needed here.
 *
 * <p>The ore click routes through {@link com.chromascape.utils.actions.MovingObject
 * #clickMovingObjectByColourObjUntilRedClick(com.chromascape.utils.core.screen.colour.ColourObj,
 * com.chromascape.base.BaseScript)} rather than a plain move-and-click, so a click that lands
 * without a confirmed red-X response is treated as a failure and halts the script instead of
 * silently continuing. Both that call and the {@code ItemDropper.dropAll} shift-drop are wrapped in
 * {@link com.chromascape.foundation.ExecutionGate#execute}, since neither one is dry-run-aware on
 * its own — {@code ItemDropper} sends real shift-clicks straight through the controller with no
 * suppression path of its own.
 */
public class IronPowerminer extends AbstractChromaScript {

  private static final Logger logger = LogManager.getLogger(IronPowerminer.class);

  private static final String ORE_COLOUR = "Cyan";
  private static final String IRON_ORE_IMAGE = "/images/user/Iron_ore.png";
  private static final int FULL_INVENTORY_SLOT = 27;

  // Pickaxe sits in inventory rather than being wielded - exclude it from the drop.
  private static final int PICKAXE_SLOT = 0;
  private static final int[] DROP_EXCLUDE_SLOTS = {PICKAXE_SLOT};

  // Project reference value (CONTEXT.md: "0.05 preferred, 0.15 maximum") - verify against the
  // live client once the sprite exists; tune tighter/looser to your own crop.
  private static final double IRON_ORE_THRESHOLD = 0.05;

  // How long to wait for the idle-notifier text after a click. 20s matches the one existing
  // precedent in DemoMiningScript, not a measured value - tune to the rock's real mining time.
  private static final int IDLE_TIMEOUT_SECONDS = 20;

  @Override
  protected List<String> requiredColours() {
    return List.of(ORE_COLOUR);
  }

  @Override
  protected List<String> requiredImages() {
    return List.of(IRON_ORE_IMAGE);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Confirms the zones this script depends on actually resolved before the first {@link
   * #cycle()} runs (delivery register M-3). {@link com.chromascape.foundation.CalibrationGate}, run
   * by {@link com.chromascape.foundation.AbstractChromaScript#onFirstCycle()} just before this
   * method, checks colour names and image existence only — it does not check that the inventory or
   * chat UI templates matched against this frame. On a frame where they didn't, {@link
   * com.chromascape.utils.domain.zones.ZoneManager#getInventorySlots()} and {@link
   * com.chromascape.utils.domain.zones.ZoneManager#getChatTabs()} return {@code null}, and both
   * {@link #isInventoryFull()} (this class, unguarded {@code .get(FULL_INVENTORY_SLOT)}) and {@link
   * Idler#waitUntilIdle} (unguarded {@code .get("Latest Message")}, called every cycle) would throw
   * an unhandled {@code NullPointerException} on the first cycle instead of a named halt.
   */
  @Override
  protected void setup() {
    verifyZonesResolved();
  }

  /**
   * Halts with a named reason if the inventory slots or the chat tabs this script (directly, or via
   * {@link Idler#waitUntilIdle}) depends on did not resolve. Logs the layout mode and, on success,
   * which zones resolved, either way.
   */
  private void verifyZonesResolved() {
    logger.info("Zone layout: {}", controller().zones().getIsFixed() ? "fixed" : "resizable");

    List<Rectangle> slots = controller().zones().getInventorySlots();
    if (slots == null || slots.size() <= FULL_INVENTORY_SLOT) {
      haltAndStop(
          "Inventory slots did not resolve (control panel template match failed on this frame) -"
              + " got "
              + (slots == null ? "null" : slots.size() + " slot(s)")
              + ", need at least "
              + (FULL_INVENTORY_SLOT + 1)
              + ".");
      return;
    }
    logger.info("Zones resolved: {} inventory slot(s)", slots.size());

    Map<String, Rectangle> chatTabs = controller().zones().getChatTabs();
    if (chatTabs == null || !chatTabs.containsKey("Latest Message")) {
      haltAndStop(
          "Chat zones did not resolve (chat template match failed on this frame) - "
              + (chatTabs == null ? "chatTabs is null" : "no 'Latest Message' key")
              + "; Idler.waitUntilIdle needs it every cycle.");
      return;
    }
    logger.info("Zones resolved: chat tabs = {}", chatTabs.keySet());
  }

  @Override
  protected void cycle() {
    if (isInventoryFull()) {
      ExecutionGate.execute(
          "Drop all inventory except pickaxe slot " + PICKAXE_SLOT,
          () -> ItemDropper.dropAll(this, ItemDropper.DropPattern.ZIGZAG, DROP_EXCLUDE_SLOTS));
    }

    clickOreRock();

    // TODO (yours, not mine): a humanised delay belongs here before the idle check - hand-write
    // your own waitRandomMillis(min, max) range. Left out deliberately; see Rule 5 in
    // copilot-instructions.md.

    if (!Idler.waitUntilIdle(this, IDLE_TIMEOUT_SECONDS)) {
      logger.warn(
          "No idle detected within {}s of clicking the rock - continuing to next cycle anyway.",
          IDLE_TIMEOUT_SECONDS);
    }
  }

  /**
   * Clicks the highlighted ore rock via {@link MovingObject}'s red-click verification, halting if
   * no rock was visible to click or if the click was never confirmed.
   */
  private void clickOreRock() {
    ColourObj colour = ColourInstances.getByName(ORE_COLOUR);

    boolean confirmed =
        ExecutionGate.execute(
            "Click ore rock (colour=" + ORE_COLOUR + "), verify red click",
            true,
            () -> {
              boolean result = MovingObject.clickMovingObjectByColourObjUntilRedClick(colour, this);
              StatisticsManager.incrementInputs();
              return result;
            });

    if (!confirmed) {
      haltAndStop("Ore rock click was never confirmed by a red click.");
    }
  }

  /** Checks the last inventory slot for iron ore, logging score/bounds either way. */
  private boolean isInventoryFull() {
    Rectangle slotZone = controller().zones().getInventorySlots().get(FULL_INVENTORY_SLOT);
    MatchResult result =
        InventorySlots.checkSlot(this, FULL_INVENTORY_SLOT, IRON_ORE_IMAGE, IRON_ORE_THRESHOLD);
    Diagnostics.recordDetection("iron-powerminer-inventory-full", slotZone, result);
    return result.success();
  }
}
