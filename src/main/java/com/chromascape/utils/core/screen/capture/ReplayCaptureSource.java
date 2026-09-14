package com.chromascape.utils.core.screen.capture;

import com.chromascape.utils.core.statistics.StatisticsManager;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Frames replayed from PNG files on disk. No client, no display, no native library.
 *
 * <p>This is the capture source behind the {@code replay} runtime profile: it lets the whole
 * pipeline — zones, colour detection, template matching, OCR, the decision layer, {@code
 * ExecutionGate}, verification and halt paths — run headless against recorded screenshots, on any
 * OS, deterministically. What it cannot do is prove anything about the live client: a fixture only
 * shows what the client looked like when it was recorded.
 *
 * <h2>Fixture directory</h2>
 *
 * <p>Read from the {@value #FIXTURES_PROPERTY} system property, default {@value
 * #DEFAULT_FIXTURES_DIR} (relative to the working directory, i.e. the {@code framework/} root, so
 * the default resolves to the project's {@code calibration/fixtures/}). Every {@code *.png} in the
 * directory is loaded eagerly, sorted by file name, and validated to share one size — the zone
 * manager computes zones once from the bounds, so mixed sizes would silently misplace every zone.
 *
 * <h2>Frame selection</h2>
 *
 * <p>The frame served is indexed by the script's cycle counter: cycle 1 sees the first file, cycle
 * 2 the second, and the last file is held for every cycle after that. A single-file directory is
 * therefore a static scene. Name files so they sort in scenario order, e.g. {@code
 * 01-ore-visible.png}, {@code 02-inventory-full.png}. Within one cycle every capture returns the
 * same frame, matching the "one fresh capture per decision" rule while keeping perception
 * consistent for that decision.
 */
public final class ReplayCaptureSource implements CaptureSource {

  /** System property naming the fixture directory. */
  public static final String FIXTURES_PROPERTY = "chromascape.replay.fixtures";

  /** Default fixture directory, relative to the {@code framework/} working directory. */
  public static final String DEFAULT_FIXTURES_DIR = "../calibration/fixtures";

  private static final Logger logger = LogManager.getLogger(ReplayCaptureSource.class);

  private final Path directory;
  private final List<Path> files;
  private final List<BufferedImage> frames;
  private final Rectangle bounds;
  private volatile int lastLoggedIndex = -1;

  /**
   * Builds a source from the {@value #FIXTURES_PROPERTY} system property, or the default directory
   * if it is unset.
   *
   * @return a loaded, validated source
   * @throws IllegalStateException if the directory is missing, empty, or its frames disagree on
   *     size
   */
  public static ReplayCaptureSource fromSystemProperties() {
    return new ReplayCaptureSource(
        Path.of(System.getProperty(FIXTURES_PROPERTY, DEFAULT_FIXTURES_DIR)));
  }

  /**
   * Loads every {@code *.png} in {@code directory}, sorted by file name.
   *
   * @param directory the fixture directory
   * @throws IllegalStateException if the directory is missing, contains no PNGs, a PNG fails to
   *     decode, or the PNGs do not all share one size; the message names the offending path
   */
  public ReplayCaptureSource(Path directory) {
    this.directory = directory.toAbsolutePath().normalize();
    if (!Files.isDirectory(this.directory)) {
      throw new IllegalStateException(
          "Replay fixture directory does not exist: "
              + this.directory
              + " (set -D"
              + FIXTURES_PROPERTY
              + "=<dir>)");
    }
    this.files = listPngs(this.directory);
    if (files.isEmpty()) {
      throw new IllegalStateException(
          "Replay fixture directory contains no .png files: " + this.directory);
    }
    this.frames = new ArrayList<>(files.size());
    Rectangle first = null;
    for (Path file : files) {
      BufferedImage image = read(file);
      Rectangle size = new Rectangle(0, 0, image.getWidth(), image.getHeight());
      if (first == null) {
        first = size;
      } else if (!first.equals(size)) {
        throw new IllegalStateException(
            "Replay fixtures must share one size: "
                + files.get(0).getFileName()
                + " is "
                + first.width
                + "x"
                + first.height
                + " but "
                + file.getFileName()
                + " is "
                + size.width
                + "x"
                + size.height);
      }
      frames.add(image);
    }
    this.bounds = first;
    logger.info(
        "Replay source loaded {} frame(s) at {}x{} from {}",
        frames.size(),
        bounds.width,
        bounds.height,
        this.directory);
  }

  private static List<Path> listPngs(Path directory) {
    try (Stream<Path> entries = Files.list(directory)) {
      return entries
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
          .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot list replay fixture directory " + directory, e);
    }
  }

  private static BufferedImage read(Path file) {
    try {
      BufferedImage image = ImageIO.read(file.toFile());
      if (image == null) {
        throw new IllegalStateException("Not a decodable image: " + file);
      }
      return image;
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read replay fixture " + file, e);
    }
  }

  /**
   * Number of frames loaded.
   *
   * @return the frame count, at least one
   */
  public int frameCount() {
    return frames.size();
  }

  /**
   * Index of the frame the current cycle sees: {@code cycles - 1}, clamped to the loaded range.
   *
   * @return the zero-based frame index for the current script cycle
   */
  public int currentIndex() {
    int cycle = StatisticsManager.getCycles();
    return Math.max(0, Math.min(cycle - 1, frames.size() - 1));
  }

  /**
   * File name of the frame the current cycle sees.
   *
   * @return the fixture file name
   */
  public String currentFrameName() {
    return files.get(currentIndex()).getFileName().toString();
  }

  @Override
  public BufferedImage captureWindow() {
    int index = currentIndex();
    if (index != lastLoggedIndex) {
      lastLoggedIndex = index;
      logger.info(
          "Replay frame {}/{}: {}", index + 1, frames.size(), files.get(index).getFileName());
    }
    return copy(frames.get(index));
  }

  private static BufferedImage copy(BufferedImage source) {
    BufferedImage target =
        new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
    Graphics2D g = target.createGraphics();
    g.drawImage(source, 0, 0, null);
    g.dispose();
    return target;
  }

  @Override
  public Rectangle getWindowBounds() {
    return new Rectangle(bounds);
  }

  @Override
  public String describe() {
    return "replay (" + frames.size() + " frame(s) from " + directory + ")";
  }

  /** No-op: frames are plain heap images. */
  @Override
  public void close() {
    // Intentionally empty; nothing native to release.
  }
}
