package GoldenEggOVO.TDCasino.machine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import GoldenEggOVO.TDCasino.game.slots.SlotsRound;
import GoldenEggOVO.TDCasino.render.MachineGeometry;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WheelDisplayTest {
    private static final class TestMachine extends GameMachine<SlotsRound> {
        TestMachine(MachineManager manager, Location origin) {
            super(manager, UUID.randomUUID(), origin, MachineDefinition.builtin("slots"),
                    () -> new SlotsRound(new Random(1)));
        }
        protected void buildGame() {}
        protected void action(String action) {}
        protected boolean available(String action) { return false; }
        protected void refresh() {}
    }

    private static final class Fixture {
        final ItemDisplay disc = mock(ItemDisplay.class);
        final ItemDisplay pointer = mock(ItemDisplay.class);
        final Player listener = mock(Player.class);
        final List<TextDisplay> labels = new ArrayList<>();
        final TestMachine machine;
        final WheelDisplay wheel;
        final World world = mock(World.class);

        Fixture(double radius, float labelSize, float yaw) {
            var plugin = mock(TDCasinoPlugin.class);
            var manager = mock(MachineManager.class);
            var casino = mock(GoldenEggOVO.TDCasino.CasinoModule.class);
            var payments = mock(GoldenEggOVO.TDCasino.economy.MachinePaymentLedger.class);
            when(manager.plugin()).thenReturn(plugin);
            when(plugin.sessions()).thenReturn(new GoldenEggOVO.TDCasino.Core.player.PlayerSessions());
            when(plugin.casino()).thenReturn(casino);
            when(casino.payments()).thenReturn(payments);
            when(payments.bind(any(), anyString()))
                    .thenReturn(GoldenEggOVO.TDCasino.economy.RoundFunding.none());
            GoldenEggOVO.TDCasino.audio.SoundListeners.install(plugin, ignored -> false, listener);
            machine = new TestMachine(manager, new Location(world, 3, 64, 5, yaw, 0));
            for (int i = 0; i < 20; i++) labels.add(mock(TextDisplay.class));
            wheel = new WheelDisplay(machine, disc, pointer, labels, radius, labelSize);
        }

        Transformation pose(Display display) {
            var poses = ArgumentCaptor.forClass(Transformation.class);
            verify(display, atLeastOnce()).setTransformation(poses.capture());
            return poses.getValue();
        }

        void assertDiscAngle(double angle) {
            var actual = pose(disc);
            var expected = MachineGeometry.itemPose(4, 0, 0);
            expected.getLeftRotation().rotateZ((float) angle);
            var point = new Vector3f(.21f, .37f, -.13f);
            assertEquals(0, matrix(actual).transformPosition(new Vector3f(point))
                    .distance(matrix(expected).transformPosition(point)), .00001);
        }
    }

    @Test
    void pointerYieldsAtAPegAndReturnsUprightAtTheResult() {
        var f = new Fixture(.78, .16f, 0);
        f.wheel.planSpin(7);
        f.wheel.animate(.5);
        assertTrue(f.pose(f.pointer).getLeftRotation().z < -.05f);
        f.wheel.finishSpin();
        assertEquals(0, f.pose(f.pointer).getLeftRotation().z, 1e-6);
    }

    @Test
    void bothWheelStylesAlignTheirWinningLabelAtEveryHeading() {
        for (float yaw : new float[] {0, 90, 180, 270}) {
            for (double radius : new double[] {.78, .64}) {
                float size = radius == .78 ? .16f : .25f;
                var f = new Fixture(radius, size, yaw);
                for (int segment : new int[] {0, 4, 19}) {
                    f.wheel.refresh(true, segment);
                    verify(f.labels.get(segment), atLeastOnce())
                            .teleport(f.machine.at(0, 1.98 + radius, .321));
                    assertEquals(size, f.pose(f.labels.get(segment)).getScale().x, .000001);
                    f.assertDiscAngle((segment + .5) * Math.PI / 10);
                }
            }
        }
    }

    @Test
    void spinKeepsFiveFullForwardTurnsBeforeItsSelectedSegment() {
        var f = new Fixture(.78, .16f, 0);
        f.wheel.refresh(false, 0);
        f.wheel.planSpin(19);
        f.wheel.animate(.5);
        f.assertDiscAngle((10 * Math.PI + 39 * Math.PI / 20) * WheelDisplay.spinTravel(.5));
        f.wheel.animate(1);
        f.assertDiscAngle(10 * Math.PI + 39 * Math.PI / 20);
    }

    @Test
    void spinAcceleratesFromRestAndBrakesWithoutReversingOrOvershooting() {
        double previous = 0;
        double[] speed = new double[80];
        for (int i = 0; i < speed.length; i++) {
            double next = WheelDisplay.spinTravel((i + 1) / 80.0);
            speed[i] = next - previous;
            assertTrue(speed[i] > 0 && next <= 1);
            previous = next;
        }
        assertTrue(speed[0] < speed[4] && speed[4] < speed[10]);
        assertTrue(speed[25] > speed[50] && speed[50] > speed[79]);
        assertEquals(1, previous, 1e-12);
    }

    @Test
    void replayStartsFromTheEndOfThePreviousSpin() {
        var f = new Fixture(.64, .25f, 0);
        f.wheel.planSpin(0);
        f.wheel.animate(.9);
        f.wheel.finishSpin();
        f.assertDiscAngle(10 * Math.PI + Math.PI / 20);
        f.wheel.planSpin(19);
        f.wheel.animate(0);
        f.assertDiscAngle(10 * Math.PI + Math.PI / 20);
        f.wheel.animate(1);
        f.assertDiscAngle(20 * Math.PI + 39 * Math.PI / 20);
    }

    @Test
    void tickSoundOnlyPlaysWhenTheDiscCrossesASegment() {
        var f = new Fixture(.78, .16f, 0);
        f.wheel.planSpin(0);
        f.wheel.animate(0);
        f.wheel.animate(.01);
        verifyNoInteractions(f.listener);
        f.wheel.animate(.2);
        verify(f.listener).playSound(any(Location.class), eq(Sound.BLOCK_WOOD_HIT),
                eq(SoundCategory.BLOCKS), eq(.16f), floatThat(pitch -> Math.abs(pitch - 1.19f) < .000001));
    }

    @Test void rapidSectorCrossingsKeepAtLeastThreeTicksBetweenClicks() {
        var f = new Fixture(.78, .16f, 0);
        var ticks = new ArrayList<Integer>();
        doAnswer(call -> { ticks.add(f.machine.age); return null; }).when(f.listener)
                .playSound(any(Location.class), eq(Sound.BLOCK_WOOD_HIT),
                        eq(SoundCategory.BLOCKS), anyFloat(), anyFloat());
        f.wheel.planSpin(19);
        for (int tick = 1; tick < 80; tick++) {
            f.machine.age = tick;
            double progress = tick / 80.0;
            f.wheel.animate(progress);
        }
        assertTrue(ticks.size() > 10, "The physical ratchet must remain audible throughout the spin");
        for (int i = 1; i < ticks.size(); i++)
            assertTrue(ticks.get(i) - ticks.get(i - 1) >= 3, "Ratchet clicks must not crowd adjacent ticks");
        assertTrue(ticks.getLast() > 60, "The final slower sector crossings must remain audible");
        f.wheel.finishSpin();
        f.assertDiscAngle(10 * Math.PI + 39 * Math.PI / 20);
    }

    private static Matrix4f matrix(Transformation pose) {
        return new Matrix4f().translation(pose.getTranslation()).rotate(pose.getLeftRotation())
                .scale(pose.getScale()).rotate(pose.getRightRotation());
    }
}
