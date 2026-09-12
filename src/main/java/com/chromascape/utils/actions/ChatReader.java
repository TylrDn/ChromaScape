package com.chromascape.utils.actions;

import com.chromascape.utils.core.screen.colour.ColourInstances;
import com.chromascape.utils.core.screen.colour.ColourObj;
import com.chromascape.utils.domain.ocr.Ocr;
import java.awt.Rectangle;

/**
 * Reads text out of a screen zone via OCR and optionally checks it for a phrase.
 *
 * <p><b>Flagged as requested, not evidenced.</b> Only {@code DemoFishingScript} calls {@code
 * Ocr.extractText} at all, at two sites: {@code getCurrentWorldPos} (Grid Info "Tile" zone, font
 * "Plain 12", colour White) and {@code checkChatPopup} (the "Chat" zone, font "Quill 8", colour
 * Black). That's two occurrences inside one demo, not the two-or-more-*demos* bar the rest of this
 * layer holds to. It's built anyway because item 5 of the build order asked for it explicitly —
 * flagged here the same way calibration and diagnostics are flagged as requested-not-evidenced,
 * rather than silently built as if the repetition existed.
 */
public final class ChatReader {

  private ChatReader() {}

  /**
   * Reads and returns the OCR'd text in {@code zone}.
   *
   * @param zone the screen region to read, e.g. {@code controller().zones().getChatTabs().get(
   *     "Chat")}
   * @param font the OCR font mask to use, e.g. {@code "Quill 8"} for chatbox, {@code "Plain 12"}
   *     for game UI
   * @param colourName the named colour of the text to isolate, resolved via {@link
   *     ColourInstances#getByName(String)}
   * @return the extracted text, with whitespace cleaned
   * @throws IllegalArgumentException if {@code colourName} does not resolve to a known colour
   */
  public static String read(Rectangle zone, String font, String colourName) {
    ColourObj colour = ColourInstances.getByName(colourName);
    if (colour == null) {
      throw new IllegalArgumentException("Unknown colour '" + colourName + "'");
    }
    return Ocr.extractText(zone, font, colour, true);
  }

  /**
   * Reads {@code zone} and checks whether the result contains {@code phrase}.
   *
   * @param zone the screen region to read
   * @param font the OCR font mask to use
   * @param colourName the named colour of the text to isolate
   * @param phrase the substring to look for
   * @return {@code true} if the extracted text contains {@code phrase}
   */
  public static boolean contains(Rectangle zone, String font, String colourName, String phrase) {
    return read(zone, font, colourName).contains(phrase);
  }
}
