package com.chromascape.utils.core.screen.capturekit;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Alternative to {@link com.chromascape.utils.core.input.remoteinput.RemoteInput}'s capture path,
 * using macOS's ScreenCaptureKit instead of RemoteInput's self-modifying-code frame hook.
 *
 * <p>Exists because that hook cannot install on this OS: RemoteInput's injected dylib tries to make
 * a signed system library's page writable-and-executable to patch it, which the kernel refuses (W^X
 * enforcement, {@code KERN_PROTECTION_FAILURE}) - confirmed via direct native instrumentation, not
 * assumed. ScreenCaptureKit needs no such patching, at the cost of requiring the standard macOS
 * Screen Recording permission grant.
 *
 * <p>This class replaces only the capture half of the pipeline. Mouse and keyboard input still go
 * through {@link com.chromascape.utils.core.input.remoteinput.RemoteInput}, which is unaffected -
 * the W^X failure is specific to the capture hook, not RemoteInput's separate input-injection
 * calls.
 *
 * <p><b>Unverified:</b> RemoteInput's coordinate space is the RuneLite Java canvas only (window
 * chrome excluded), whereas ScreenCaptureKit captures the whole OS window (chrome included). Frame
 * dimensions from this class may not line up 1:1 with {@code RemoteInput.getTargetDimensions()} -
 * confirm the offset against a live client before trusting any zone/click math derived from it.
 */
public class ScreenCaptureBridge implements AutoCloseable {

  private static final String COMPILED_BINARY_FILENAME = "libScreenCaptureBridge.dylib";

  private final int pid;
  private final ScreenCaptureBridgeInterface bridge;

  /**
   * Constructs the bridge and starts capturing the target process's window.
   *
   * @param pid The process ID of the target application
   * @throws RuntimeException if no matching window was found or capture failed to start
   */
  public ScreenCaptureBridge(int pid) {
    this.pid = pid;
    this.bridge = loadBridge();
    if (!bridge.CSC_StartCapture(pid)) {
      throw new RuntimeException("ScreenCaptureBridge failed to start capture for pid: " + pid);
    }
  }

  /**
   * Loads the ScreenCaptureBridge binary. Checks for a user-built copy first, falling back to the
   * precompiled one shipped in the repo.
   *
   * @return An interface bridging to the native binary
   */
  private static ScreenCaptureBridgeInterface loadBridge() {
    Path binaryFile =
        Paths.get(
            "third-party", "ScreenCaptureBridge", ".build", "release", COMPILED_BINARY_FILENAME);
    if (!Files.exists(binaryFile)) {
      binaryFile =
          Paths.get("third-party", "ScreenCaptureBridge", "precompiled", COMPILED_BINARY_FILENAME);
    }

    try {
      return Native.load(
          binaryFile.toAbsolutePath().toString(), ScreenCaptureBridgeInterface.class);
    } catch (UnsatisfiedLinkError e) {
      throw new RuntimeException("Unable to load ScreenCaptureBridge binary from path", e);
    }
  }

  /**
   * Retrieves the most recently captured frame for the target process.
   *
   * @return A {@link CaptureFrame} wrapping the pixel buffer and its dimensions, or {@code null} if
   *     no frame has arrived yet
   */
  public synchronized CaptureFrame getImageBuffer() {
    IntByReference width = new IntByReference();
    IntByReference height = new IntByReference();
    Pointer buffer = bridge.CSC_GetImageBuffer(pid, width, height);
    if (buffer == null) {
      return null;
    }
    return new CaptureFrame(buffer, width.getValue(), height.getValue());
  }

  /** Stops the ScreenCaptureKit stream for this target. */
  @Override
  public void close() {
    bridge.CSC_StopCapture(pid);
  }

  /**
   * A captured BGRA frame: a pointer to the pixel data plus the dimensions it was captured at.
   *
   * @param buffer Pointer to the start of the BGRA pixel array
   * @param width Frame width in pixels
   * @param height Frame height in pixels
   */
  public record CaptureFrame(Pointer buffer, int width, int height) {}
}
