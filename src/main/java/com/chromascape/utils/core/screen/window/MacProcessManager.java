package com.chromascape.utils.core.screen.window;

import java.util.Arrays;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A class whose sole responsibility is to provide a native macOS implementation to return the
 * process ID of RuneLite.
 *
 * <p>Mirrors the approach taken by {@link LinuxProcessManager}, which scans {@code /proc} for the
 * RuneLite main class. macOS exposes no {@code /proc}, so this implementation uses {@link
 * ProcessHandle} to enumerate processes and inspect their command lines for the same identifier.
 */
public class MacProcessManager implements ProcessManager {

  private static final Logger logger = LogManager.getLogger(MacProcessManager.class);

  /** The RuneLite main class, as it appears in the launched process's command line. */
  private static final String RUNELITE_MAIN_CLASS = "net.runelite.client.RuneLite";

  /**
   * To provide a macOS native way of grabbing and returning the Process ID of RuneLite. This is to
   * be used by RemoteInput.
   *
   * <p>Enumerates all visible processes and returns the first whose command line contains the
   * RuneLite main class. Falls back to inspecting argument arrays when the full command line is
   * unavailable, which macOS may withhold for processes the current user does not own.
   *
   * @return An integer Process ID, or {@code -1} if RuneLite could not be found
   */
  @Override
  public int getPid() {
    Optional<ProcessHandle> runelite =
        ProcessHandle.allProcesses().filter(MacProcessManager::isRunelite).findFirst();

    if (runelite.isEmpty()) {
      logger.error(
          "Could not locate a RuneLite process. Is the client running, and was it launched by this"
              + " user?");
      return -1;
    }

    int pid = (int) runelite.get().pid();
    logger.info("Found RuneLite process with pid {}", pid);
    return pid;
  }

  /**
   * Determines whether a given process is RuneLite by looking for its main class in the process's
   * command line, then in its argument array as a fallback.
   *
   * @param handle The process to inspect
   * @return true if the process appears to be RuneLite, else false
   */
  private static boolean isRunelite(ProcessHandle handle) {
    ProcessHandle.Info info = handle.info();

    if (info.commandLine().map(line -> line.contains(RUNELITE_MAIN_CLASS)).orElse(false)) {
      return true;
    }

    return info.arguments()
        .map(args -> Arrays.stream(args).anyMatch(a -> a.contains(RUNELITE_MAIN_CLASS)))
        .orElse(false);
  }
}
