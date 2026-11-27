package GoldenEggOVO.TDCasino.render;

import GoldenEggOVO.TDCasino.CasinoTestSupport;

import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.render.model.ModelCarrierItems;
import GoldenEggOVO.TDCasino.Core.render.model.ModelRepository;
import GoldenEggOVO.TDCasino.render.MachineGeometry;
import GoldenEggOVO.TDCasino.render.model.ModelCarrierItems;
import GoldenEggOVO.TDCasino.Core.render.DisplayAudience;
import GoldenEggOVO.TDCasino.Core.pack.ResourcePackService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PackedMachineDisplayTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @SuppressWarnings("unchecked")
    private static final class Fixture {
        final TDCasinoPlugin plugin = mock(TDCasinoPlugin.class);
        final World world = mock(World.class);
        final Player player = mock(Player.class);
        final ItemDisplay carrier = mock(ItemDisplay.class);
        final List<Entity> spawned = new ArrayList<>(), owned = new ArrayList<>();
        final Function<String, ItemStack> items = mock(Function.class);
        final Transformation pose = new Transformation(new Vector3f(.1f, .2f, .3f),
                new Quaternionf().rotateX(.4f), new Vector3f(2), new Quaternionf().rotateY(.2f));
        final ItemStack packedItem = new ItemStack(Material.PAPER);
        final Location at = new Location(world, 0, 80, 0);
        final DisplayAudience audience;

        Fixture() {
            when(plugin.scheduler()).thenReturn(new GoldenEggOVO.TDCasino.Core.platform.PlatformScheduler(plugin));
            when(plugin.playerViews()).thenReturn(new GoldenEggOVO.TDCasino.Core.render.PlayerViews(plugin));
            when(plugin.pack()).thenReturn(mock(ResourcePackService.class));
            when(plugin.resourcePackState()).thenAnswer(invocation -> plugin.pack());
            when(plugin.pack().managed()).thenCallRealMethod();
            when(plugin.pack().required()).thenCallRealMethod();
            when(world.getPlayers()).thenReturn(List.of(player));
            when(player.isOnline()).thenReturn(true);
            when(player.getLocation()).thenReturn(new Location(world, 0, 80, 1));
            when(plugin.allowed(player)).thenReturn(true);
            when(plugin.pack().packed(player)).thenReturn(true);
            when(carrier.isValid()).thenReturn(true);
            when(carrier.getWorld()).thenReturn(world);
            when(carrier.getLocation()).thenAnswer(call -> at.clone());
            when(carrier.getTransformation()).thenReturn(pose);
            when(carrier.getItemStack()).thenReturn(ModelCarrierItems.resolve("3dcasino:keno_tile"));
            when(items.apply(anyString())).thenReturn(packedItem);
            when(world.spawn(any(Location.class), any(Class.class), any(Consumer.class))).thenAnswer(call -> {
                Display entity = (Display) mock((Class<? extends Entity>) call.getArgument(1));
                when(entity.getTransformation()).thenReturn(new Transformation(
                        new Vector3f(), new Quaternionf(), new Vector3f(1), new Quaternionf()));
                when(entity.isValid()).thenReturn(true);
                spawned.add(entity);
                ((Consumer<Display>) call.getArgument(2)).accept(entity);
                return entity;
            });
            audience = DisplayAudience.withNativeFallback(plugin, carrier.getLocation());
        }

        MachineModelDisplay display() {
            return new MachineModelDisplay(carrier, owned::add, owned, audience, items);
        }

        long blocks() { return spawned.stream().filter(BlockDisplay.class::isInstance).count(); }
        ItemDisplay packed() {
            var models = spawned.stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).toList();
            assertEquals(1, models.size(), "A packed viewer needs exactly one model display");
            return models.getFirst();
        }
    }

    @Test
    void packedViewerGetsOneCompleteModelWithoutVanillaChildren() {
        var f = new Fixture();
        var display = f.display();
        assertEquals(0, f.blocks(), "A loaded-pack-only scene should not allocate native block children");
        var packed = f.packed();
        verify(packed).setItemStack(f.packedItem);
        verify(packed).setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        assertSameFacing(f, packed);
        verify(packed, atLeastOnce()).setVisibleByDefault(false);
        verify(f.player).showEntity(f.plugin, packed);
        for (int tick = 0; tick < 50; tick++) display.sync();
        assertEquals(1, f.spawned.size());
        verify(f.items, times(1)).apply("keno_tile");
    }

    @Test
    void packedModelKeepsNativeFacingDuringRotationAndButtonPresses() {
        var f = new Fixture();
        var display = f.display();
        var packed = f.packed();
        for (float yaw : new float[] {0, 90, 180, 270}) {
            f.at.setYaw(yaw);
            for (float angle : new float[] {0, .7f, 2.3f, 4.8f}) {
                for (float press : new float[] {0, -.04f}) {
                    f.pose.getLeftRotation().rotationXYZ(-.61f, angle, .3f);
                    f.pose.getTranslation().z = press;
                    f.pose.getScale().set(3, 4, 2);
                    display.sync();
                    assertSameFacing(f, packed);
                    assertEquals(new Quaternionf().rotateY(.2f), f.pose.getRightRotation(),
                            "Packed correction must not rotate the carrier");
                }
            }
            if (yaw != 0) verify(packed).teleport(f.at);
        }
    }

    private static void assertSameFacing(Fixture f, ItemDisplay packed) {
        var poses = org.mockito.ArgumentCaptor.forClass(Transformation.class);
        verify(packed, atLeastOnce()).setTransformation(poses.capture());
        var pose = poses.getValue();
        assertNotSame(f.pose.getTranslation(), pose.getTranslation());
        assertNotSame(f.pose.getLeftRotation(), pose.getLeftRotation());
        assertNotSame(f.pose.getScale(), pose.getScale());
        assertNotSame(f.pose.getRightRotation(), pose.getRightRotation());
        var actual = new org.joml.Matrix4f().translation(pose.getTranslation())
                .rotate(pose.getLeftRotation()).scale(pose.getScale()).rotate(pose.getRightRotation())
                .rotateY((float) Math.PI);
        // Pack coordinates are (8 - 4*x, 8 + 4*y, 8 - 4*z), centered and divided by 16.
        for (var physical : List.of(new Vector3f(.2f, .35f, .1f), new Vector3f(-.4f, .8f, -.3f))) {
            var packedPoint = new Vector3f(-physical.x, physical.y, -physical.z).mul(.25f);
            var expected = MachineGeometry.nativePose(f.pose).transformPosition(new Vector3f(physical).mul(.25f));
            assertEquals(0, actual.transformPosition(packedPoint).distance(expected), .00001,
                    "Packed and native geometry must face the same interaction surface");
        }
    }

    @Test
    void nativeWheelRotationsStayRigidAndTranslationStillInterpolates() {
        for (String name : List.of("wheel_of_fortune_disc", "money_wheel_disc")) {
            var f = new Fixture();
            when(f.plugin.pack().packed(f.player)).thenReturn(false);
            when(f.carrier.getItemStack()).thenReturn(ModelCarrierItems.resolve("3dcasino:" + name));
            f.audience.refresh();
            var display = f.display();
            assertTrue(f.blocks() > 0);
            f.pose.getLeftRotation().rotateZ(.8f);
            display.sync();
            assertNativeInterpolation(f, 0);
            f.pose.getLeftRotation().rotateZ(.06f);
            display.sync();
            assertNativeInterpolation(f, 1);
            f.pose.getTranslation().z -= .04f;
            display.sync();
            assertNativeInterpolation(f, 1);
        }
    }

    @Test
    void otherNativeModelRotationsKeepTheirInterpolation() {
        var f = new Fixture();
        when(f.plugin.pack().packed(f.player)).thenReturn(false);
        f.audience.refresh();
        var display = f.display();
        assertTrue(f.blocks() > 0);
        f.pose.getLeftRotation().rotateX(.7f);
        display.sync();
        assertNativeInterpolation(f, 1);
    }

    @Test
    void nativeModelHidingAndReappearanceDoNotInterpolateThroughSingularPartPoses() {
        var f = new Fixture();
        when(f.plugin.pack().packed(f.player)).thenReturn(false);
        when(f.carrier.getItemStack()).thenReturn(ModelCarrierItems.resolve("3dcasino:penguin_cross_penguin"));
        f.audience.refresh();
        var display = f.display();
        assertTrue(f.blocks() > 0);
        int entities = f.spawned.size();
        f.pose.getScale().set(0);
        display.sync();
        assertNativeInterpolation(f, 0);
        f.pose.getScale().set(4);
        display.sync();
        assertNativeInterpolation(f, 0);
        f.pose.getTranslation().z += .01f;
        display.sync();
        assertNativeInterpolation(f, 1);
        assertEquals(entities, f.spawned.size(), "Visibility changes must reuse the existing model parts");
    }

    private static void assertNativeInterpolation(Fixture f, int expected) {
        for (Entity entity : f.spawned) if (entity instanceof BlockDisplay block) {
            var durations = org.mockito.ArgumentCaptor.forClass(Integer.class);
            verify(block, atLeastOnce()).setInterpolationDuration(durations.capture());
            assertEquals(expected, durations.getValue());
        }
    }

    @Test
    void machinesRetainNativeFallbackWhenTheRequiredPackIsUnavailable() throws Exception {
        var f = new Fixture();
        var mode = ResourcePackService.class.getField("mode");
        mode.setAccessible(true);
        mode.set(f.plugin.pack(), ResourcePackService.Mode.RESOURCE_PACK);
        when(f.plugin.pack().packed(f.player)).thenReturn(false);
        f.audience.refresh();
        f.display();
        assertEquals(ModelRepository.get("casino/" + "keno_tile").blocks().size(), f.blocks());
        verify(f.items, never()).apply(anyString());
        for (Entity child : f.spawned) verify(f.player).showEntity(f.plugin, child);
    }

    @Test
    void disablingThePackReplacesItsLayerAndLeavingRemovesNativeChildren() {
        var f = new Fixture();
        var display = f.display();
        var packed = f.packed();
        when(f.plugin.pack().packed(f.player)).thenReturn(false);
        f.audience.refresh();
        display.sync();
        verify(packed).remove();
        assertFalse(f.owned.contains(packed));
        assertEquals(ModelRepository.get("casino/" + "keno_tile").blocks().size(), f.blocks());
        when(f.world.getPlayers()).thenReturn(List.of());
        f.audience.refresh();
        display.sync();
        assertTrue(f.owned.isEmpty(), "No visible audience should leave no allocated model parts");
    }

    @Test
    void packedRevealReusesTheDisplayAndKeepsGlowAndCarrierLifetime() {
        var f = new Fixture();
        var display = f.display();
        var packed = f.packed();
        when(f.carrier.getItemStack()).thenReturn(ModelCarrierItems.resolve("3dcasino:keno_tile_selected"));
        when(f.carrier.isGlowing()).thenReturn(true);
        display.sync();
        assertEquals(1, f.spawned.size());
        verify(f.items).apply("keno_tile_selected");
        verify(packed).setGlowing(true);
        when(f.carrier.isValid()).thenReturn(false);
        assertFalse(display.sync());
        verify(packed).remove();
        assertTrue(f.owned.isEmpty());
    }

    @Test
    void simultaneousViewersOnlySeeTheirOwnModelLayer() {
        var f = new Fixture();
        var nativePlayer = mock(Player.class);
        when(nativePlayer.isOnline()).thenReturn(true);
        when(nativePlayer.getLocation()).thenReturn(new Location(f.world, 0, 80, 1));
        when(f.plugin.allowed(nativePlayer)).thenReturn(true);
        when(f.world.getPlayers()).thenReturn(List.of(f.player, nativePlayer));
        f.audience.refresh();
        f.display();
        var packed = f.packed();
        verify(f.player).showEntity(f.plugin, packed);
        verify(nativePlayer, never()).showEntity(f.plugin, packed);
        assertTrue(f.blocks() > 0);
        for (Entity child : f.spawned) if (child instanceof BlockDisplay) {
            verify(nativePlayer).showEntity(f.plugin, child);
            verify(f.player, never()).showEntity(f.plugin, child);
        }
    }

    @Test
    void packedCabinetsKeepRuntimeTranslatedLabelsWithoutNativeBlocks() {
        var f = new Fixture();
        when(f.carrier.getItemStack()).thenReturn(ModelCarrierItems.resolve("3dcasino:blackjack_machine"));
        f.display();
        assertEquals(0, f.blocks());
        var labels = f.spawned.stream().filter(TextDisplay.class::isInstance).toList();
        assertEquals(ModelRepository.get("casino/" + "blackjack_machine").labels().size(), labels.size());
        assertFalse(labels.isEmpty());
        for (Entity label : labels) verify(f.player).showEntity(f.plugin, label);
        f.packed();
    }
}
