package com.chromascape.utils.core.screen.capture;

import com.chromascape.utils.core.input.remoteinput.RemoteInput;
import com.sun.jna.Pointer;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Frames from RemoteInput's in-process frame hook — the upstream default capture path.
 *
 * <p>Reads {@link RemoteInput#getImageBuffer()} on every call. Known limitation on this project's
 * development target: the hook cannot install on current macOS (the kernel refuses {@code
 * mach_vm_protect} on signed system libraries with {@code KERN_PROTECTION_FAILURE}), so this source
 * serves black frames there. See {@code docs/reference/live-readiness.md}.
 */
public final class RemoteInputCaptureSource implements CaptureSource {

  private final RemoteInput remoteInput;

  /**
   * Creates a source reading frames from an already-paired {@link RemoteInput}.
   *
   * @param remoteInput the paired RemoteInput; its lifecycle stays with the caller because the same
   *     object is also the input device
   */
  public RemoteInputCaptureSource(RemoteInput remoteInput) {
    this.remoteInput = remoteInput;
  }

  @Override
  public BufferedImage captureWindow() {
    Rectangle dims = remoteInput.getTargetDimensions();
    if (dims.width <= 0 || dims.height <= 0) {
      return null;
    }
    Pointer buffer = remoteInput.getImageBuffer();
    if (buffer == null) {
      return null;
    }
    int size = dims.width * dims.height * 4;
    return BgraFrames.toBufferedImage(buffer.getByteArray(0, size), dims.width, dims.height);
  }

  @Override
  public Rectangle getWindowBounds() {
    return remoteInput.getTargetDimensions();
  }

  @Override
  public String describe() {
    return "remoteinput (RemoteInput in-process frame hook)";
  }

  /** No-op: the {@link RemoteInput} is owned and closed by the controller. */
  @Override
  public void close() {
    // Intentionally empty; see Javadoc.
  }
}
