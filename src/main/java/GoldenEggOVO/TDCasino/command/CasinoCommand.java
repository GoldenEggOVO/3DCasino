package GoldenEggOVO.TDCasino.command;

import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.Core.platform.PlatformMessages;
import GoldenEggOVO.TDCasino.Core.player.GamePermissions;
import GoldenEggOVO.TDCasino.Core.text.Language;
import GoldenEggOVO.TDCasino.text.CasinoMessages;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CasinoCommand implements Listener, CommandExecutor, TabCompleter {
    private final TDCasinoPlugin plugin;
    public CasinoCommand(TDCasinoPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        synchronized (plugin) {
            return execute(sender, args);
        }
    }

    private boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            PlatformMessages.send(sender, CasinoMessages.component("command.player-only"));
            return true;
        }
        if (!plugin.allowed(player)) {
            PlatformMessages.send(player, CasinoMessages.component("machine.denied"));
            return true;
        }
        if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("menu")) plugin.menus().main(player);
        else if (args.length == 1 && args[0].equalsIgnoreCase("settings")) plugin.menus().preferences(player);
        else if (args.length == 1 && args[0].equalsIgnoreCase("reload-pack")) {
            if (GamePermissions.admin(player)) plugin.pack().reload(player);
            else PlatformMessages.send(player, CasinoMessages.component("machine.denied"));
        } else if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!GamePermissions.admin(player)) PlatformMessages.send(player, CasinoMessages.component("machine.denied"));
            else {
                plugin.reloadConfig();
                if (Language.reload(plugin.getDataFolder().toPath().resolve("languages"), plugin.getConfig().getString("language", "en_US"), plugin.getLogger()::warning)) {
                    plugin.menus().closeOpenMenus();
                    PlatformMessages.send(player, CasinoMessages.component("command.reloaded"));
                } else PlatformMessages.send(player, CasinoMessages.component("command.reload-failed"));
            }
        } else plugin.casino().machines().command(player, args);
        return true;
    }

    @EventHandler
    public void commandSuggestions(PlayerCommandSendEvent event) {
        if (event.getCommands().contains("3dcasino")) event.getCommands().remove("3dcasino:3dcasino");
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        synchronized (plugin) {
            return complete(sender, args);
        }
    }

    private List<String> complete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || !plugin.allowed(player)) return List.of();
        if (args.length > 1) return plugin.casino().machines().complete(player, args);
        var choices = new ArrayList<>(plugin.casino().machines().complete(player, args));
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        var commands = new ArrayList<>(List.of("menu", "settings"));
        if (GamePermissions.admin(player)) commands.addAll(List.of("reload", "reload-pack"));
        commands.stream().filter(value -> value.startsWith(prefix)).forEach(choices::add);
        return List.copyOf(choices);
    }
}
