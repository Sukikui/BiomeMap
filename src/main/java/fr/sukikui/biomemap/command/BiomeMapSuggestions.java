package fr.sukikui.biomemap.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.World;

/** Builds suggestions after the command handler has verified administrator access. */
final class BiomeMapSuggestions {

  private BiomeMapSuggestions() {
  }

  static List<String> complete(String[] args) {
    if (args.length == 1) {
      String prefix = args[0].toLowerCase(Locale.ROOT);
      List<String> suggestions = new ArrayList<>();
      if ("status".startsWith(prefix)) {
        suggestions.add("status");
      }
      if ("stop".startsWith(prefix)) {
        suggestions.add("stop");
      }
      for (World world : Bukkit.getWorlds()) {
        String name = world.getName();
        if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
          suggestions.add(name);
        }
      }
      return suggestions;
    } else if (args.length == 2 && "status".equalsIgnoreCase(args[0])) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      if ("map".startsWith(prefix)) {
        return List.of("map");
      }
      return Collections.emptyList();
    } else if (args.length >= 6) {
      return List.of("4", "8", "16", "32", "64", "128", "256", "preview");
    }
    return Collections.emptyList();
  }
}
