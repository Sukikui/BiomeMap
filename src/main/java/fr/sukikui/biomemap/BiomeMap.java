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

  public static final int DEFAULT_CELL_SIZE = 32;
  public static final int SCAN_RADIUS = 5000;

    @Override
  public void onEnable() {
    PaperLib.suggestPaper(this);
    ensureDataFolder();

    Logger logger = getLogger();
    File dataFolder = getDataFolder();
    BiomeExporter exporter = new BiomeExporter(dataFolder, SCAN_RADIUS);

    BiomeMapCommand commandHandler = new BiomeMapCommand(exporter, logger, DEFAULT_CELL_SIZE);
    PluginCommand biomemapCommand =
        Objects.requireNonNull(getCommand("biomemap"), "Command biomemap not defined in plugin.yml");
    biomemapCommand.setExecutor(commandHandler);
    biomemapCommand.setTabCompleter(commandHandler);
  }

  private void ensureDataFolder() {
    if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
      getLogger().warning("Unable to create plugin data folder. Exports may fail.");
    }
  }
}
