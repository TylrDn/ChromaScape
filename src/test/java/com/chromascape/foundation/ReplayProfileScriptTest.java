package com.chromascape.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chromascape.utils.actions.PointSelector;
import com.chromascape.utils.core.screen.capture.ReplayCaptureSource;
import com.chromascape.utils.core.screen.window.ScreenManager;
import com.chromascape.utils.core.state.BotState;
import com.chromascape.utils.core.state.StateManager;
import com.chromascape.utils.core.statistics.StatisticsManager;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a complete script lifecycle — {@code BaseScript.run()} → {@code Controller.init()} in the
 * replay profile → calibration gate → perception → decision → gated action → verification → halt —
 * with no client, no display and no native library.
 *
 * <p>Frame 1 shows a cyan target in the game view; frame 2 shows nothing. The probe script clicks
 * the target on cycle 1 and halts on cycle 2 because its verification (re-observing the target)
 * fails. That halt is the expected end of the run, not an error.
 */
class ReplayProfileScriptTest {

  /** RuneLite fixed-mode canvas size. */
  private static final int WIDTH = 765;

  private static final int HEIGHT = 503;
  private static final Rectangle TARGET = new Rectangle(300, 200, 60, 40);

  @TempDir Path fixtures;

  private String previousProfile;
  private String previousFixtures;

  /** Minimal script following the four-layer contract; one target, one click, halt when gone. */
  static final class Probe extends AbstractChromaScript {
    final List<Point> observed = new ArrayList<>();

    Probe() {}

    @Override
    protected List<String> requiredColours() {
      return List.of("Cyan");
    }

    @Override
    protected List<String> requiredImages() {
      return List.of();
    }

    @Override
    protected void cycle() {
      // Perception: facts only.
      BufferedImage gameView = controller().zones().getGameView();
      Point target = PointSelector.getRandomPointInColour(gameView, "Cyan", 15);
      observed.add(target);
      // Decision + action + verification collapse to one step for a probe this small:
      // no target visible means the previous click's expected outcome is confirmed, so stop.
      if (target == null) {
        haltAndStop("Cyan target no longer visible - verification of the click succeeded");
      }
      clickPointOrHalt(target, "fast");
      checkInterrupted();
    }
  }

  @BeforeEach
  void selectReplayProfile() throws IOException {
    writeFrame("01-target-visible.png", true);
    writeFrame("02-target-gone.png", false);
    previousProfile = System.getProperty(RuntimeProfile.PROFILE_PROPERTY);
    previousFixtures = System.getProperty(ReplayCaptureSource.FIXTURES_PROPERTY);
    System.setProperty(RuntimeProfile.PROFILE_PROPERTY, "replay");
    System.setProperty(ReplayCaptureSource.FIXTURES_PROPERTY, fixtures.toString());
    StatisticsManager.reset();
    StateManager.setState(BotState.WAITING);
  }

  @AfterEach
  void restore() {
    restoreProperty(RuntimeProfile.PROFILE_PROPERTY, previousProfile);
    restoreProperty(ReplayCaptureSource.FIXTURES_PROPERTY, previousFixtures);
    ExecutionGate.setDryRun(true);
    ScreenManager.setCaptureSource(null);
  }

  private static void restoreProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }

  private void writeFrame(String name, boolean withTarget) throws IOException {
    BufferedImage frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    if (withTarget) {
      for (int y = TARGET.y; y < TARGET.y + TARGET.height; y++) {
        for (int x = TARGET.x; x < TARGET.x + TARGET.width; x++) {
          frame.setRGB(x, y, Color.CYAN.getRGB());
        }
      }
    }
    ImageIO.write(frame, "png", fixtures.resolve(name).toFile());
  }

  @Test
  void dryRunSeesTheTargetSuppressesTheClickAndHaltsWhenItIsGone() {
    ExecutionGate.setDryRun(true);
    Probe probe = new Probe();

    probe.run();

    assertNotEquals(
        BotState.ERROR,
        StateManager.getState(),
        "BaseScript.run() swallowed a failure into the log; read the test output above");
    assertEquals(2, probe.observed.size(), "one cycle per frame, then halt");
    Point seen = probe.observed.get(0);
    assertNotNull(seen, "cycle 1 must perceive the cyan target");
    assertTrue(TARGET.contains(seen), "point " + seen + " must fall inside " + TARGET);
    assertNull(probe.observed.get(1), "cycle 2 must perceive nothing");
    assertEquals(0, StatisticsManager.getInputs(), "dry-run must not count a sent input");
  }

  @Test
  void liveModeAgainstTheHeadlessDeviceExecutesTheClick() {
    ExecutionGate.setDryRun(false);
    Probe probe = new Probe();

    probe.run();

    assertNotEquals(
        BotState.ERROR,
        StateManager.getState(),
        "BaseScript.run() swallowed a failure into the log; read the test output above");
    assertEquals(2, probe.observed.size());
    assertNotNull(probe.observed.get(0));
    // ClickActions counts the click and VirtualMouseUtils counts its own move/press steps, so
    // the exact number is an implementation detail; what matters is that it is no longer zero.
    assertTrue(StatisticsManager.getInputs() >= 1, "the gated click reached the headless device");
  }
}
