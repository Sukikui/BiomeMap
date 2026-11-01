package fr.sukikui.biomemap.export;

import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/** Shared scheduling and missing-terrain settings for one plugin instance. */
public record ExportSettings(int chunksPerTick, int maxInFlight, MissingChunkPolicy missingChunks) {

  /** Normalizes scheduling values without forcing the concurrency cap above the per-tick rate. */
  public ExportSettings {
    chunksPerTick = Math.max(1, chunksPerTick);
    maxInFlight = Math.max(1, maxInFlight);
  }

  /** Loads settings and preserves a stricter legacy concurrency cap during migration. */
  public static ExportSettings fromConfig(ConfigurationSection config, Logger logger) {
    int perTick = config.getInt("performance.chunks-per-tick", 4);
    int inFlight = Math.max(1, config.getInt("performance.max-in-flight", 16));
    if (config.contains("performance.max-concurrent-chunks", true)) {
      inFlight = Math.min(inFlight,
          Math.max(1, config.getInt("performance.max-concurrent-chunks", 64)));
      logger.warning("performance.max-concurrent-chunks is deprecated. Using the smaller cap ("
          + inFlight + "). Set performance.max-in-flight to this value and remove the old key.");
    }
    MissingChunkPolicy policy = MissingChunkPolicy.fromConfig(
        config.getString("export.missing-chunks"), logger);
    return new ExportSettings(perTick, inFlight, policy);
  }
}
