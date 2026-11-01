package fr.sukikui.biomemap.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ExportSettingsTest {

  private final List<String> warnings = new ArrayList<>();
  private final Logger logger = Logger.getAnonymousLogger();

  ExportSettingsTest() {
    logger.setUseParentHandlers(false);
    logger.addHandler(new Handler() {
      @Override
      public void publish(LogRecord record) {
        warnings.add(record.getMessage());
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    });
  }

  @Test
  void defaultsToStrictTerrainChecksWithFourJobsPerTickAndSixteenInFlight() {
    assertEquals(new ExportSettings(4, 16, MissingChunkPolicy.ERROR),
        ExportSettings.fromConfig(new YamlConfiguration(), logger));
    assertTrue(warnings.isEmpty());
  }

  @Test
  void acceptsAllPoliciesAndWarnsOnInvalidValues() {
    for (MissingChunkPolicy policy : MissingChunkPolicy.values()) {
      assertEquals(policy, MissingChunkPolicy.fromConfig(policy.configValue(), logger));
    }
    assertEquals(MissingChunkPolicy.ERROR, MissingChunkPolicy.fromConfig(" ERROR ", logger));
    assertEquals(MissingChunkPolicy.ERROR, MissingChunkPolicy.fromConfig("invalid", logger));
    assertEquals(1, warnings.size());
  }

  @Test
  void preservesStricterLegacyCapWithoutRaisingItToThePerTickRate() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("performance.chunks-per-tick", 16);
    config.set("performance.max-in-flight", 8);
    config.set("performance.max-concurrent-chunks", 2);
    config.set("export.missing-chunks", "unknown");
    assertEquals(new ExportSettings(16, 2, MissingChunkPolicy.UNKNOWN),
        ExportSettings.fromConfig(config, logger));
    assertEquals(1, warnings.size());
  }

  @Test
  void ignoresLegacyDefaultAndNormalizesNonpositiveSchedulingValues() {
    YamlConfiguration config = new YamlConfiguration();
    config.addDefault("performance.max-concurrent-chunks", 1);
    config.set("performance.max-in-flight", 4);
    config.set("performance.chunks-per-tick", 0);
    assertEquals(new ExportSettings(1, 4, MissingChunkPolicy.ERROR),
        ExportSettings.fromConfig(config, logger));
    assertTrue(warnings.isEmpty());
    assertEquals(1, new ExportSettings(-1, -1, MissingChunkPolicy.ERROR).maxInFlight());
  }
}
