package GoldenEggOVO.TDCasino.game.penguin_cross;

import GoldenEggOVO.TDCasino.audio.MachineSounds;
import GoldenEggOVO.TDCasino.machine.AnimatedMachine;
import GoldenEggOVO.TDCasino.machine.GameMachine;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import org.bukkit.*;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PenguinCrossPresentationTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @ParameterizedTest @ValueSource(longs = {0, 100})
    void collectingAfterASuccessfulStepKeepsThePenguinStandingAtAnyStake(long stake) throws Exception {
        var f = new Fixture(stake, 20);
        f.action("play"); f.action("step"); f.frames(30);
        assertTrue(f.round.active()); assertEquals(1, f.round.steps());
        f.action("cash");
        assertTrue(f.round.finished()); assertEquals(20, f.round.lastRoll());
        assertEquals(Math.round(stake * 1.25), f.round.payout());
        f.standing(-.825);
        verify(f.listener, never()).playSound(any(Location.class), eq(Sound.ENTITY_GENERIC_SPLASH),
                any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @ParameterizedTest @ValueSource(longs = {0, 100})
    void crossingEightSuccessfulStepsKeepsTheFinalFloeAndPenguinAboveWater(long stake) throws Exception {
        var f = new Fixture(stake, 20);
        f.action("play");
        for (int step = 0; step < 8; step++) { f.action("step"); f.frames(30); }
        assertTrue(f.round.finished()); assertEquals(8, f.round.steps());
        assertEquals(20, f.round.lastRoll());
        assertEquals(Math.round(stake * Math.pow(1.25, 8)), f.round.payout());
        f.standing(1.1);
        verify(f.listener, never()).playSound(any(Location.class), eq(Sound.ENTITY_GENERIC_SPLASH),
                any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @ParameterizedTest @ValueSource(longs = {0, 100})
    void hopPreparesAtTheBankAndMakesOnlyTheLandingFloeBob(long stake) throws Exception {
        var f = new Fixture(stake, 20);
        f.action("play"); f.action("step"); f.frames(2);
        assertEquals(f.point(-1.1, 1.08, 0), f.at(f.penguin));
        assertTrue(f.pose(f.penguin).getScale().y < 4);
        f.frames(9);
        assertTrue(f.at(f.penguin).getY() > f.point(0, 1.28, 0).getY());
        f.frames(7);
        assertEquals(f.point(-.825, 1.08, 0), f.at(f.penguin));
        f.frames(6);
        assertTrue(f.at(f.ice.getFirst()).getY() < f.point(0, .98, 0).getY());
        assertEquals(.08, f.at(f.penguin).getY() - f.at(f.ice.getFirst()).getY(), 1e-7);
        assertEquals(f.point(-.55, 1, 0), f.at(f.ice.get(1)));
        f.frames(6); f.standing(-.825);
    }

    @ParameterizedTest @ValueSource(longs = {0, 100})
    void aFailedRollStillSinksSplashesAndRestartsWithEveryFloeRestored(long stake) throws Exception {
        var f = new Fixture(stake, 19, 20);
        f.action("play"); f.action("step");
        double previousY = Double.POSITIVE_INFINITY;
        for (int tick = 1; tick <= 30; tick++) {
            f.frames(1);
            if (tick >= 19) {
                assertTrue(f.at(f.penguin).getY() <= previousY + 1e-7, "The failure sink must not rebound");
                previousY = f.at(f.penguin).getY();
            }
        }
        assertTrue(f.round.finished()); assertEquals(19, f.round.lastRoll()); assertEquals(0, f.round.payout());
        assertEquals(f.point(-.825, .55, 0), f.at(f.penguin));
        assertEquals(0, f.pose(f.penguin).getScale().length(), 1e-7);
        assertEquals(f.point(-.825, .45, 0), f.at(f.ice.getFirst()));
        for (int i = 1; i < 8; i++) assertEquals(81, f.at(f.ice.get(i)).getY(), 1e-7);
        verify(f.listener, times(1)).playSound(any(Location.class), eq(Sound.ENTITY_GENERIC_SPLASH),
                eq(SoundCategory.BLOCKS), eq(.22f), eq(1.2f));
        f.action("play"); f.standing(-1.1);
        f.action("step"); f.frames(30); f.standing(-.825);
        assertTrue(f.round.active()); assertEquals(20, f.round.lastRoll());
        verify(f.listener, times(1)).playSound(any(Location.class), eq(Sound.ENTITY_GENERIC_SPLASH),
                any(SoundCategory.class), anyFloat(), anyFloat());
    }

    private static final class Fixture {
        final PenguinCrossMachine machine = mock(PenguinCrossMachine.class, CALLS_REAL_METHODS);
        final ItemDisplay penguin = mock(ItemDisplay.class);
        final List<ItemDisplay> ice = new ArrayList<>();
        final Player listener = mock(Player.class);
        final World world = mock(World.class);
        final PenguinCrossRound round;
        int age;

        Fixture(long stake, int... rolls) throws Exception {
            var soundPlugin = GoldenEggOVO.TDCasino.audio.SoundListeners.install(mock(GoldenEggOVO.TDCasino.TDCasinoPlugin.class), p -> false, listener);
            set(machine, "manager", mock(GoldenEggOVO.TDCasino.machine.MachineManager.class));
            set(machine, "origin", new Location(world, 3, 80, 5));
            set(machine, "definition", MachineDefinition.builtin("penguin_cross"));
            set(machine, "sounds", new MachineSounds(soundPlugin, new Location(world, 3, 80, 5)));
            set(machine, "figures", new ArrayList<>(List.of(penguin)));
            for (int i = 0; i < 8; i++) ice.add(mock(ItemDisplay.class));
            set(machine, "ice", ice);
            round = new PenguinCrossRound(new Random(0) {
                int index;
                @Override public int nextInt(int bound) { assertEquals(100, bound); return rolls[Math.min(index++, rolls.length - 1)]; }
            });
            round.setStake(stake);
            set(machine, "round", round);
        }

        void action(String action) throws Exception {
            assertTrue(round.available(action), "The fixture must only invoke a legal round action");
            var busy = AnimatedMachine.class.getDeclaredMethod("busy"); busy.setAccessible(true);
            assertEquals(false, busy.invoke(machine), "The fixture must wait until the previous animation finishes");
            var perform = AnimatedMachine.class.getDeclaredMethod("action", String.class); perform.setAccessible(true);
            perform.invoke(machine, action);
        }
        void frames(int count) throws Exception {
            var animate = AnimatedMachine.class.getDeclaredMethod("animate"); animate.setAccessible(true);
            for (int i = 0; i < count; i++) { set(machine, "age", ++age); animate.invoke(machine); }
        }
        Location point(double x, double y, double z) throws Exception {
            var at = GameMachine.class.getDeclaredMethod("at", double.class, double.class, double.class);
            at.setAccessible(true); return (Location) at.invoke(machine, x, y, z);
        }
        Location at(ItemDisplay display) {
            var captured = ArgumentCaptor.forClass(Location.class);
            verify(display, atLeastOnce()).teleport(captured.capture()); return captured.getValue();
        }
        Transformation pose(ItemDisplay display) {
            var captured = ArgumentCaptor.forClass(Transformation.class);
            verify(display, atLeastOnce()).setTransformation(captured.capture()); return captured.getValue();
        }
        void standing(double x) throws Exception {
            assertEquals(point(x, 1.08, 0), at(penguin));
            assertEquals(4, pose(penguin).getScale().x, 1e-7);
            for (int i = 0; i < 8; i++) assertEquals(point(-1.1 + (i + 1) * 2.2 / 8, 1, 0), at(ice.get(i)));
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
