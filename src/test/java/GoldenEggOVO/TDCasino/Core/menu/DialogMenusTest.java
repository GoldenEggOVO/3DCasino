package GoldenEggOVO.TDCasino.Core.menu;

import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DialogMenusTest {
    @Test void casinoSettingsUseTheHeaderInsteadOfTheGameGrid() {
        MockBukkit.mock();
        try (var bodies = mockStatic(io.papermc.paper.registry.data.dialog.body.DialogBody.class);
             var bases = mockStatic(DialogBase.class);
             var actions = mockStatic(DialogAction.class);
             var buttons = mockStatic(ActionButton.class);
             var types = mockStatic(DialogType.class);
             var dialogs = mockStatic(Dialog.class)) {
            var captions = new java.util.ArrayList<Component>();
            bodies.when(() -> io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(any(), anyInt()))
                    .thenAnswer(call -> {
                        captions.add(call.getArgument(0));
                        return mock(io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody.class);
                    });
            var plugin = mock(TDCasinoPlugin.class);
            when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            var player = mock(Player.class);
            var world = mock(World.class);
            when(player.getWorld()).thenReturn(world);
            when(world.getUID()).thenReturn(UUID.randomUUID());
            var settings = new MenuView.Button(Component.text("Settings"), null, 174, "preferences");
            var game = new MenuView.Button(Component.text("Slots"), null, 174, "game:slots");
            var exit = new MenuView.Button(Component.text("Close"), null, 350, "close");
            DialogMenus.open(plugin, player, new MenuView(Component.text("Casino"), List.of(), List.of(),
                    List.of(game, settings), exit, 2, "casino"), (action, values) -> {});
            assertEquals(1, captions.size(), "Settings must appear above the game grid");
            assertEquals("[Settings]", GoldenEggOVO.TDCasino.Core.text.MessageText.plain(captions.getFirst()));
            assertNotNull(captions.getFirst().clickEvent());
            buttons.verify(() -> ActionButton.create(any(), any(), anyInt(), any()), times(2));
        } finally { MockBukkit.unmock(); }
    }

    @Test
    void resourcePackDoesNotChangeNativeDialogLayout() {
        MockBukkit.mock();
        try (var bodies = mockStatic(io.papermc.paper.registry.data.dialog.body.DialogBody.class);
             var bases = mockStatic(DialogBase.class);
             var actions = mockStatic(DialogAction.class);
             var buttons = mockStatic(ActionButton.class);
             var types = mockStatic(DialogType.class);
             var dialogs = mockStatic(Dialog.class)) {
            bodies.when(() -> io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(any(), anyInt()))
                    .thenReturn(mock(io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody.class));
            var nativeTitle = new AtomicReference<Component>();
            bases.when(() -> DialogBase.create(any(), isNull(), anyBoolean(), anyBoolean(),
                    any(), anyList(), anyList())).thenAnswer(call -> {
                        nativeTitle.set(call.getArgument(0));
                        return null;
                    });
            var plugin = mock(TDCasinoPlugin.class);
            var pack = mock(GoldenEggOVO.TDCasino.Core.pack.ResourcePackService.class);
            var player = mock(Player.class);
            var world = mock(World.class);
            when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            when(plugin.pack()).thenReturn(pack);
            when(pack.packed(player)).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(world.getUID()).thenReturn(UUID.randomUUID());
            var title = Component.text("Casino");
            var exit = new MenuView.Button(Component.text("Close"), null, 170, "close");
            DialogMenus.open(plugin, player, new MenuView(title, List.of(), List.of(), List.of(), exit, 1),
                    (action, values) -> {});
            assertEquals(title, nativeTitle.get());
        } finally {
            MockBukkit.unmock();
        }
    }
    @Test
    void disabledMenusDoNotOpenADialog() {
        var plugin = mock(TDCasinoPlugin.class);
        var config = new YamlConfiguration();
        config.set("menu-enabled", false);
        when(plugin.getConfig()).thenReturn(config);
        var player = mock(Player.class);
        assertDoesNotThrow(() -> DialogMenus.open(plugin, player, null, (action, values) -> {}));
        verify(player, never()).showDialog(any());
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    void exitButtonsCloseTheCurrentScreenBeforeServerNavigation() {
        MockBukkit.mock();
        try (var bases = mockStatic(DialogBase.class);
             var actions = mockStatic(DialogAction.class);
             var buttons = mockStatic(ActionButton.class);
             var types = mockStatic(DialogType.class);
             var dialogs = mockStatic(Dialog.class)) {
            var afterAction = new AtomicReference<DialogBase.DialogAfterAction>();
            bases.when(() -> DialogBase.create(any(), isNull(), anyBoolean(), anyBoolean(),
                    any(), anyList(), anyList())).thenAnswer(call -> {
                        afterAction.set(call.getArgument(4));
                        return null;
                    });
            var plugin = mock(TDCasinoPlugin.class);
            when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            var player = mock(Player.class);
            var world = mock(World.class);
            when(player.getWorld()).thenReturn(world);
            when(world.getUID()).thenReturn(UUID.randomUUID());
            var exit = new MenuView.Button(Component.text("Back"), null, 170, "back");
            DialogMenus.open(plugin, player,
                    new MenuView(Component.text("Casino"), List.of(), List.of(), List.of(), exit, 1),
                    (action, values) -> {});
            assertEquals(DialogBase.DialogAfterAction.CLOSE, afterAction.get());
        } finally {
            MockBukkit.unmock();
        }
    }
}
