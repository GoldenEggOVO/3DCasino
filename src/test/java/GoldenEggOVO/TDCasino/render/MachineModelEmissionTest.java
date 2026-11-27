package GoldenEggOVO.TDCasino.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import GoldenEggOVO.TDCasino.render.model.ModelCarrierItems;
import GoldenEggOVO.TDCasino.Core.render.model.ModelRepository;
import GoldenEggOVO.TDCasino.Core.render.model.ModelDefinition;
import GoldenEggOVO.TDCasino.render.MachineGeometry;
import GoldenEggOVO.TDCasino.render.model.ModelCarrierItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class MachineModelEmissionTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test
    void initialBuildLightsOnlyEmissivePartsAndKeepsLegacyBrightness() {
        var model = geometry("""
                {"blocks":[
                  {"name":"first","position":[0.5,0.5,0.5],"size":[1,1,1],"material":"STONE"},
                  {"name":"second","position":[1.5,0.5,0.5],"size":[1,1,1],"material":"SEA_LANTERN","brightness":15}
                ],"labels":[]}
                """);
        try (var models = mockStatic(ModelRepository.class, CALLS_REAL_METHODS)) {
            models.when(() -> ModelRepository.get("casino/" + "dragon_tile_hidden")).thenReturn(model);
            var fixture = new Fixture();

            assertEquals(2, fixture.created.size());
            assertBrightness(fixture.created.get(0), 12);
            assertBrightness(fixture.created.get(1), 15);
            assertEquals(fixture.created, fixture.registered);
        }
    }

    @Test
    void inPlaceRecolorUpdatesEmissionAndRestoresCarrierBrightnessWhenEmissionEnds() {
        var lit = geometry(singleBox("SEA_LANTERN", ",\"brightness\":15"));
        var dim = geometry(singleBox("CYAN_CONCRETE", ",\"brightness\":4"));
        var ordinary = geometry(singleBox("STONE", ""));
        try (var models = mockStatic(ModelRepository.class, CALLS_REAL_METHODS)) {
            models.when(() -> ModelRepository.get("casino/" + "dragon_tile_hidden")).thenReturn(lit);
            models.when(() -> ModelRepository.get("casino/" + "dragon_tile_safe")).thenReturn(dim);
            models.when(() -> ModelRepository.get("casino/" + "dragon_tile_trap")).thenReturn(ordinary);
            var fixture = new Fixture();
            var block = fixture.created.getFirst();
            assertBrightness(block, 15);

            fixture.change("dragon_tile_safe");
            assertBrightness(block, 4);
            assertEquals(org.bukkit.Material.CYAN_CONCRETE, block.getBlock().getMaterial());

            fixture.change("dragon_tile_trap");
            assertBrightness(block, 12);
            assertEquals(org.bukkit.Material.STONE, block.getBlock().getMaterial());
            assertEquals(List.of(block), fixture.created);
            assertEquals(List.of(block), fixture.registered);
            verify(block, never()).remove();
        }
    }

    private static ModelDefinition geometry(String json) {
        return ModelRepository.read(path -> new java.io.ByteArrayInputStream(
                (path.endsWith("index.json") ? "{\"casino/test\":\"test.json\"}" : json)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))).get("casino/test");
    }

    private static String singleBox(String material, String brightness) {
        return "{\"blocks\":[{\"name\":\"part\",\"position\":[0.5,0.5,0.5],\"size\":[1,1,1],\"material\":\""
                + material + "\"" + brightness + "}],\"labels\":[]}";
    }

    private static void assertBrightness(Display display, int expected) {
        assertNotNull(display.getBrightness());
        assertEquals(expected, display.getBrightness().getBlockLight());
        assertEquals(expected, display.getBrightness().getSkyLight());
    }

    @SuppressWarnings("unchecked")
    private static final class Fixture {
        final World world = mock(World.class);
        final ItemDisplay carrier = mock(ItemDisplay.class);
        final List<BlockDisplay> created = new ArrayList<>();
        final List<Entity> registered = new ArrayList<>();
        final Map<Display, Display.Brightness> brightness = new HashMap<>();
        final MachineModelDisplay display;
        ItemStack item = ModelCarrierItems.resolve("3dcasino:dragon_tile_hidden");

        Fixture() {
            when(carrier.isValid()).thenReturn(true);
            when(carrier.getWorld()).thenReturn(world);
            when(carrier.getLocation()).thenReturn(new Location(world, 0, 0, 0));
            when(carrier.getItemStack()).thenAnswer(call -> item);
            when(carrier.getBrightness()).thenReturn(new Display.Brightness(12, 12));
            when(carrier.getTransformation()).thenReturn(MachineGeometry.itemPose(4, 0, 0));
            when(world.spawn(any(Location.class), eq(BlockDisplay.class), any(Consumer.class))).thenAnswer(call -> {
                var block = mock(BlockDisplay.class);
                created.add(block);
                when(block.getTransformation()).thenReturn(new Transformation(new Vector3f(),
                        new Quaternionf(), new Vector3f(1), new Quaternionf()));
                when(block.getBrightness()).thenAnswer(i -> brightness.get(block));
                doAnswer(i -> { brightness.put(block, i.getArgument(0)); return null; })
                        .when(block).setBrightness(any());
                var data = new org.bukkit.block.data.BlockData[1];
                when(block.getBlock()).thenAnswer(i -> data[0]);
                doAnswer(i -> { data[0] = i.getArgument(0); return null; }).when(block).setBlock(any());
                ((Consumer<BlockDisplay>) call.getArgument(2)).accept(block);
                return block;
            });
            display = new MachineModelDisplay(carrier, entity -> {
                registered.add(entity);
                ((Display) entity).setBrightness(new Display.Brightness(12, 12));
            }, registered);
        }

        void change(String name) {
            item = ModelCarrierItems.resolve("3dcasino:" + name);
            assertTrue(display.sync());
        }
    }
}
