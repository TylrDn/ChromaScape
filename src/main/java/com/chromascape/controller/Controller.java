package com.chromascape.controller;

import com.chromascape.foundation.ExecutionGate;
import com.chromascape.foundation.RuntimeProfile;
import com.chromascape.utils.core.input.guard.GuardedRemoteInput;
import com.chromascape.utils.core.input.guard.HeadlessRemoteInput;
import com.chromascape.utils.core.input.keyboard.VirtualKeyboardUtils;
import com.chromascape.utils.core.input.mouse.VirtualMouseUtils;
import com.chromascape.utils.core.input.remoteinput.RemoteInput;
import com.chromascape.utils.core.screen.capture.CaptureSource;
import com.chromascape.utils.core.screen.capture.RemoteInputCaptureSource;
import com.chromascape.utils.core.screen.capture.ReplayCaptureSource;
import com.chromascape.utils.core.screen.capture.ScreenCaptureKitCaptureSource;
import com.chromascape.utils.core.screen.capturekit.ScreenCaptureBridge;
import com.chromascape.utils.core.screen.window.ProcessManagerFactory;
import com.chromascape.utils.core.screen.window.ScreenManager;
import com.chromascape.utils.domain.ocr.Ocr;
import com.chromascape.utils.domain.walker.Walker;
import com.chromascape.utils.domain.zones.ZoneManager;
import java.time.Duration;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The central controller managing the lifecycle and access to core stateful utilities for input
 * simulation, screen capture, zone management, and hotkey listening.
 *
 * <p>Responsible for initializing and shutting down resources and enforcing runtime state checks to
 * prevent access to utilities when inactive.
 *
 * <p>This class abstracts and coordinates lower-level modules required for automation scripts.
 *
 * <p>FORK DIVERGENCE — {@link #init()} wires capture and input according to the {@link
 * RuntimeProfile} selected by {@code -Dchromascape.profile}. With no profile set it behaves as
 * upstream does (RemoteInput for both), plus a dry-run guard on the input device.
 */
public class Controller {

  /** Represents the current running state of the controller. */
  private enum ControllerState {
    STOPPED,
    RUNNING
  }

  /**
   * How long to wait for ScreenCaptureKit's first frame before giving up. The stream is
   * asynchronous; the zone manager needs real bounds before it can map anything.
   */
  private static final Duration CAPTUREKIT_FIRST_FRAME_TIMEOUT = Duration.ofSeconds(3);

  /**
   * Bounded retry for pairing RemoteInput with a just-found pid. Covers a transient race only: the
   * process was found by {@link ProcessManagerFactory} but has not finished registering with EIOS
   * yet, so {@code new RemoteInput(pid)} throws "Target Not Found" even though the pid is real.
   * Observed twice for the same pid, about a minute apart, on 2026-09-13 ({@code
   * output/logs/chromascape-2026-09-13-*.log.gz}). This does <b>not</b> address the injection route
   * itself being unrecorded or reverted (C-4) or the dead RemoteInput frame hook (a separate,
   * permanently refused subsystem) — see {@code docs/reference/macos-task-for-pid.md}.
   */
  private static final int PAIR_MAX_ATTEMPTS = 3;

  /**
   * Wait between pairing attempts. Not humanisation — a technical retry interval (Rule 5 does not
   * apply; this never touches input or timing visible to the client).
   */
  private static final Duration PAIR_RETRY_BACKOFF = Duration.ofMillis(750);

  private ControllerState state;

  private RuntimeProfile profile;
  private RemoteInput remoteInput;
  private RemoteInput inputDevice;
  private CaptureSource captureSource;
  private VirtualMouseUtils virtualMouseUtils;
  private VirtualKeyboardUtils virtualKeyboardUtils;
  private ZoneManager zoneManager;
  private Walker walker;
  private static final Logger logger = LogManager.getLogger(Controller.class);

  /** Constructs a new Controller instance. */
  public Controller() {
    this.state = ControllerState.STOPPED;
  }

