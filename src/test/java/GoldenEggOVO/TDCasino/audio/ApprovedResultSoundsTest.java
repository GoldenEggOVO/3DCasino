package GoldenEggOVO.TDCasino.audio;

import GoldenEggOVO.TDCasino.machine.MachineResultState;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApprovedResultSoundsTest {
    @Test void aResultClearedBeforeTheListenersRegionTicksIsSilent() {
        var listener = mock(Player.class);
        var origin = new Location(mock(World.class), 0, 64, 0);
        var plugin = SoundListeners.install(mock(GoldenEggOVO.TDCasino.TDCasinoPlugin.class), p -> true, listener);
        var pending = new java.util.ArrayList<Runnable>();
        var scheduler = plugin.scheduler();
        doAnswer(call -> { pending.add(call.getArgument(1)); return null; })
                .when(scheduler).execute(eq(listener), any(Runnable.class));
        var sounds = new MachineSounds(plugin, origin);
        sounds.result(new MachineResultState.Result(100, 200), 10);
        sounds.clear();
        pending.forEach(Runnable::run);
        verify(listener, never()).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @Test void anOlderMachineCannotStopAnotherMachinesLatestResult() {
        var world = mock(World.class);
        var packed = mock(Player.class);
        var origin = new Location(world, 0, 64, 0);
        var plugin = SoundListeners.install(mock(GoldenEggOVO.TDCasino.TDCasinoPlugin.class), p -> true, packed);
        var a = new MachineSounds(plugin, origin);
        var b = new MachineSounds(plugin, origin.clone().add(4, 0, 0));
        a.result(new MachineResultState.Result(100, 200), 10);
        b.result(new MachineResultState.Result(100, 200), 10);
        clearInvocations(packed);
        a.clear();
        verifyNoInteractions(packed);
        b.clear();
        verify(packed).stopSound("3dcasino:result.win", SoundCategory.BLOCKS);
    }

    @Test void clearingStopsAnAlreadySentPackedComposition() {
        var world = mock(World.class);
        var packed = mock(Player.class);
        var origin = new Location(world, 0, 64, 0);
        var plugin = SoundListeners.install(mock(GoldenEggOVO.TDCasino.TDCasinoPlugin.class), p -> true, packed);
        var sounds = new MachineSounds(plugin, origin);
        sounds.result(new MachineResultState.Result(100, 200), 10);
        sounds.clear();
        verify(packed).stopSound("3dcasino:result.win", SoundCategory.BLOCKS);
        clearInvocations(packed);
        for (int tick = 10; tick < 25; tick++) sounds.tick(tick);
        verifyNoInteractions(packed);
    }

    @Test void packedWinnerGetsOneCompositionWhileNativeListenerKeepsThreeNotes() {
        var world = mock(World.class);
        var packed = mock(Player.class);
        var nativePlayer = mock(Player.class);
        var origin = new Location(world, 0, 64, 0);
        var plugin = SoundListeners.install(mock(GoldenEggOVO.TDCasino.TDCasinoPlugin.class), p -> p == packed, packed, nativePlayer);
        var sounds = new MachineSounds(plugin, origin);
        sounds.result(new MachineResultState.Result(100, 200), 10);
        for (int tick = 10; tick < 25; tick++) sounds.tick(tick);
        verify(packed).playSound(origin, "3dcasino:result.win", SoundCategory.BLOCKS, .25f, 1f);
        verify(packed, never()).playSound(any(Location.class), any(Sound.class), any(SoundCategory.class), anyFloat(), anyFloat());
        verify(nativePlayer, times(3)).playSound(eq(origin), eq(Sound.BLOCK_NOTE_BLOCK_CHIME), eq(SoundCategory.BLOCKS), eq(.25f), anyFloat());
        verify(nativePlayer, never()).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());
    }
}
