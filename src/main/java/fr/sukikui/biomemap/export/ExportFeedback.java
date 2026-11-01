package fr.sukikui.biomemap.export;

import fr.sukikui.biomemap.util.LogPaths;
import fr.sukikui.biomemap.util.ProgressFormatter;
import java.io.File;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Resolves the current invoker and formats export progress and output notifications. */
final class ExportFeedback {

  private static final String PREFIX = "§8[§b§lBiomeMap§8] §r";
  private final Plugin plugin;
  private final CommandSender sender;
  private final UUID playerId;
  private final Logger logger;

  ExportFeedback(Plugin plugin, CommandSender sender, UUID playerId, Logger logger) {
    this.plugin = plugin;
    this.sender = sender;
    this.playerId = playerId;
    this.logger = logger;
  }

  void info(String message) {
    send("§7" + message);
  }

  void error(String message) {
    send("§c§lError: §c" + message);
    if (!console()) {
      logger.warning(message);
    }
  }

  void progress(String phase, ProgressFormatter.ProgressSnapshot progress) {
    info(phase + ": " + ProgressFormatter.formatChatLine(
        progress.progressPercent(), progress.completedChunks(), progress.totalChunks(),
        progress.elapsedMs(), progress.etaMs(), ProgressFormatter.DEFAULT_BAR_WIDTH));
    if (!console()) {
      logger.info(phase + ": " + ProgressFormatter.formatPlainLine(
          progress.progressPercent(), progress.completedChunks(), progress.totalChunks(),
          progress.elapsedMs(), progress.etaMs(), ProgressFormatter.DEFAULT_BAR_WIDTH));
    }
  }

  void complete(ExportGrid grid, ExportFiles files, long elapsedMs, long requested, int missing) {
    String message = String.format(Locale.ROOT,
        "Export complete in %.2fs (%d cells, %d missing chunks).",
        elapsedMs / 1000.0, grid.totalCells(), missing);
    send("§a§l" + message);
    info("JSON: §f" + path(files.json()));
    if (files.preview() != null) {
      info("PNG: §f" + path(files.preview()));
    }
    if (!console()) {
      logger.info(message + " chunks=" + grid.totalChunks() + " requested=" + requested
          + " json=" + path(files.json())
          + (files.preview() == null ? "" : " png=" + path(files.preview())));
    }
  }

  String path(File file) {
    return LogPaths.format(file, plugin.getServer().getWorldContainer());
  }

  String initiatorName() {
    Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
    return player == null ? sender.getName() : player.getName();
  }

  boolean initiatorOnline() {
    Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
    return player != null && player.isOnline();
  }

  private boolean console() {
    return playerId == null
        && (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender);
  }

  private void send(String message) {
    CommandSender recipient = playerId == null ? sender : Bukkit.getPlayer(playerId);
    if (recipient != null && (!(recipient instanceof Player player) || player.isOnline())) {
      recipient.sendMessage(PREFIX + message);
    }
  }
}
