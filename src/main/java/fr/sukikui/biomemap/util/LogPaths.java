package fr.sukikui.biomemap.util;

import java.io.File;
import java.nio.file.Path;

/** Formats export paths relative to the server directory where possible. */
public final class LogPaths {

  private LogPaths() {
  }

  /** Returns the existing server-relative display format for an output file. */
  public static String format(File file, File worldContainer) {
    Path absolute = file.toPath().toAbsolutePath().normalize();
    Path serverRoot = worldContainer.toPath().toAbsolutePath().normalize();
    Path rootParent = serverRoot.getParent();
    if (rootParent != null && absolute.startsWith(rootParent)) {
      return rootParent.relativize(absolute).toString().replace('\\', '/');
    }
    if (absolute.startsWith(serverRoot)) {
      String relative = serverRoot.relativize(absolute).toString().replace('\\', '/');
      return serverRoot.getFileName() + "/" + relative;
    }
    return absolute.toString().replace('\\', '/');
  }
}
