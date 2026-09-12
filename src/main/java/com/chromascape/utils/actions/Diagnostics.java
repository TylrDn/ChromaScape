package com.chromascape.utils.actions;

import com.chromascape.utils.core.screen.topology.MatchResult;
import com.chromascape.utils.core.screen.window.ScreenManager;
import com.chromascape.utils.core.statistics.StatisticsManager;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Structured detector-result logging plus a cropped failure screenshot, written to {@code
 * output/diagnostics/}.
 *
 * <p><b>Flagged as requested, not evidenced.</b> None of the four demos log a detector's score,
 * bounds, or zone on failure, or save a screenshot when one fails — {@code
 * DemoWineScript.checkIfImageInvSlot1}, {@code DemoFishingScript.checkIfCorrectInventoryLayout},
 * and {@code DemoMiningScript.isInventoryFull} all discard everything but {@code
 * MatchResult.success()} (see {@link InventorySlots}, built to stop that discarding at the utility
 * layer). There's no cross-demo repetition to cite for this class the way there is for the others —
 * it exists because the project's own checklist and the Phase 2 build order ask for it directly,
 * the same way the calibration and dry-run gates do.
 *
 * <p>{@link StatisticsManager#incrementObjectsDetected()} is called here too, on every *successful*
 * detection — also requested, not evidenced; no demo touches {@code StatisticsManager} directly.
 * Only successes are counted, matching the checklist's own framing of the counter ("detection count
 * falling off is an early warning that client state drifted") — counting failures too would keep
 * the number constant regardless of drift.
 */
public final class Diagnostics {

  private static final Logger logger = LogManager.getLogger(Diagnostics.class);
  private static final String OUTPUT_DIR = "output/diagnostics";
  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

  private Diagnostics() {}

  /**
   * Logs a detector result (success, score, bounds, zone, message) and, on failure, saves a cropped
   * screenshot of the zone that was searched.
   *
   * @param label a short, filesystem-safe name identifying the detector/check, used in the log line
   *     and, on failure, the saved file name, e.g. {@code "iron-ore-slot27"}
   * @param zone the screen region that was searched; only captured to disk if {@code result} failed
   * @param result the {@link MatchResult} returned by {@code TemplateMatching.match}
   */
  public static void recordDetection(String label, Rectangle zone, MatchResult result) {
    if (result.success()) {
      StatisticsManager.incrementObjectsDetected();
    }

    logger.info(
        "Detector '{}': success={} score={} bounds={} zone={} message={}",
        label,
        result.success(),
        result.score(),
        result.bounds(),
        zone,
        result.message());

    if (!result.success()) {
      saveFailureScreenshot(label, zone);
    }
  }

  /**
   * Captures {@code zone} and writes it to {@code output/diagnostics/} with a timestamped file
   * name, so a failed detector leaves behind the frame it actually saw.
   *
   * @param label a short, filesystem-safe name identifying the detector/check, used in the file
   *     name
   * @param zone the screen region to capture
   */
  public static void saveFailureScreenshot(String label, Rectangle zone) {
    BufferedImage capture = ScreenManager.captureZone(zone);
    String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
    File file = new File(OUTPUT_DIR, label + "_" + timestamp + ".png");

    File parent = file.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }

    try {
      ImageIO.write(capture, "png", file);
      logger.info("Saved diagnostic screenshot: {}", file.getPath());
    } catch (IOException e) {
      logger.error("Failed to save diagnostic screenshot '{}': {}", file.getPath(), e.getMessage());
    }
  }
}
