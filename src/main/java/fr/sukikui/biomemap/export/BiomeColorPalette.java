package fr.sukikui.biomemap.export;

import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Color palette for vanilla biome ids (Minecraft 26.1.2) used by preview rendering.
 */
public final class BiomeColorPalette {

  private static final Color UNKNOWN_COLOR = new Color(255, 0, 255);
  private static final Map<String, Color> COLORS = new HashMap<>();

  static {
    register("minecraft:badlands", 216, 118, 73);
    register("minecraft:bamboo_jungle", 72, 122, 54);
    register("minecraft:basalt_deltas", 80, 73, 78);
    register("minecraft:beach", 250, 240, 192);
    register("minecraft:birch_forest", 120, 162, 83);
    register("minecraft:cherry_grove", 242, 176, 196);
    register("minecraft:cold_ocean", 61, 87, 214);
    register("minecraft:crimson_forest", 146, 63, 95);
    register("minecraft:dark_forest", 65, 95, 55);
    register("minecraft:deep_cold_ocean", 49, 68, 171);
    register("minecraft:deep_dark", 35, 44, 53);
    register("minecraft:deep_frozen_ocean", 51, 62, 131);
    register("minecraft:deep_lukewarm_ocean", 57, 89, 155);
    register("minecraft:deep_ocean", 46, 73, 141);
    register("minecraft:deep_warm_ocean", 53, 137, 173);
    register("minecraft:desert", 250, 148, 24);
    register("minecraft:dripstone_caves", 137, 126, 115);
    register("minecraft:end_barrens", 163, 163, 100);
    register("minecraft:end_highlands", 181, 181, 105);
    register("minecraft:end_midlands", 172, 172, 101);
    register("minecraft:eroded_badlands", 255, 109, 76);
    register("minecraft:flower_forest", 134, 190, 108);
    register("minecraft:forest", 92, 140, 57);
    register("minecraft:frozen_ocean", 137, 177, 255);
    register("minecraft:frozen_peaks", 197, 211, 218);
    register("minecraft:frozen_river", 167, 188, 255);
    register("minecraft:grove", 161, 183, 150);
    register("minecraft:ice_spikes", 181, 200, 201);
    register("minecraft:jagged_peaks", 149, 163, 166);
    register("minecraft:jungle", 83, 123, 50);
    register("minecraft:lukewarm_ocean", 69, 118, 196);
    register("minecraft:lush_caves", 79, 153, 89);
    register("minecraft:mangrove_swamp", 81, 111, 53);
    register("minecraft:meadow", 145, 181, 114);
    register("minecraft:mushroom_fields", 141, 88, 139);
    register("minecraft:nether_wastes", 87, 37, 38);
    register("minecraft:ocean", 48, 80, 180);
    register("minecraft:old_growth_birch_forest", 99, 143, 81);
    register("minecraft:old_growth_pine_taiga", 89, 122, 84);
    register("minecraft:old_growth_spruce_taiga", 87, 112, 78);
    register("minecraft:pale_garden", 174, 181, 163);
    register("minecraft:plains", 141, 179, 96);
    register("minecraft:river", 86, 120, 220);
    register("minecraft:savanna", 189, 178, 95);
    register("minecraft:savanna_plateau", 167, 157, 100);
    register("minecraft:small_end_islands", 160, 160, 102);
    register("minecraft:snowy_beach", 243, 249, 255);
    register("minecraft:snowy_plains", 247, 254, 255);
    register("minecraft:snowy_slopes", 228, 239, 245);
    register("minecraft:snowy_taiga", 167, 197, 167);
    register("minecraft:soul_sand_valley", 84, 74, 63);
    register("minecraft:sparse_jungle", 100, 138, 71);
    register("minecraft:stony_peaks", 149, 154, 160);
    register("minecraft:stony_shore", 156, 156, 156);
    register("minecraft:sunflower_plains", 169, 200, 102);
    register("minecraft:swamp", 107, 142, 57);
    register("minecraft:taiga", 90, 125, 81);
    register("minecraft:the_end", 177, 171, 112);
    register("minecraft:the_void", 0, 0, 0);
    register("minecraft:warm_ocean", 67, 182, 219);
    register("minecraft:warped_forest", 67, 143, 145);
    register("minecraft:windswept_forest", 103, 121, 94);
    register("minecraft:windswept_gravelly_hills", 136, 136, 136);
    register("minecraft:windswept_hills", 117, 130, 103);
    register("minecraft:windswept_savanna", 181, 167, 111);
    register("minecraft:wooded_badlands", 176, 108, 79);
    register("minecraft:unknown", 255, 0, 255);
  }

  private BiomeColorPalette() {
  }

  /**
   * Returns a stable color for the biome id.
   */
  public static Color colorFor(String biomeId) {
    if (biomeId == null || biomeId.isBlank()) {
      return UNKNOWN_COLOR;
    }
    String key = biomeId.toLowerCase(Locale.ROOT);
    Color known = COLORS.get(key);
    return known != null ? known : fallbackColor(key);
  }

  private static void register(String biomeId, int red, int green, int blue) {
    COLORS.put(biomeId, new Color(red, green, blue));
  }

  private static Color fallbackColor(String biomeId) {
    int hash = biomeId.hashCode();
    int red = 70 + (hash & 0x7F);
    int green = 70 + ((hash >> 8) & 0x7F);
    int blue = 70 + ((hash >> 16) & 0x7F);
    return new Color(red, green, blue);
  }
}
