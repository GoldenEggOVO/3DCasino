package GoldenEggOVO.TDCasino.machine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import GoldenEggOVO.TDCasino.CasinoModule;
import GoldenEggOVO.TDCasino.game.keno.KenoRound;
import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MachinePreferencesTest {
    @TempDir Path folder;

    @Test void disablingEconomyResetsStoredPositiveAmountAndKeepsOtherPreferences() throws Exception {
        var plugin = mock(TDCasinoPlugin.class);
        var module = mock(CasinoModule.class);
        when(plugin.casino()).thenReturn(module);
        var store = new PlayerMachineProfileStore(folder);
        var manager = manager(plugin, store);
        var player = UUID.randomUUID();
        var owner = UUID.randomUUID();
        var original = new KenoRound(new Random(1));
        original.setStake(125);
        original.action("select:7");
        var resultState = new MachineResultState();
        resultState.launch(1, 125);
        resultState.reveal(1, 250);
        store.save(player, owner, "keno", PlayerMachineProfileStore.Profile.capture(original, resultState));
        var restored = new KenoRound(new Random(2));
        var restoredFeedback = new MachineResultState();
        manager.restorePlayerData(owner, "keno", player, restored, restoredFeedback);
        assertEquals(0, restored.stake());
        assertEquals(java.util.Set.of(7), restored.selected());
        assertEquals(125, restoredFeedback.totalNet());
        when(module.economyEnabled()).thenReturn(true);
        manager.resetInvalidConfiguredStake(restored);
        assertEquals(0, restored.stake());
    }

    @Test void changedLimitsOrCurrencyPrecisionResetOnlyTheAmount() throws Exception {
        var plugin = mock(TDCasinoPlugin.class);
        var module = mock(CasinoModule.class);
        when(plugin.casino()).thenReturn(module);
        when(module.economyEnabled()).thenReturn(true);
        doThrow(new IllegalArgumentException("Amount no longer valid")).when(module).validateStakeCents(125);
        var manager = manager(plugin, new PlayerMachineProfileStore(folder));
        var round = new KenoRound(new Random(1));
        round.setStake(125);
        round.action("select:9");
        manager.resetInvalidConfiguredStake(round);
        assertEquals(0, round.stake());
        assertEquals(java.util.Set.of(9), round.selected());
        verify(module).validateStakeCents(125);
    }

    private static MachineManager manager(TDCasinoPlugin plugin, PlayerMachineProfileStore store) throws Exception {
        var manager = mock(MachineManager.class, CALLS_REAL_METHODS);
        for (var entry : java.util.Map.of("plugin", plugin, "playerProfiles", store).entrySet()) {
            var field = MachineManager.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(manager, entry.getValue());
        }
        return manager;
    }
}
