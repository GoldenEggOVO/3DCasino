package GoldenEggOVO.TDCasino.machine;

import GoldenEggOVO.TDCasino.game.blackjack.BlackjackMachine;
import GoldenEggOVO.TDCasino.game.duck_race.DuckRaceMachine;
import GoldenEggOVO.TDCasino.game.duck_race.DuckRaceRound;
import GoldenEggOVO.TDCasino.game.penguin_cross.PenguinCrossMachine;
import GoldenEggOVO.TDCasino.game.penguin_cross.PenguinCrossRound;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnimationLandingTest {
    @Test void penguinSinkFinishesAtTheRestingHeightWithoutATerminalJump() throws Exception {
        var machine = fixture(PenguinCrossMachine.class, "penguin_cross");
        var round = mock(PenguinCrossRound.class);
        when(round.finished()).thenReturn(true);
        when(round.steps()).thenReturn(3);
        set(machine, "round", round);
        set(machine, "previousStage", 3);
        ItemDisplay penguin = mock(ItemDisplay.class);
        set(machine, "figures", new ArrayList<>(List.of(penguin)));
        var ice = new ArrayList<ItemDisplay>();
        for (int i = 0; i < 8; i++) ice.add(mock(ItemDisplay.class));
        set(machine, "ice", ice);
        animate(machine, 0);
        assertLocation(machine.at(-.275, 1.08, 0), lastLocation(penguin));
        animate(machine, 1);
        assertLocation(machine.at(0, .55, 0), lastLocation(penguin));
        assertLocation(machine.at(0, .45, 0), lastLocation(ice.get(3)));
        for (int i = 0; i < 8; i++) if (i != 3) verify(ice.get(i), never()).teleport(any(Location.class));
    }

    @Test void successfulPenguinHopLandsOnTheNextIceFloeAtTheOriginalHeight() throws Exception {
        var machine = fixture(PenguinCrossMachine.class, "penguin_cross");
        var round = mock(PenguinCrossRound.class);
        set(machine, "round", round);
        set(machine, "previousStage", 2);
        ItemDisplay penguin = mock(ItemDisplay.class);
        set(machine, "figures", new ArrayList<>(List.of(penguin)));
        set(machine, "ice", java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> mock(ItemDisplay.class)).toList());
        animate(machine, 1);
        assertLocation(machine.at(-.275, 1.08, 0), lastLocation(penguin));
    }

    @Test void duckBobbingStartsAndEndsOnTheTrackForEveryLaneAndWinner() throws Exception {
        for (int winner = 0; winner < 4; winner++) {
            var machine = fixture(DuckRaceMachine.class, "duck_race");
            var round = mock(DuckRaceRound.class);
            when(round.winner()).thenReturn(winner);
            set(machine, "round", round);
            set(machine, "age", 37);
            List<ItemDisplay> ducks = new ArrayList<>();
            for (int lane = 0; lane < 4; lane++) ducks.add(mock(ItemDisplay.class));
            set(machine, "figures", ducks);
            animate(machine, 0);
            for (int lane = 0; lane < 4; lane++)
                assertLocation(machine.at(-.9 + lane * .6, 1.08, .7), lastLocation(ducks.get(lane)));
            animate(machine, 1);
            for (int lane = 0; lane < 4; lane++) {
                double finish = lane == winner ? -2.3 : -1.5 + lane * .18;
                assertLocation(machine.at(-.9 + lane * .6, 1.08, finish), lastLocation(ducks.get(lane)));
            }
        }
    }

    @Test void blackjackDealAcceleratesAndBrakesButStillLandsInEightTicks() throws Exception {
        var machine = mock(BlackjackMachine.class, CALLS_REAL_METHODS);
        ItemDisplay card = mock(ItemDisplay.class);
        World world = mock(World.class);
        var from = new Location(world, 0, 80, 0);
        var to = new Location(world, 1, 80, 0);
        var slideType = Class.forName(BlackjackMachine.class.getName() + "$Slide");
        var constructor = slideType.getDeclaredConstructor(Location.class, Location.class, int.class);
        constructor.setAccessible(true);
        Map<ItemDisplay, Object> slides = new HashMap<>();
        slides.put(card, constructor.newInstance(from, to, 0));
        set(machine, "slides", slides);
        set(machine, "pendingDeals", new HashSet<>());
        var animate = BlackjackMachine.class.getDeclaredMethod("animate");
        animate.setAccessible(true);
        double[] x = new double[9];
        for (int frame = 0; frame <= 8; frame++) {
            set(machine, "age", frame);
            animate.invoke(machine);
            Location at = lastLocation(card);
            x[frame] = at.getX();
            if (frame == 0 || frame == 8) assertEquals(80, at.getY(), 1e-9);
            else assertTrue(at.getY() > 80 && at.getY() <= 80.046);
            if (frame == 4) assertEquals(80.045, at.getY(), 1e-9);
            assertEquals(0, at.getZ(), 1e-9);
            assertEquals(frame == 8, slides.isEmpty());
        }
        assertTrue(x[1] < x[2] - x[1], "Deal should accelerate out of the shoe");
        assertTrue(x[8] - x[7] < x[7] - x[6], "Deal should brake before its slot");
        assertLocation(to, lastLocation(card));
        verify(card, never()).remove();
    }

    private static <T extends GameMachine<?>> T fixture(Class<T> type, String game) throws Exception {
        T machine = mock(type, CALLS_REAL_METHODS);
        set(machine, "manager", mock(MachineManager.class));
        set(machine, "origin", new Location(mock(World.class), 3, 64, 5));
        set(machine, "definition", MachineDefinition.builtin(game));
        return machine;
    }

    private static void animate(Object machine, double progress) throws Exception {
        var method = machine.getClass().getDeclaredMethod("animateFrame", double.class, double.class);
        method.setAccessible(true);
        method.invoke(machine, progress, progress);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static Location lastLocation(ItemDisplay part) {
        var locations = ArgumentCaptor.forClass(Location.class);
        verify(part, atLeastOnce()).teleport(locations.capture());
        return locations.getValue();
    }

    private static void assertLocation(Location expected, Location actual) {
        assertEquals(expected.getX(), actual.getX(), 1e-8);
        assertEquals(expected.getY(), actual.getY(), 1e-8);
        assertEquals(expected.getZ(), actual.getZ(), 1e-8);
    }
}