  /**
   * Initializes and starts the controller, setting up all core utilities needed for the bot to
   * operate, including input devices, screen capture, and zone management.
   *
   * <p>Reads the {@link RuntimeProfile}, then builds the capture source and input device that
   * profile calls for, then the utilities that sit on top of them. Any failure propagates so that
   * {@code BaseScript.run()} can log it and still call {@link #shutdown()}.
   */
  public void init() {
    profile = RuntimeProfile.fromSystemProperties();
    logger.info("Runtime profile: {}", profile.name().toLowerCase());

    logger.info("Setting up Font masks...");
    Ocr.loadFont("Plain 11");
    Ocr.loadFont("Plain 12");
    Ocr.loadFont("Bold 12");

    switch (profile) {
      case REPLAY -> {
        ReplayCaptureSource replay = ReplayCaptureSource.fromSystemProperties();
        captureSource = replay;
        // No process to pair with: the headless device is both the "remoteInput" we own and the
        // device the utilities drive. Frames never come from it.
        remoteInput = new HeadlessRemoteInput(replay.getWindowBounds());
        inputDevice = remoteInput;
      }
      case CAPTUREKIT -> {
        int pid = pairRemoteInput();
        logger.info("Setting up ScreenCaptureBridge for pid {}...", pid);
        ScreenCaptureKitCaptureSource kit =
            new ScreenCaptureKitCaptureSource(new ScreenCaptureBridge(pid));
        captureSource = kit;
        if (!kit.awaitFirstFrame(CAPTUREKIT_FIRST_FRAME_TIMEOUT)) {
          throw new IllegalStateException(
              "ScreenCaptureKit started but delivered no frame within "
                  + CAPTUREKIT_FIRST_FRAME_TIMEOUT.toSeconds()
                  + "s. Check System Settings > Privacy & Security > Screen Recording for the JVM"
                  + " that launched ChromaScape, and that the RuneLite window is on screen.");
        }
        // A live run whose frame space differs from the input space would click in the wrong
        // place, and a dry run cannot show that. Refuse it here, with both sizes in the message.
        ScreenCaptureKitCaptureSource.requireInputSpaceMatch(
            kit.getWindowBounds(), remoteInput.getTargetDimensions(), ExecutionGate.isDryRun());
        inputDevice = new GuardedRemoteInput(remoteInput);
      }
      case REMOTEINPUT -> {
        pairRemoteInput();
        captureSource = new RemoteInputCaptureSource(remoteInput);
        inputDevice = new GuardedRemoteInput(remoteInput);
      }
      default -> throw new IllegalStateException("Unhandled profile " + profile);
    }
    ScreenManager.setCaptureSource(captureSource);
    logger.info("Capture source: {}", captureSource.describe());

    // Initialize virtual input utilities with current window bounds and fullscreen status
    logger.info("Initialising mouse and keyboard utils...");
    virtualMouseUtils = new VirtualMouseUtils(inputDevice);
    virtualKeyboardUtils = new VirtualKeyboardUtils(inputDevice);

    logger.info("Pre-loading and instantiating zones...");
    // Initialize zone management with fixed mode option
    zoneManager = new ZoneManager();
    // Initialise gameView instead of LazyLoading, to improve startup overhead
    zoneManager.getGameView();

    state = ControllerState.RUNNING;

    // Initialises a walker to provide the script with Walking functionality through the DAX API
    walker = new Walker(this);
    logger.info("Controller State: {}", state);
  }

  /**
   * Finds the RuneLite process and pairs RemoteInput with it, assigning {@link #remoteInput}.
   *
   * @return the RuneLite pid
   */
  private int pairRemoteInput() {
    logger.info("Setting up Remote Input Library...");
    int pid = ProcessManagerFactory.getProcessManager().getPid();
    remoteInput =
        retryWithBackoff(
            "pair RemoteInput with pid " + pid,
            PAIR_MAX_ATTEMPTS,
            PAIR_RETRY_BACKOFF,
            () -> new RemoteInput(pid));
    return pid;
  }

