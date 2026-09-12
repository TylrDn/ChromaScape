package com.chromascape.foundation;

import com.chromascape.utils.core.screen.colour.ColourInstances;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Verifies that a script's declared colour and template-image dependencies actually resolve before
 * the script is allowed to run.
 *
 * <p>Two failure modes motivate this class, both verified against source rather than assumed:
 *
 * <ul>
 *   <li>{@link ColourInstances#getByName(String)} returns {@code null} for any colour name that is
 *       misspelled, missing from {@code colours.json}, or unresolvable because the process was
 *       started from the wrong working directory (colours.json load failure is swallowed to an
 *       empty list at class-init time, not thrown). Every caller downstream receives that {@code
 *       null} silently.
 *   <li>{@code TemplateMatching.match} turns a missing template resource into an ordinary failed
 *       {@code MatchResult} ("Template image is empty"), indistinguishable from the template
 *       genuinely not being on screen.
 * </ul>
 *
 * <p>This gate turns both failure modes into one specific, loud exception raised before the
 * script's first cycle, instead of a run that quietly never clicks anything.
 */
public final class CalibrationGate {

  private static final Logger logger = LogManager.getLogger(CalibrationGate.class);

  private CalibrationGate() {}

  /**
   * Checks that every named colour and template image a script depends on actually resolves.
   *
   * @param requiredColours colour names looked up via {@link ColourInstances#getByName(String)}
   *     (e.g. {@code "Cyan"}); pass an empty list if the script uses none
   * @param requiredImagePaths absolute classpath resource paths as passed to {@code
   *     TemplateMatching.match} (e.g. {@code "/images/user/Grapes.png"}); pass an empty list if the
   *     script uses none
   * @throws IllegalStateException if any colour or image fails to resolve; the message names every
   *     failure found, not just the first
   */
  public static void check(List<String> requiredColours, List<String> requiredImagePaths) {
    List<String> failures = new ArrayList<>();

    for (String colourName : requiredColours) {
      if (ColourInstances.getByName(colourName) == null) {
        failures.add(
            "colour '" + colourName + "' not found (check colours.json and working directory)");
      }
    }

    for (String imagePath : requiredImagePaths) {
      if (CalibrationGate.class.getResourceAsStream(imagePath) == null) {
        failures.add(
            "template image '"
                + imagePath
                + "' not found on classpath (path must be absolute, "
                + "starting with '/')");
      }
    }

    if (!failures.isEmpty()) {
      String message = "Calibration failed:\n  - " + String.join("\n  - ", failures);
      logger.error(message);
      throw new IllegalStateException(message);
    }

    logger.info(
        "Calibration passed: {} colour(s), {} image(s) resolved.",
        requiredColours.size(),
        requiredImagePaths.size());
  }
}
