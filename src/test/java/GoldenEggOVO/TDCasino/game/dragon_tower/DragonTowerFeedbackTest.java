package GoldenEggOVO.TDCasino.game.dragon_tower;

import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.CasinoModule;
import GoldenEggOVO.TDCasino.Core.player.PlayerSessions;
import GoldenEggOVO.TDCasino.economy.MachinePaymentLedger;
import GoldenEggOVO.TDCasino.economy.RoundFunding;
import GoldenEggOVO.TDCasino.machine.GameMachine;
import GoldenEggOVO.TDCasino.machine.MachineManager;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DragonTowerFeedbackTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void selectedTileGivesAShortLocalPulseAndReturnsWithoutDelayingTheNextChoice(boolean compact) throws Exception {
        var f = new Fixture(compact);
        f.machine.action("play");
        f.machine.action("select:0");
        assertEquals(1, f.round.floors());
        assertTrue(f.machine.available("select:0"), "The next floor must remain immediately available");
        long payout = f.round.payout();
        f.tiles.forEach(org.mockito.Mockito::clearInvocations);
        f.frame(3);
        assertTrue(pose(f.tiles.getFirst()).getTranslation().length() > .005,
                "The selected tile should lift toward the player as feedback");
        for (int tile = 1; tile < 24; tile++) verify(f.tiles.get(tile), never()).setTransformation(any());
        f.frame(6);
        assertEquals(0, pose(f.tiles.getFirst()).getTranslation().length(), 1e-7);
        assertEquals(payout, f.round.payout(), "Visual animation must not alter winnings");
        var busy = GameMachine.class.getDeclaredMethod("busy"); busy.setAccessible(true);
        assertEquals(false, busy.invoke(f.machine));
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void aSecondChoicePreservesTheFirstPulseAndNewRoundsClearAllOffsets(boolean compact) throws Exception {
        var f = new Fixture(compact);
        f.machine.action("play"); f.machine.action("select:0"); f.frame(2);
        f.machine.action("select:0"); f.frame(3);
        assertTrue(pose(f.tiles.getFirst()).getTranslation().length() > .005);
        assertTrue(pose(f.tiles.get(4)).getTranslation().length() > 0);
        f.machine.action("cash"); f.machine.action("play");
        for (ItemDisplay tile : f.tiles) assertEquals(0, pose(tile).getTranslation().length(), 1e-7);
        f.frame(10);
        for (ItemDisplay tile : f.tiles) assertEquals(0, pose(tile).getTranslation().length(), 1e-7);
    }

    private static Transformation pose(ItemDisplay tile) {
        var poses = ArgumentCaptor.forClass(Transformation.class);
        verify(tile, atLeastOnce()).setTransformation(poses.capture()); return poses.getValue();
    }

    private static final class Fixture {
        final World world = mock(World.class);
        final DragonTowerMachine machine;
        final DragonTowerRound round = new DragonTowerRound(new Random(0) {
            @Override public int nextInt(int bound) { return 1; }
        });
        final List<ItemDisplay> tiles;

        @SuppressWarnings("unchecked") Fixture(boolean compact) throws Exception {
            var plugin = mock(TDCasinoPlugin.class); var manager = mock(MachineManager.class);
            var casino = mock(CasinoModule.class); var payments = mock(MachinePaymentLedger.class);
            var pluginGetter = MachineManager.class.getDeclaredMethod("plugin"); pluginGetter.setAccessible(true);
            when((TDCasinoPlugin) pluginGetter.invoke(manager)).thenReturn(plugin);
            when(plugin.sessions()).thenReturn(new PlayerSessions());
            when(plugin.casino()).thenReturn(casino); when(casino.payments()).thenReturn(payments);
            when(payments.bind(any(), anyString())).thenReturn(RoundFunding.none());
            var builtin = MachineDefinition.builtin("dragon_tower");
            var definition = compact ? builtin : new MachineDefinition("custom_dragon", builtin.game(), builtin.models(),
                    builtin.anchors(), builtin.buttons(), builtin.parts(), builtin.settingsBounds());
            machine = new DragonTowerMachine(manager, UUID.randomUUID(), new Location(world, 0, 80, 0), definition);
            var roundField = GameMachine.class.getDeclaredField("round"); roundField.setAccessible(true); roundField.set(machine, round);
            var tilesField = DragonTowerMachine.class.getDeclaredField("tiles"); tilesField.setAccessible(true);
            tiles = (List<ItemDisplay>) tilesField.get(machine);
            for (int i = 0; i < 24; i++) tiles.add(mock(ItemDisplay.class));
        }

        void frame(int frame) throws Exception {
            var age = GameMachine.class.getDeclaredField("age"); age.setAccessible(true); age.setInt(machine, frame);
            Class<?> type = DragonTowerMachine.class;
            while (type != null) {
                try { var animate = type.getDeclaredMethod("animate"); animate.setAccessible(true); animate.invoke(machine); return; }
                catch (NoSuchMethodException ignored) { type = type.getSuperclass(); }
            }
            throw new NoSuchMethodException("animate");
        }
    }
}
