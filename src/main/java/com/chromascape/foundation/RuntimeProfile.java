package com.chromascape.foundation;

import java.util.Arrays;
import java.util.Locale;

/**
 * Which capture and input mechanism the controller wires up at start.
 *
 * <p>Selected once per process from the {@value #PROFILE_PROPERTY} system property on the
 * <em>application</em> JVM. The {@code bootRun} task in {@code build.gradle.kts} forwards every
 * {@code chromascape.*} property from the Gradle command line; the VS Code launch configurations
 * and the {@code run-*.sh} entrypoints set it directly.
 *
 * <table>
 * <caption>Profiles</caption>
 * <tr><th>Value</th><th>Frames from</th><th>Input to</th><th>Needs</th></tr>
 * <tr><td>{@code remoteinput}</td><td>RemoteInput frame hook</td><td>RemoteInput</td>
 *     <td>RuneLite running; the hook does not install on current macOS</td></tr>
 * <tr><td>{@code capturekit}</td><td>macOS ScreenCaptureKit</td><td>RemoteInput</td>
 *     <td>RuneLite running, Screen Recording permission for the JVM</td></tr>
 * <tr><td>{@code replay}</td><td>PNG fixtures on disk</td><td>a headless device (nothing)</td>
 *     <td>nothing — runs on any OS with no client and no display</td></tr>
 * </table>
 *
 * <p>Independent of dry-run: {@code chromascape.dryRun} decides whether input is sent, this decides
 * where frames come from and what "sending input" means. {@code replay} plus dry-run off exercises
 * the live code path against a device that records instead of sends.
 */
public enum RuntimeProfile {
  /** Upstream default: RemoteInput for both frames and input. */
  REMOTEINPUT,
  /** macOS: ScreenCaptureKit frames, RemoteInput input. */
  CAPTUREKIT,
  /** Headless: recorded frames, no client, no native code. */
  REPLAY;

  /** System property selecting the profile. Values are the enum names, case-insensitive. */
  public static final String PROFILE_PROPERTY = "chromascape.profile";

  /**
   * Pre-profile opt-in flag for the capture bridge. Still honoured when {@value #PROFILE_PROPERTY}
   * is unset, so an existing {@code -Dchromascape.captureBridge=true} launch keeps working.
   */
  public static final String LEGACY_CAPTURE_BRIDGE_PROPERTY = "chromascape.captureBridge";

  /**
   * Reads the profile from system properties.
   *
   * @return the selected profile; {@link #REMOTEINPUT} when nothing is set
   * @throws IllegalArgumentException if {@value #PROFILE_PROPERTY} holds an unknown value
   */
  public static RuntimeProfile fromSystemProperties() {
    return parse(
        System.getProperty(PROFILE_PROPERTY), System.getProperty(LEGACY_CAPTURE_BRIDGE_PROPERTY));
  }

  /**
   * Resolves a profile from the two property values. Package-visible for tests.
   *
   * @param profileValue the {@value #PROFILE_PROPERTY} value, possibly {@code null} or blank
   * @param legacyCaptureBridgeValue the {@value #LEGACY_CAPTURE_BRIDGE_PROPERTY} value, possibly
   *     {@code null}
   * @return the resolved profile
   * @throws IllegalArgumentException if {@code profileValue} is set but unknown
   */
  static RuntimeProfile parse(String profileValue, String legacyCaptureBridgeValue) {
    if (profileValue == null || profileValue.isBlank()) {
      return Boolean.parseBoolean(legacyCaptureBridgeValue) ? CAPTUREKIT : REMOTEINPUT;
    }
    String normalised =
        profileValue.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
    for (RuntimeProfile profile : values()) {
      if (profile.name().equals(normalised)) {
        return profile;
      }
    }
    throw new IllegalArgumentException(
        "Unknown "
            + PROFILE_PROPERTY
            + " value '"
            + profileValue
            + "'; expected one of "
            + Arrays.toString(
                Arrays.stream(values()).map(p -> p.name().toLowerCase(Locale.ROOT)).toArray()));
  }

  /**
   * Whether this profile needs a running RuneLite process to pair with.
   *
   * @return {@code true} for every profile except {@link #REPLAY}
   */
  public boolean requiresClient() {
    return this != REPLAY;
  }
}
