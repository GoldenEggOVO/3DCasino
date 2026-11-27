package GoldenEggOVO.TDCasino.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import GoldenEggOVO.TDCasino.render.model.ModelCarrierItems;
import GoldenEggOVO.TDCasino.Core.render.DisplayAudience;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class NativePlayingCardDisplayTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void casinoSuitsUseTheSharedTwentyFourHeadFaces() throws Exception {
        try (var input = getClass().getResourceAsStream("/vanilla-models/shared/cards/head_textures.json")) {
            var catalog = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            var fixture = new Fixture(null);
            for (var example : Map.of(0, "spades_ace", 13, "hearts_ace", 26, "clubs_ace",
                    39, "diamonds_ace", 51, "diamonds_king").entrySet()) {
                fixture.face(example.getKey());
                var cards = fixture.active();
                assertEquals(24, cards.size());
                for (int tile = 0; tile < 24; tile++) {
                    var head = assertInstanceOf(ItemDisplay.class, cards.get(tile));
                    assertEquals(Material.PLAYER_HEAD, head.getItemStack().getType());
                    var profile = ((SkullMeta) head.getItemStack().getItemMeta()).getPlayerProfile();
                    var texture = profile.getProperties().stream()
                            .filter(p -> p.getName().equals("textures")).findFirst().orElseThrow();
                    var expected = catalog.getAsJsonArray(example.getValue()).get(tile).getAsJsonObject();
                    assertEquals(expected.get("value").getAsString(), texture.getValue());
                    assertEquals(expected.get("signature").getAsString(), texture.getSignature());
                }
            }
            fixture.close();
        }
    }

    @Test void nativeHeadGridKeepsTheLargeCasinoFootprintAndFacesUp() throws Exception {
        var fixture = new Fixture(null);
        fixture.face(0);
        var client = new Matrix4f().rotateY((float) Math.PI)
                .translate(0, -.5f, 0).rotateX((float) Math.PI);
        var cards = fixture.active();
        assertEquals(24, cards.size());
        for (int tile = 0; tile < 24; tile++) {
            var matrix = new Matrix4f(fixture.matrices.get(cards.get(tile))).mul(client);
            var topLeft = matrix.transformPosition(new Vector3f(-.25f, -.5f, -.25f));
            var bottomRight = matrix.transformPosition(new Vector3f(.25f, 0, -.25f));
            assertEquals(-.215 + tile % 4 * .1075, topLeft.x, 1e-5);
            assertEquals(-.215 + (tile % 4 + 1) * .1075, bottomRight.x, 1e-5);
            assertEquals(-.43 * 2 / 3 + tile / 4 * (.43 * 2 / 9), topLeft.z, 1e-5);
            assertEquals(-.43 * 2 / 3 + (tile / 4 + 1) * (.43 * 2 / 9), bottomRight.z, 1e-5);
            assertEquals(topLeft.y, bottomRight.y, 1e-5);
            assertTrue(topLeft.y >= 0 && topLeft.y < .01);
            var normal = matrix.transformDirection(new Vector3f(0, 0, -1)).normalize();
            assertEquals(1, normal.y, 1e-5);
        }
        fixture.close();
    }

    @Test void backUsesTwoSeparatedBlocksAndRevealingRemovesTheOldFace() throws Exception {
        var fixture = new Fixture(null);
        fixture.face(52);
        var backs = fixture.active();
        assertEquals(2, backs.size());
        var white = assertInstanceOf(BlockDisplay.class, backs.get(0));
        var blue = assertInstanceOf(BlockDisplay.class, backs.get(1));
        assertEquals(Material.WHITE_CONCRETE, white.getBlock().getMaterial());
        assertEquals(Material.BLUE_CONCRETE, blue.getBlock().getMaterial());
        var whitePose = fixture.matrices.get(white);
        var bluePose = fixture.matrices.get(blue);
        assertEquals(.43, whitePose.getScale(new Vector3f()).x, 1e-5);
        assertEquals(.43 * 4 / 3, whitePose.getScale(new Vector3f()).y, 1e-5);
        assertTrue(bluePose.transformPosition(new Vector3f()).y
                > whitePose.transformPosition(new Vector3f(0, 0, 1)).y);
        fixture.face(47);
        assertEquals(24, fixture.active().size());
        assertTrue(fixture.removed.containsAll(backs));
        fixture.location.add(1, .5, -2);
        fixture.display.sync();
        for (var entity : fixture.active()) assertEquals(fixture.location, entity.getLocation());
        fixture.close();
    }

    @Test void packedViewersNeverReceiveNativeHeadsAndLayerChangesReleaseThem() throws Exception {
        var audience = mock(DisplayAudience.class);
        when(audience.needed(true)).thenReturn(true);
        doAnswer(call -> { ((Entity) call.getArgument(0)).remove(); return null; })
                .when(audience).remove(any(Entity.class));
        var fixture = new Fixture(audience);
        fixture.face(0);
        assertEquals(1, fixture.active().size());
        assertEquals(Material.PAPER, ((ItemDisplay) fixture.active().getFirst()).getItemStack().getType());
        when(audience.needed(false)).thenReturn(true);
        fixture.display.sync();
        assertEquals(25, fixture.active().size());
        when(audience.needed(false)).thenReturn(false);
        fixture.display.sync();
        assertEquals(1, fixture.active().size());
        fixture.close();
    }

    private static final class Fixture {
        final World world = mock(World.class);
        final ItemDisplay carrier = mock(ItemDisplay.class);
        final List<Entity> created = new ArrayList<>(), registered = new ArrayList<>();
        final Set<Entity> removed = new HashSet<>();
        final Map<Entity, Matrix4f> matrices = new HashMap<>();
        final Location location = new Location(world, 0, 0, 0);
        final MachineModelDisplay display;
        ItemStack face = ModelCarrierItems.resolve("3dcasino:card_0");

        @SuppressWarnings("unchecked")
        Fixture(DisplayAudience audience) {
            when(carrier.isValid()).thenReturn(true);
            when(carrier.getWorld()).thenReturn(world);
            when(carrier.getLocation()).thenAnswer(call -> location.clone());
            when(carrier.getItemStack()).thenAnswer(call -> face);
            when(carrier.getTransformation()).thenReturn(MachineGeometry.itemPose(.43, -Math.PI / 2, 0));
            when(world.spawn(any(Location.class), any(Class.class), any(Consumer.class))).thenAnswer(call -> {
                var entity = mock((Class<? extends Display>) call.getArgument(1));
                created.add(entity);
                var at = new Location[]{((Location) call.getArgument(0)).clone()};
                var pose = new Transformation[]{new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(1), new Quaternionf())};
                when(entity.getLocation()).thenAnswer(i -> at[0].clone());
                doAnswer(i -> { at[0] = ((Location) i.getArgument(0)).clone(); return true; })
                        .when(entity).teleport(any(Location.class));
                doAnswer(i -> { removed.add(entity); return null; }).when(entity).remove();
                when(entity.getTransformation()).thenAnswer(i -> pose[0]);
                doAnswer(i -> {
                    var m = (Matrix4f) i.getArgument(0);
                    pose[0] = new Transformation(m.getTranslation(new Vector3f()),
                            m.getUnnormalizedRotation(new Quaternionf()), m.getScale(new Vector3f()),
                            new Quaternionf());
                    return null;
                }).when(entity).setTransformationMatrix(any(Matrix4f.class));
                doAnswer(i -> {
                    pose[0] = i.getArgument(0);
                    matrices.put(entity, new Matrix4f().translate(pose[0].getTranslation())
                            .rotate(pose[0].getLeftRotation()).scale(pose[0].getScale())
                            .rotate(pose[0].getRightRotation()));
                    return null;
                }).when(entity).setTransformation(any(Transformation.class));
                if (entity instanceof ItemDisplay item) {
                    var stack = new ItemStack[]{new ItemStack(Material.AIR)};
                    when(item.getItemStack()).thenAnswer(i -> stack[0]);
                    doAnswer(i -> { stack[0] = i.getArgument(0); return null; }).when(item).setItemStack(any());
                } else if (entity instanceof BlockDisplay block) {
                    var material = new org.bukkit.block.data.BlockData[]{Material.AIR.createBlockData()};
                    when(block.getBlock()).thenAnswer(i -> material[0]);
                    doAnswer(i -> { material[0] = i.getArgument(0); return null; }).when(block).setBlock(any());
                }
                ((Consumer<Entity>) call.getArgument(2)).accept(entity);
                return entity;
            });
            display = new MachineModelDisplay(carrier, registered::add, registered, audience,
                    name -> new ItemStack(Material.PAPER));
        }

        void face(int index) { face = ModelCarrierItems.resolve("3dcasino:card_" + index); display.sync(); }
        List<Entity> active() { return created.stream().filter(e -> !removed.contains(e)).toList(); }
        void close() {
            when(carrier.isValid()).thenReturn(false);
            assertFalse(display.sync());
            assertEquals(0, active().size());
            assertTrue(registered.isEmpty());
        }
    }
}
