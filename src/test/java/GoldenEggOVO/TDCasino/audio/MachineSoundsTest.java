package GoldenEggOVO.TDCasino.audio;

import GoldenEggOVO.TDCasino.CasinoModule;
import GoldenEggOVO.TDCasino.Core.player.PlayerSessions;
import GoldenEggOVO.TDCasino.Core.render.ResourcePackState;
import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.economy.MachinePaymentLedger;
import GoldenEggOVO.TDCasino.economy.RoundFunding;
import GoldenEggOVO.TDCasino.game.plinko.PlinkoMachine;
import GoldenEggOVO.TDCasino.game.slots.SlotsRound;
import GoldenEggOVO.TDCasino.machine.GameMachine;
import GoldenEggOVO.TDCasino.machine.MachineManager;
import GoldenEggOVO.TDCasino.machine.MachineResultState;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MachineSoundsTest {
    private static final class TestMachine extends GameMachine<SlotsRound> {
        TestMachine(MachineManager manager, Location origin) {
            super(manager, UUID.randomUUID(), origin, MachineDefinition.builtin("slots"),
                    () -> new SlotsRound(new Random(1)));
        }
        protected void buildGame() {}
        protected void action(String action) {}
        protected boolean available(String action) { return false; }
        protected void refresh() {}
        void deal() { sound(Sound.ITEM_BOOK_PAGE_TURN, .2f, 1.2f); }
        void tone() { sound(Sound.BLOCK_NOTE_BLOCK_PLING, .22f, 1.35f); }
    }

    private static final class Fixture {
        final Player packed = mock(Player.class), nativePlayer = mock(Player.class);
        final World world = mock(World.class);
        final TDCasinoPlugin plugin = mock(TDCasinoPlugin.class);
        final MachineManager manager = mock(MachineManager.class);
        final ResourcePackState pack = mock(ResourcePackState.class);
        final Location origin = new Location(world, 0, 64, 0);

        Fixture() throws Exception {
            var casino = mock(CasinoModule.class);
            var payments = mock(MachinePaymentLedger.class);
            var pluginGetter = MachineManager.class.getDeclaredMethod("plugin");
            pluginGetter.setAccessible(true);
            when(pluginGetter.invoke(manager)).thenReturn(plugin);
            when(plugin.sessions()).thenReturn(new PlayerSessions());
            when(plugin.casino()).thenReturn(casino);
            when(plugin.resourcePackState()).thenReturn(pack);
            when(casino.payments()).thenReturn(payments);
            when(payments.bind(any(), anyString())).thenReturn(RoundFunding.none());
            SoundListeners.install(plugin, pack::packed, packed, nativePlayer);
            when(pack.packed(packed)).thenReturn(true);
        }
    }

    @Test void eachListenerUsesTheDealSampleOnlyWhileItsOwnPackIsReady() throws Exception {
        var f = new Fixture();
        var machine = new TestMachine(f.manager, f.origin);
        machine.deal();
        verify(f.packed).playSound(f.origin, "3dcasino:cards.deal", SoundCategory.BLOCKS, .2f, 1f);
        verify(f.packed, never()).playSound(any(Location.class), any(Sound.class), any(SoundCategory.class), anyFloat(), anyFloat());
        verify(f.nativePlayer).playSound(f.origin, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, .2f, 1.2f);
        when(f.pack.packed(f.packed)).thenReturn(false);
        clearInvocations(f.packed);
        machine.deal();
        verify(f.packed).playSound(f.origin, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, .2f, 1.2f);
        verify(f.packed, never()).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @Test void nonCardTonesKeepTheirNativePitchForEveryListener() throws Exception {
        var f = new Fixture();
        new TestMachine(f.manager, f.origin).tone();
        for (var listener : List.of(f.packed, f.nativePlayer)) {
            verify(listener).playSound(f.origin, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.BLOCKS, .22f, 1.35f);
            verify(listener, never()).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());
        }
    }

    @Test void plinkoOutcomesAreSingleCuesWithoutAnOldWinTailAfterALoss() throws Exception {
        var f = new Fixture();
        var machine = new PlinkoMachine(f.manager, UUID.randomUUID(), f.origin, MachineDefinition.builtin("plinko"));
        reveal(machine, 1, 100, 200, 100);
        queue(machine).tick(100);
        queue(machine).tick(103);
        reveal(machine, 2, 100, 0, 104);
        for (int tick = 104; tick <= 115; tick++) queue(machine).tick(tick);
        verify(f.nativePlayer, times(2)).playSound(any(Location.class), any(Sound.class), any(SoundCategory.class), anyFloat(), anyFloat());
        verify(f.nativePlayer, never()).playSound(any(Location.class), eq(Sound.BLOCK_NOTE_BLOCK_CHIME), any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @Test void ordinaryMachinesRetainTheirThreeNoteWinAndClearStopsTheTail() throws Exception {
        var f = new Fixture();
        var machine = new TestMachine(f.manager, f.origin);
        reveal(machine, 1, 100, 200, 100);
        for (int tick = 100; tick <= 110; tick++) queue(machine).tick(tick);
        verify(f.nativePlayer, times(3)).playSound(any(Location.class), eq(Sound.BLOCK_NOTE_BLOCK_CHIME), eq(SoundCategory.BLOCKS), eq(.25f), anyFloat());
        clearInvocations(f.nativePlayer);
        reveal(machine, 2, 100, 500, 120);
        queue(machine).tick(120);
        queue(machine).clear();
        for (int tick = 121; tick <= 135; tick++) queue(machine).tick(tick);
        verify(f.nativePlayer, times(1)).playSound(any(Location.class), eq(Sound.BLOCK_NOTE_BLOCK_CHIME), eq(SoundCategory.BLOCKS), eq(.35f), anyFloat());
    }

    @Test void physicalMachineActionsRoutePerViewerWithoutReplacingResultNotes() throws Exception {
        var f = new Fixture();
        var sounds = new MachineSounds(f.plugin, f.origin);
        var expected = java.util.Map.of(
                Sound.BLOCK_STONE_BUTTON_CLICK_ON, "machine.button",
                Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, "ball.land",
                Sound.BLOCK_GLASS_HIT, "ball.contact",
                Sound.BLOCK_WOOD_HIT, "wheel.tick",
                Sound.BLOCK_PISTON_CONTRACT, "machine.latch");
        for (var entry : expected.entrySet()) {
            clearInvocations(f.packed, f.nativePlayer);
            sounds.play(entry.getKey(), .2f, 1.2f);
            verify(f.packed).playSound(f.origin, "3dcasino:" + entry.getValue(), SoundCategory.BLOCKS, .2f, 1f);
            verify(f.nativePlayer).playSound(f.origin, entry.getKey(), SoundCategory.BLOCKS, .2f, 1.2f);
        }
        clearInvocations(f.packed);
        when(f.pack.packed(f.packed)).thenReturn(false);
        sounds.play(Sound.BLOCK_GLASS_HIT, .2f, 1.2f);
        verify(f.packed).playSound(f.origin, Sound.BLOCK_GLASS_HIT, SoundCategory.BLOCKS, .2f, 1.2f);
    }

    private static void reveal(GameMachine<?> machine, int id, long stake, long returned, int age) throws Exception {
        var ageField = GameMachine.class.getDeclaredField("age"); ageField.setAccessible(true); ageField.setInt(machine, age);
        var resultField = GameMachine.class.getDeclaredField("resultState"); resultField.setAccessible(true);
        ((MachineResultState) resultField.get(machine)).launch(id, stake);
        var settle = GameMachine.class.getDeclaredMethod("settleFeedback", Object.class, long.class);
        settle.setAccessible(true); settle.invoke(machine, id, returned);
    }

    private static MachineSounds queue(GameMachine<?> machine) throws Exception {
        var field = GameMachine.class.getDeclaredField("sounds"); field.setAccessible(true);
        return (MachineSounds) field.get(machine);
    }
}
