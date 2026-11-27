package GoldenEggOVO.TDCasino.audio;

import GoldenEggOVO.TDCasino.Core.platform.PlatformScheduler;
import GoldenEggOVO.TDCasino.Core.render.PlayerViews;
import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.function.Predicate;

import static org.mockito.Mockito.*;

public final class SoundListeners {
    private SoundListeners() {}

    public static TDCasinoPlugin install(TDCasinoPlugin plugin, Predicate<Player> packed, Player... players) {
        var views = mock(PlayerViews.class);
        var scheduler = mock(PlatformScheduler.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.scheduler()).thenReturn(scheduler);
        when(plugin.playerViews()).thenReturn(views);
        when(views.nearby(any(Location.class), eq(9.0))).thenAnswer(call -> {
            Location origin = call.getArgument(0);
            return Arrays.stream(players).map(player -> new PlayerViews.Snapshot(player, origin, true, packed.test(player))).toList();
        });
        doAnswer(call -> { call.<Runnable>getArgument(1).run(); return null; })
                .when(scheduler).execute(any(Player.class), any(Runnable.class));
        return plugin;
    }
}
