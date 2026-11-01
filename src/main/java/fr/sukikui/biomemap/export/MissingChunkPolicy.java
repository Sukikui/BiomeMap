package fr.sukikui.biomemap.export;

import java.util.Locale;
import java.util.logging.Logger;

/** Controls whether missing terrain rejects, partially fills, or generates an export. */
public enum MissingChunkPolicy {
  ERROR,
  UNKNOWN,
  GENERATE;

  /** Reads the configured policy, rejecting missing terrain by default. */
  public static MissingChunkPolicy fromConfig(String value, Logger logger) {
    if (value != null) {
      try {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        logger.warning("Invalid export.missing-chunks '" + value
            + "'. Expected error, unknown, or generate; using error.");
      }
    }
    return ERROR;
  }

  /** Returns the configuration spelling of this policy. */
  public String configValue() {
    return name().toLowerCase(Locale.ROOT);
  }
}
