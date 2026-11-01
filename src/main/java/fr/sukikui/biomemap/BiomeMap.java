package fr.sukikui.biomemap;

import fr.sukikui.biomemap.command.BiomeMapCommand;
import fr.sukikui.biomemap.export.BiomeExporter;
import fr.sukikui.biomemap.export.ExportSettings;
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

  @Override
  public void onEnable() {
    ensureDataFolder();
    saveDefaultConfig();

    Logger logger = getLogger();
    File dataFolder = getDataFolder();
    BiomeExporter exporter = new BiomeExporter(dataFolder);

    commandHandler =
        new BiomeMapCommand(
            this, exporter, logger, DEFAULT_CELL_SIZE,
            ExportSettings.fromConfig(getConfig(), logger));
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
}
