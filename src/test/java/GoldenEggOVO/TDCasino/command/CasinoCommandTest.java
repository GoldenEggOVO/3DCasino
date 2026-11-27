package GoldenEggOVO.TDCasino.command;

import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.CasinoModule;
import GoldenEggOVO.TDCasino.machine.MachineManager;
import GoldenEggOVO.TDCasino.menu.CasinoMenus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CasinoCommandTest {
    @Test void emptyCommandOpensTheStandaloneCatalog() {
        var plugin = mock(TDCasinoPlugin.class);
        var player = mock(Player.class);
        var menus = mock(CasinoMenus.class);
        when(plugin.allowed(player)).thenReturn(true);
        when(plugin.menus()).thenReturn(menus);
        assertTrue(new CasinoCommand(plugin).onCommand(player, mock(Command.class), "3dcasino", new String[0]));
        verify(menus).main(player);
    }

    @Test void machineArgumentsAndSuggestionsHaveNoCasinoPrefix() {
        var plugin = mock(TDCasinoPlugin.class);
        var player = mock(Player.class);
        var module = mock(CasinoModule.class);
        var machines = mock(MachineManager.class);
        when(plugin.allowed(player)).thenReturn(true);
        when(plugin.casino()).thenReturn(module);
        when(module.machines()).thenReturn(machines);
        var args = new String[] {"create", "slots"};
        when(machines.complete(player, args)).thenReturn(List.of("slots"));
        var command = new CasinoCommand(plugin);
        command.onCommand(player, mock(Command.class), "3dcasino", args);
        verify(machines).command(player, args);
        assertEquals(List.of("slots"), command.onTabComplete(player, mock(Command.class), "3dcasino", args));
    }

    @Test void unauthorizedPlayersCannotReachMachinesOrMenus() {
        var plugin = mock(TDCasinoPlugin.class);
        var player = mock(Player.class);
        var command = new CasinoCommand(plugin);
        command.onCommand(player, mock(Command.class), "3dcasino", new String[0]);
        verify(plugin, never()).menus();
        verify(plugin, never()).casino();
        assertEquals(List.of(), command.onTabComplete(player, mock(Command.class), "3dcasino", new String[] {""}));
    }

    @Test void rootSuggestionsHideOnlyTheDuplicateOwnedNamespace() {
        var choices = new java.util.LinkedHashSet<>(List.of("3dcasino", "3dcasino:3dcasino", "3dgames", "other:3dcasino"));
        new CasinoCommand(mock(TDCasinoPlugin.class)).commandSuggestions(
                new org.bukkit.event.player.PlayerCommandSendEvent(mock(Player.class), choices));
        assertEquals(new java.util.LinkedHashSet<>(List.of("3dcasino", "3dgames", "other:3dcasino")), choices);
    }

    @Test void aNamespaceOnlySuggestionRemainsReachable() {
        var choices = new java.util.LinkedHashSet<>(List.of("3dcasino:3dcasino"));
        new CasinoCommand(mock(TDCasinoPlugin.class)).commandSuggestions(
                new org.bukkit.event.player.PlayerCommandSendEvent(mock(Player.class), choices));
        assertEquals(java.util.Set.of("3dcasino:3dcasino"), choices);
    }

    @Test void consoleCannotOpenAPlayerDialog() {
        var plugin = mock(TDCasinoPlugin.class);
        assertTrue(new CasinoCommand(plugin).onCommand(mock(CommandSender.class), mock(Command.class), "3dcasino", new String[0]));
        verify(plugin, never()).menus();
    }

    @Test void onlySuccessfulLanguageReloadClosesOpenMenus(@org.junit.jupiter.api.io.TempDir java.nio.file.Path data) throws Exception {
        var plugin = mock(TDCasinoPlugin.class);
        var admin = mock(Player.class);
        var menus = mock(CasinoMenus.class);
        when(plugin.allowed(admin)).thenReturn(true);
        when(admin.hasPermission("3dcasino.admin")).thenReturn(true);
        when(plugin.getDataFolder()).thenReturn(data.toFile());
        when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(plugin.menus()).thenReturn(menus);
        var command = new CasinoCommand(plugin);
        command.onCommand(admin, mock(Command.class), "3dcasino", new String[]{"reload"});
        verify(menus).closeOpenMenus();

        java.nio.file.Files.writeString(data.resolve("languages/en_US.yml"), "\"casino.menu.title\": 'Invalid {unknown}'");
        command.onCommand(admin, mock(Command.class), "3dcasino", new String[]{"reload"});
        verifyNoMoreInteractions(menus);
    }
}
