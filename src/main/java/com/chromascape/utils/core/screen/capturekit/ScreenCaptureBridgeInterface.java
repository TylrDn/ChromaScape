package com.chromascape.utils.core.screen.capturekit;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/** JNA interface to load the ScreenCaptureBridge dylib as a {@link ScreenCaptureBridge} object. */
public interface ScreenCaptureBridgeInterface extends Library {

  /**
   * Finds the target process's window and starts a live ScreenCaptureKit stream of it.
   *
   * @param pid The OS process ID to capture
   * @return Whether capture started successfully
   */
  boolean CSC_StartCapture(int pid);

  /**
   * Retrieves a pointer to the most recently captured BGRA frame for the given process.
   *
   * @param pid The OS process ID being captured
   * @param outWidth Mutated with the frame's width in pixels
   * @param outHeight Mutated with the frame's height in pixels
   * @return A pointer to the start of the BGRA pixel array, or {@code null} if no frame exists yet
   */
  Pointer CSC_GetImageBuffer(int pid, IntByReference outWidth, IntByReference outHeight);

  /**
   * Stops the stream for the given process, if one is running.
   *
   * @param pid The OS process ID to stop capturing
   */
  void CSC_StopCapture(int pid);
}
