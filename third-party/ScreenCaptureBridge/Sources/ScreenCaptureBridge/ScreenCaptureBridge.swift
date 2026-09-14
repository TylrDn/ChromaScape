import CoreMedia
import CoreVideo
import Foundation
import ScreenCaptureKit

/// Owns one SCStream for a single target process and the most recently decoded BGRA frame.
///
/// Frames arrive continuously on a background queue; `CSC_GetImageBuffer` just reads whatever
/// was last stored, mirroring RemoteInput's "pull the latest rendered frame" model.
private final class CaptureTarget: NSObject, SCStreamOutput, SCStreamDelegate, @unchecked Sendable {
  let pid: Int32
  var stream: SCStream?

  private let lock = NSLock()
  private var buffer: UnsafeMutableRawPointer?
  private var bufferCapacity = 0
  private var width: Int32 = 0
  private var height: Int32 = 0

  init(pid: Int32) {
    self.pid = pid
  }

  func stream(
    _ stream: SCStream, didOutputSampleBuffer sampleBuffer: CMSampleBuffer,
    of type: SCStreamOutputType
  ) {
    guard type == .screen, let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
      return
    }

    CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
    defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }

    guard let src = CVPixelBufferGetBaseAddress(pixelBuffer) else { return }
    let w = CVPixelBufferGetWidth(pixelBuffer)
    let h = CVPixelBufferGetHeight(pixelBuffer)
    let srcStride = CVPixelBufferGetBytesPerRow(pixelBuffer)
    let dstStride = w * 4
    let needed = dstStride * h
    guard needed > 0 else { return }

    lock.lock()
    defer { lock.unlock() }

    if buffer == nil || bufferCapacity < needed {
      buffer?.deallocate()
      buffer = UnsafeMutableRawPointer.allocate(byteCount: needed, alignment: 16)
      bufferCapacity = needed
    }

    if srcStride == dstStride {
      memcpy(buffer!, src, needed)
    } else {
      // CVPixelBuffer rows can be padded past width*4 - copy row-by-row into a tightly packed buffer.
      for row in 0..<h {
        memcpy(buffer! + row * dstStride, src + row * srcStride, dstStride)
      }
    }

    width = Int32(w)
    height = Int32(h)
  }

  func stream(_ stream: SCStream, didStopWithError error: Error) {
    fputs("[ScreenCaptureBridge] pid \(pid) stream stopped: \(error)\n", stderr)
  }

  func copyLatest(outWidth: UnsafeMutablePointer<Int32>?, outHeight: UnsafeMutablePointer<Int32>?)
    -> UnsafeMutableRawPointer?
  {
    lock.lock()
    defer { lock.unlock() }
    outWidth?.pointee = width
    outHeight?.pointee = height
    return buffer
  }
}

private final class TargetRegistry: @unchecked Sendable {
  private let lock = NSLock()
  private var targets: [Int32: CaptureTarget] = [:]

  func set(_ target: CaptureTarget, for pid: Int32) {
    lock.lock()
    defer { lock.unlock() }
    targets[pid] = target
  }

  func get(_ pid: Int32) -> CaptureTarget? {
    lock.lock()
    defer { lock.unlock() }
    return targets[pid]
  }

  func remove(_ pid: Int32) -> CaptureTarget? {
    lock.lock()
    defer { lock.unlock() }
    return targets.removeValue(forKey: pid)
  }
}

private let registry = TargetRegistry()

/// Minimal lock-guarded box, since `OSAllocatedUnfairLock` needs a newer os module import than
/// this toolchain resolves cleanly for a captured `Bool` inside a detached `Task`.
private final class LockedFlag: @unchecked Sendable {
  private let lock = NSLock()
  private var value: Bool

  init(_ initial: Bool) { self.value = initial }

  func set(_ newValue: Bool) {
    lock.lock()
    defer { lock.unlock() }
    value = newValue
  }

  func get() -> Bool {
    lock.lock()
    defer { lock.unlock() }
    return value
  }
}

/// Finds the largest on-screen window owned by `pid` and starts a live SCStream capture of it.
/// Blocks the calling thread (JNA's call, off the JVM's own event loop) until either capture has
/// started or `timeoutSeconds` elapses.
///
/// - Returns: `true` if capture started, `false` on any failure (see stderr for the reason).
@_cdecl("CSC_StartCapture")
public func CSC_StartCapture(_ pid: Int32) -> Bool {
  let semaphore = DispatchSemaphore(value: 0)
  let started = LockedFlag(false)

  Task {
    do {
      let content = try await SCShareableContent.excludingDesktopWindows(
        false, onScreenWindowsOnly: false)
      let candidates = content.windows.filter { $0.owningApplication?.processID == pid }
      guard
        let window = candidates.max(by: {
          ($0.frame.width * $0.frame.height) < ($1.frame.width * $1.frame.height)
        })
      else {
        fputs(
          "[ScreenCaptureBridge] no SCWindow owned by pid \(pid) (\(candidates.count) candidates)\n",
          stderr)
        semaphore.signal()
        return
      }
      fputs(
        "[ScreenCaptureBridge] pid \(pid): capturing window '\(window.title ?? "")' frame=\(window.frame)\n",
        stderr)

      let target = CaptureTarget(pid: pid)
      let filter = SCContentFilter(desktopIndependentWindow: window)
      let config = SCStreamConfiguration()
      config.width = max(Int(window.frame.width), 1)
      config.height = max(Int(window.frame.height), 1)
      config.pixelFormat = kCVPixelFormatType_32BGRA
      config.showsCursor = false
      config.queueDepth = 3

      let stream = SCStream(filter: filter, configuration: config, delegate: target)
      try stream.addStreamOutput(
        target, type: .screen, sampleHandlerQueue: DispatchQueue(label: "chromascape.capture.\(pid)"))
      target.stream = stream

      try await stream.startCapture()

      registry.set(target, for: pid)
      started.set(true)
    } catch {
      fputs("[ScreenCaptureBridge] capture setup failed for pid \(pid): \(error)\n", stderr)
    }
    semaphore.signal()
  }

  _ = semaphore.wait(timeout: .now() + 10)
  return started.get()
}

/// Returns a pointer to the most recently captured BGRA frame for `pid`, or `nil` if capture was
/// never started (or the process has no frame yet). `outWidth`/`outHeight` are only valid when a
/// non-nil pointer is returned.
@_cdecl("CSC_GetImageBuffer")
public func CSC_GetImageBuffer(
  _ pid: Int32, _ outWidth: UnsafeMutablePointer<Int32>?, _ outHeight: UnsafeMutablePointer<Int32>?
) -> UnsafeMutableRawPointer? {
  registry.get(pid)?.copyLatest(outWidth: outWidth, outHeight: outHeight)
}

/// Stops the SCStream for `pid`, if one is running, and drops its buffer.
@_cdecl("CSC_StopCapture")
public func CSC_StopCapture(_ pid: Int32) {
  guard let stream = registry.remove(pid)?.stream else { return }
  let semaphore = DispatchSemaphore(value: 0)
  Task {
    try? await stream.stopCapture()
    semaphore.signal()
  }
  _ = semaphore.wait(timeout: .now() + 5)
}
