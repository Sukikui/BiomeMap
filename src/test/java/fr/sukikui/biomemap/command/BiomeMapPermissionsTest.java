package fr.sukikui.biomemap.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.sukikui.biomemap.export.ExportSettings;
import fr.sukikui.biomemap.export.MissingChunkPolicy;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BiomeMapPermissionsTest {

  private final Command command = new Command("biomemap") {
    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
      throw new AssertionError("Tests must use the registered handler");
    }
  };

  private final BiomeMapCommand handler = new BiomeMapCommand(
      null, null, Logger.getLogger("BiomeMapPermissionsTest"), 16,
      new ExportSettings(1, 4, MissingChunkPolicy.GENERATE));

  static Stream<List<String>> commandArguments() {
    return Stream.of(
        List.of(),
        List.of("status"),
        List.of("status", "map"),
        List.of("status", "--map"),
        List.of("stop"),
        List.of("world", "0", "0", "16", "16"),
        List.of("world", "0", "0", "16", "16", "4", "preview"));
  }

  @ParameterizedTest
  @MethodSource("commandArguments")
  void deniesEveryCommandBeforeAnyWorldOrExportAccess(List<String> args) {
    List<String> messages = new ArrayList<>();
    CommandSender sender = sender(false, messages);

    assertTrue(handler.onCommand(sender, command, "biomemap", args.toArray(String[]::new)));

    assertEquals(1, messages.size());
    assertTrue(messages.getFirst().contains("You do not have permission"));
  }

  @ParameterizedTest
  @MethodSource("commandArguments")
  void hidesAllSuggestionsFromUnauthorizedSenders(List<String> args) {
    List<String> messages = new ArrayList<>();

    assertTrue(Objects.requireNonNull(handler.onTabComplete(
        sender(false, messages), command, "biomemap", args.toArray(String[]::new))).isEmpty());
    assertTrue(messages.isEmpty());
  }

  @Test
  void allowsAuthorizedStatusAndStopCommands() {
    List<String> messages = new ArrayList<>();
    CommandSender sender = sender(true, messages);

    assertTrue(handler.onCommand(sender, command, "biomemap", new String[] {"status"}));
    assertTrue(handler.onCommand(sender, command, "biomemap", new String[] {"stop"}));

    assertEquals(2, messages.size());
    assertTrue(messages.stream().allMatch(message -> message.contains("No biome export")));
  }

  @Test
  void allowsAuthorizedHelpAndSuggestions() {
    List<String> messages = new ArrayList<>();
    CommandSender sender = sender(true, messages);

    assertTrue(handler.onCommand(sender, command, "biomemap", new String[0]));
    assertTrue(messages.getFirst().contains("Usage:"));
    assertEquals(List.of("map"), handler.onTabComplete(
        sender, command, "biomemap", new String[] {"status", "m"}));
    assertTrue(Objects.requireNonNull(handler.onTabComplete(sender, command, "biomemap",
        new String[] {"world", "0", "0", "16", "16", ""})).contains("preview"));
  }

  @Test
  void packagedCommandRequiresPermissionGrantedOnlyToOperatorsByDefault() throws Exception {
    try (InputStream input = Objects.requireNonNull(
        getClass().getResourceAsStream("/plugin.yml"))) {
      PluginDescriptionFile description = new PluginDescriptionFile(input);

      assertEquals(BiomeMapCommand.ADMIN_PERMISSION,
          description.getCommands().get("biomemap").get("permission"));
      assertEquals(PermissionDefault.OP, description.getPermissions().stream()
          .filter(permission -> permission.getName().equals(BiomeMapCommand.ADMIN_PERMISSION))
          .findFirst().orElseThrow().getDefault());
    }
  }

  private CommandSender sender(boolean allowed, List<String> messages) {
    return (CommandSender) Proxy.newProxyInstance(
        CommandSender.class.getClassLoader(), new Class<?>[] {CommandSender.class},
        (proxy, method, args) -> {
          if ("hasPermission".equals(method.getName())) {
            assertEquals(BiomeMapCommand.ADMIN_PERMISSION, args[0]);
            return allowed;
          }
          if ("sendMessage".equals(method.getName()) && args.length == 1
              && args[0] instanceof String message) {
            messages.add(message);
            return null;
          }
          throw new AssertionError("Unexpected sender access: " + method.getName());
        });
  }
}
