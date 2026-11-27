package GoldenEggOVO.TDCasino.menu;

import GoldenEggOVO.TDCasino.CasinoTestSupport;

import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.CasinoModule;
import GoldenEggOVO.TDCasino.machine.GameMachine;
import GoldenEggOVO.TDCasino.machine.MachineManager;
import GoldenEggOVO.TDCasino.Core.menu.MenuView;
import GoldenEggOVO.TDCasino.Core.platform.NativeDialogs;
import GoldenEggOVO.TDCasino.Core.platform.PlatformScheduler;
import GoldenEggOVO.TDCasino.Core.menu.DialogMenus;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CasinoMenusTest {
    @Test void subpagesHaveOneBackActionAndEscapeClosesEveryPage() {
        try (var f = new Fixture()) {
            var pack = mock(GoldenEggOVO.TDCasino.Core.pack.ResourcePackService.class);
            when(f.plugin.pack()).thenReturn(pack);
            f.menus.main(f.player);
            assertEquals("close", f.view.exit().action());
            f.callback.accept("preferences", Map.of());
            assertSubmenuNavigation(f.view);
            f.callback.accept("back", Map.of());
            f.callback.accept("game:slots", Map.of());
            assertSubmenuNavigation(f.view);
            when(f.module.canConfigureStake()).thenReturn(true);
            when(f.manager.canManage(f.player, f.machine)).thenReturn(true);
            f.menus.settings(f.player, f.machine);
            assertSubmenuNavigation(f.view);
            f.callback.accept("amount", Map.of());
            assertSubmenuNavigation(f.view);
            assertEquals(List.of("save", "back"), f.view.buttons().stream().map(MenuView.Button::action).toList());
            f.callback.accept("back", Map.of("amount", "invalid"));
            verify(f.machine, never()).setStake(any(), anyLong());
            f.callback.accept("remove", Map.of());
            assertSubmenuNavigation(f.view);
        }
    }

    private static void assertSubmenuNavigation(MenuView view) {
        assertEquals("close", view.exit().action());
        assertEquals(1, view.buttons().stream().filter(button -> button.action().equals("back")).count());
        assertEquals("back", view.buttons().getLast().action());
    }
    @Test void catalogDoesNotRepeatMachineInteractionInstructions() {
        try (var f = new Fixture()) {
            f.menus.main(f.player);
            assertTrue(f.view.body().isEmpty());
        }
    }

    @Test void catalogUsesRuleTooltipsAndKeepsSettingsBeforeASeparateClose() {
        try (var f = new Fixture()) {
            f.menus.main(f.player);
            assertEquals("close", f.view.exit().action());
            assertEquals("preferences", f.view.buttons().getLast().action());
            assertTrue(f.view.buttons().stream().filter(button -> button.action().startsWith("game:"))
                    .allMatch(button -> button.tooltip() != null));
            assertEquals(350, f.view.exit().width());
        }
    }

    @Test void settingsKeepNavigationAfterActionsAndMakeRemovalDestructive() {
        try (var f = new Fixture()) {
            when(f.module.canConfigureStake()).thenReturn(true);
            when(f.manager.canManage(f.player, f.machine)).thenReturn(true);
            f.menus.settings(f.player, f.machine);
            assertEquals(List.of("amount", "remove", "back"), f.view.buttons().stream().map(MenuView.Button::action).toList());
            assertTrue(f.view.buttons().stream().allMatch(button -> button.width() == 330));
            var remove = f.view.buttons().stream().filter(button -> button.action().equals("remove")).findFirst().orElseThrow();
            assertTrue(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(remove.text()).contains("§c"));
            f.callback.accept("back", Map.of());
            assertSubmenuNavigation(f.view);
        }
    }

    static final class Fixture implements AutoCloseable {
        final TDCasinoPlugin plugin = mock(TDCasinoPlugin.class);
        final CasinoModule module = mock(CasinoModule.class);
        final MachineManager manager = mock(MachineManager.class);
        final GameMachine<?> machine = mock(GameMachine.class);
        final Player player = mock(Player.class);
        final CasinoMenus menus;
        final org.mockito.MockedStatic<DialogMenus> renderer = mockStatic(DialogMenus.class);
        MenuView view;
        BiConsumer<String, Map<String, String>> callback;

        Fixture() {
            when(plugin.allowed(player)).thenReturn(true);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(module.machines()).thenReturn(manager);
            when(manager.contains(machine)).thenReturn(true);
            when(manager.canUse(player, machine)).thenReturn(true);
            when(machine.nearSettings(player)).thenReturn(true);
            when(machine.game()).thenReturn("slots");
            when(module.formatCents(anyLong())).thenAnswer(call -> Long.toString(call.getArgument(0)));
            renderer.when(() -> DialogMenus.open(eq(plugin), any(Player.class), any(), any())).thenAnswer(call -> {
                view = call.getArgument(2);
                callback = call.getArgument(3);
                return true;
            });
            menus = new CasinoMenus(plugin, module);
        }
        @Override public void close() { renderer.close(); }
    }

    @Test void catalogSettingsKeepTheResourcePackToggleOffTheCatalog() {
        try (var f = new Fixture()) {
            var pack = mock(GoldenEggOVO.TDCasino.Core.pack.ResourcePackService.class);
            when(f.plugin.pack()).thenReturn(pack);
            when(pack.hasToggle()).thenReturn(true);
            when(pack.button(f.player)).thenReturn(net.kyori.adventure.text.Component.text("Resource pack"));
            f.menus.main(f.player);
            assertFalse(f.view.buttons().stream().anyMatch(b -> b.action().equals("pack")));
            f.callback.accept("preferences", Map.of());
            assertEquals(List.of("pack", "back"), f.view.buttons().stream().map(MenuView.Button::action).toList());
            f.callback.accept("pack", Map.of());
            verify(pack).toggle(f.player);
            f.callback.accept("back", Map.of());
            assertEquals("preferences", f.view.buttons().getLast().action());
        }
    }

    @Test void backFromTheAmountEditorDiscardsInputAndRestoresSettings() {
        try (var f = new Fixture()) {
            when(f.module.canConfigureStake()).thenReturn(true);
            f.menus.settings(f.player, f.machine);
            f.callback.accept("amount", Map.of());
            assertSubmenuNavigation(f.view);
            f.callback.accept("back", Map.of("amount", "invalid"));
            assertSubmenuNavigation(f.view);
            verify(f.machine, never()).setStake(any(), anyLong());
        }
    }

    @Test void escapeDiscardsTheAmountAndEndsTheMenuSession() {
        try (var f = new Fixture()) {
            when(f.module.canConfigureStake()).thenReturn(true);
            f.menus.settings(f.player, f.machine);
            f.callback.accept("amount", Map.of());
            var editor = f.view;
            var click = f.callback;
            click.accept(editor.exit().action(), Map.of("amount", "12.34"));
            f.menus.refresh(f.player);
            click.accept("save", Map.of("amount", "12.34"));
            assertSame(editor, f.view);
            verify(f.machine, never()).setStake(any(), anyLong());
        }
    }

    @Test void returningFromSettingsConsumesThePageWithoutChangingTheMachine() {
        try (var f = new Fixture()) {
            when(f.module.canConfigureStake()).thenReturn(true);
            f.menus.settings(f.player, f.machine);
            var callback = f.callback;
            callback.accept("back", Map.of());
            var view = f.view;
            callback.accept("amount", Map.of());
            assertSame(view, f.view);
            verify(f.machine, never()).setStake(any(), anyLong());
        }
    }

    @Test void economyCapabilityControlsTheAmountButton() {
        try (var f = new Fixture()) {
            f.menus.settings(f.player, f.machine);
            assertEquals(List.of("back"), f.view.buttons().stream().map(MenuView.Button::action).toList());
            when(f.module.canConfigureStake()).thenReturn(true);
            f.menus.settings(f.player, f.machine);
            assertEquals(List.of("amount", "back"), f.view.buttons().stream().map(MenuView.Button::action).toList());
        }
    }

    @Test void amountIsPersonalAndEachPageCanBeConsumedOnlyOnce() {
        try (var f = new Fixture()) {
            when(f.module.canConfigureStake()).thenReturn(true);
            f.menus.settings(f.player, f.machine);
            f.callback.accept("amount", Map.of());
            var submit = f.callback;
            submit.accept("save", Map.of("amount", "12.34"));
            submit.accept("save", Map.of("amount", "99"));
            verify(f.module).validateStakeCents(1234);
            verify(f.machine).setStake(f.player, 1234);
            verify(f.machine, times(1)).setStake(any(), anyLong());
        }
    }

    @Test void replacementPagesAndRemovedMachinesInvalidateOldActions() {
        try (var f = new Fixture()) {
            when(f.module.canConfigureStake()).thenReturn(true);
            f.menus.settings(f.player, f.machine);
            f.callback.accept("amount", Map.of());
            var old = f.callback;
            f.menus.settings(f.player, f.machine);
            old.accept("save", Map.of("amount", "8"));
            verify(f.machine, never()).setStake(any(), anyLong());
            f.callback.accept("amount", Map.of());
            when(f.manager.contains(f.machine)).thenReturn(false);
            f.callback.accept("save", Map.of("amount", "8"));
            verify(f.machine, never()).setStake(any(), anyLong());
        }
    }

    @Test void reloadClosesEveryViewerAndImmediatelyRejectsOldAmountAndDeleteCallbacks() {
        try (var f = new Fixture(); var dialogs = mockStatic(NativeDialogs.class)) {
            var admin = mock(Player.class);
            var unrelated = mock(Player.class);
            when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
            when(f.plugin.allowed(admin)).thenReturn(true);
            when(admin.isOnline()).thenReturn(true);
            when(f.player.isOnline()).thenReturn(true);
            when(f.manager.canManage(admin, f.machine)).thenReturn(true);
            when(f.manager.canUse(admin, f.machine)).thenReturn(true);
            when(f.machine.nearSettings(admin)).thenReturn(true);
            when(f.module.canConfigureStake()).thenReturn(true);
            var pending = postponeCloses(f);
            f.menus.settings(f.player, f.machine);
            f.callback.accept("amount", Map.of());
            var amount = f.callback;
            f.menus.settings(admin, f.machine);
            f.callback.accept("remove", Map.of());
            var remove = f.callback;

            f.menus.closeOpenMenus();
            assertEquals(2, pending.size());
            amount.accept("save", Map.of("amount", "12.34"));
            remove.accept("confirm", Map.of());
            verify(f.machine, never()).setStake(any(), anyLong());
            verify(f.manager, never()).remove(any());
            pending.forEach(Runnable::run);
            dialogs.verify(() -> NativeDialogs.clear(f.plugin, f.player));
            dialogs.verify(() -> NativeDialogs.clear(f.plugin, admin));
            dialogs.verify(() -> NativeDialogs.clear(f.plugin, unrelated), never());
        }
    }

    @Test void delayedReloadCloseSparesNewPagesAndDisconnectedViewers() {
        try (var f = new Fixture(); var dialogs = mockStatic(NativeDialogs.class)) {
            var pending = postponeCloses(f);
            when(f.player.isOnline()).thenReturn(true);
            f.menus.main(f.player);
            f.menus.closeOpenMenus();
            f.menus.main(f.player);
            pending.removeFirst().run();
            dialogs.verifyNoInteractions();
            var newPage = f.view;
            f.callback.accept("game:slots", Map.of());
            assertNotSame(newPage, f.view);

            f.menus.closeOpenMenus();
            when(f.player.isOnline()).thenReturn(false);
            pending.removeFirst().run();
            dialogs.verifyNoInteractions();
            f.menus.closeOpenMenus();
            assertTrue(pending.isEmpty());
        }
    }

    private static List<Runnable> postponeCloses(Fixture f) {
        var scheduler = mock(PlatformScheduler.class);
        when(f.plugin.scheduler()).thenReturn(scheduler);
        var pending = new ArrayList<Runnable>();
        when(scheduler.execute(any(Player.class), any())).thenAnswer(call -> {
            pending.add(call.getArgument(1));
            return null;
        });
        return pending;
    }

    @Test void removalRequiresLiveManagementPermissionAndConfirmation() {
        try (var f = new Fixture()) {
            when(f.manager.canManage(f.player, f.machine)).thenReturn(true);
            f.menus.settings(f.player, f.machine);
            f.callback.accept("remove", Map.of());
            verify(f.manager, never()).remove(any());
            when(f.manager.canManage(f.player, f.machine)).thenReturn(false);
            f.callback.accept("confirm", Map.of());
            verify(f.manager, never()).remove(any());
        }
    }
}
