package fr.sukikui.biomemap;

import fr.sukikui.biomemap.command.BiomeMapCommand;
import fr.sukikui.biomemap.export.BiomeExporter;
import io.papermc.lib.PaperLib;
import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper plugin entry point for BiomeMap.
 */
public final class BiomeMap extends JavaPlugin {

  public static final int DEFAULT_CELL_SIZE = 16;

  private BiomeMapCommand commandHandler;
  private int chunksPerTick;
  private int maxInFlight;
  private int maxConcurrentChunks;

  @Override
  public void onEnable() {
    PaperLib.suggestPaper(this);
    ensureDataFolder();
    saveDefaultConfig();
    loadPerformanceSettings();

    Logger logger = getLogger();
    File dataFolder = getDataFolder();
    BiomeExporter exporter = new BiomeExporter(dataFolder);

    commandHandler =
        new BiomeMapCommand(
            this, exporter, logger, DEFAULT_CELL_SIZE, chunksPerTick, maxInFlight,
            maxConcurrentChunks);
    PluginCommand biomemapCommand =
        Objects.requireNonNull(
            getCommand("biomemap"), "Command biomemap not defined in plugin.yml");
    biomemapCommand.setExecutor(commandHandler);
    biomemapCommand.setTabCompleter(commandHandler);
  }

  @Override
  public void onDisable() {
    if (commandHandler != null) {
      commandHandler.cancelAllExports();
    }
  }

  private void ensureDataFolder() {
    if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
      getLogger().warning("Unable to create plugin data folder. Exports may fail.");
    }
  }

  private void loadPerformanceSettings() {
    chunksPerTick = Math.max(1, getConfig().getInt("performance.chunks-per-tick", 1));
    int configuredMaxInFlight = getConfig().getInt("performance.max-in-flight", chunksPerTick * 2);
    maxInFlight = Math.max(chunksPerTick, configuredMaxInFlight);
    maxConcurrentChunks = Math.max(
        1, getConfig().getInt("performance.max-concurrent-chunks", 64));
  }
}