  /**
   * Retries {@code attempt} up to {@code maxAttempts} times, sleeping {@code backoff} between
   * attempts, with one structured {@code [INJECTION]} log line per attempt so a failure is
   * traceable from the log alone, without reproducing it.
   *
   * <p>Package-private and generic in {@code T} purely so {@code ControllerInjectionRetryTest} can
   * exercise the retry/backoff/logging behaviour with a fake {@link Supplier}, without a native
   * library — {@link #pairRemoteInput()} is the only call site (Rule 6: not promoted beyond that
   * until a second one exists).
   *
   * @param label what is being attempted, for the log lines
   * @param maxAttempts total attempts, including the first; must be at least 1
   * @param backoff sleep between a failed attempt and the next one; not applied after the last
   * @param attempt the operation to retry; a {@link RuntimeException} counts as a failed attempt
   * @return the first successful result
   * @throws RuntimeException the exception from the final attempt, if every attempt failed
   */
  static <T> T retryWithBackoff(
      String label, int maxAttempts, Duration backoff, Supplier<T> attempt) {
    RuntimeException lastFailure = null;
    for (int i = 1; i <= maxAttempts; i++) {
      try {
        T result = attempt.get();
        if (i > 1) {
          logger.info("[INJECTION] {} succeeded on attempt {}/{}", label, i, maxAttempts);
        }
        return result;
      } catch (RuntimeException e) {
        lastFailure = e;
        logger.warn(
            "[INJECTION] {} attempt {}/{} failed: {}", label, i, maxAttempts, e.getMessage());
        if (i < maxAttempts) {
          sleepQuietly(backoff);
        }
      }
    }
    logger.error(
        "[INJECTION] {} gave up after {} attempt(s), {} apart. This is the C-4 route — see"
            + " docs/reference/macos-task-for-pid.md \"Record the outcome here\": which JDK"
            + " launched RuneLite, whether it was signed, RuneLite.app vs a plain java -jar"
            + " launch, and any DYLD_INSERT_LIBRARIES preload. A bounded retry cannot fix an"
            + " unrecorded or reverted route.",
        label,
        maxAttempts,
        backoff);
    throw lastFailure;
  }

  private static void sleepQuietly(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Shuts down the controller and releases all resources.
   *
   * <p>Closes the capture source, releases the input device, and prevents further access to
   * stateful utilities until re-initialized. Every step is null-guarded because {@link #init()} may
   * have failed part-way through.
   */
  public void shutdown() {
    if (captureSource != null) {
      captureSource.close();
      captureSource = null;
    }
    ScreenManager.setCaptureSource(null);
    if (inputDevice != null && inputDevice != remoteInput) {
      inputDevice.close(); // the guard: logs its allowed/suppressed totals, owns nothing native
    }
    inputDevice = null;
    if (remoteInput != null) {
      remoteInput.close();
      remoteInput = null;
    }
    state = ControllerState.STOPPED;
    logger.info("Shutting down");
  }

  /**
   * The runtime profile this controller was initialised with.
   *
   * @return the profile, or {@code null} before {@link #init()} has run
   */
  public RuntimeProfile profile() {
    return profile;
  }

  /**
   * Provides access to the virtual mouse utility.
   *
   * @return The virtual mouse utility for simulated mouse actions.
   * @throws IllegalStateException if called while the controller is not running.
   */
  public VirtualMouseUtils mouse() {
    assertRunning("VirtualMouseUtils");
    return virtualMouseUtils;
  }

  /**
   * Provides access to the virtual keyboard utility.
   *
   * @return The virtual keyboard utility for simulated keyboard actions.
   * @throws IllegalStateException if called while the controller is not running.
   */
  public VirtualKeyboardUtils keyboard() {
    assertRunning("VirtualKeyboardUtils");
    return virtualKeyboardUtils;
  }

  /**
   * Provides access to the zone manager utility.
   *
   * <p>The ZoneManager maintains mappings of UI sub-zones to support interaction with different
   * client interface areas.
   *
   * @return The ZoneManager instance.
   * @throws IllegalStateException if called while the controller is not running.
   */
  public ZoneManager zones() {
    assertRunning("ZoneManager");
    return zoneManager;
  }

  /**
   * Provides access to the walker domain utility.
   *
   * @return The walker utility, to be able to pathfind in-game.
   */
  public Walker walker() {
    assertRunning("Walker");
    return walker;
  }

  /**
   * Checks that the controller is currently running before allowing access to any stateful utility,
   * logging and throwing an exception if not.
   *
   * @param component The name of the utility being accessed.
   * @throws IllegalStateException if the controller is not running.
   */
  private void assertRunning(String component) {
    if (state != ControllerState.RUNNING) {
      if (logger != null) {
        logger.info("{} accessed while bot is not running.", component);
      }
      throw new IllegalStateException(component + " accessed while bot is not running.");
    }
  }
}
