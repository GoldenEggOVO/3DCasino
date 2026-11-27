package GoldenEggOVO.TDCasino.Core.render.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ModelRepositoryTest {
    private static final String MODEL = """
            {"blocks":[{"name":"top","position":[0,0.5,0],"size":[2,1,3],
              "material":"DARK_OAK_PLANKS"}],"labels":[]}
            """;

    @Test void indexAliasesShareOneImmutableDefinitionAndReadEachFileOnce() {
        var reads = new AtomicInteger();
        var resources = Map.of("/vanilla-models/index.json",
                "{\"casino/example\":\"shared/example.json\",\"shared/example\":\"shared/example.json\"}",
                "/vanilla-models/shared/example.json", MODEL);
        var models = ModelRepository.read(path -> {
            if (!path.endsWith("index.json")) reads.incrementAndGet();
            return input(resources.get(path));
        });
        assertEquals(1, reads.get());
        assertSame(models.get("casino/example"), models.get("shared/example"));
        assertThrows(UnsupportedOperationException.class, models::clear);
        assertThrows(UnsupportedOperationException.class, models.get("casino/example").blocks()::clear);
        assertEquals(Material.DARK_OAK_PLANKS, models.get("casino/example").blocks().getFirst().material());
        assertNull(models.get("casino/example").blocks().getFirst().brightness());
    }

    @Test void malformedModelsIdentifyTheirFileAndRejectInvalidGeometry() {
        for (String invalid : new String[]{
                MODEL.replace("[2,1,3]", "[2,0,3]"),
                MODEL.replace("[0,0.5,0]", "[0,1e999,0]"),
                MODEL.replace("DARK_OAK_PLANKS", "MISSING_BLOCK"),
                MODEL.replace("DARK_OAK_PLANKS", "PAPER"),
                MODEL.replace("\"size\"", "\"szie\""),
                MODEL.replace("\"material\"", "\"brightness\":16,\"material\"")}) {
            var error = assertThrows(IllegalStateException.class, () -> read(invalid));
            assertTrue(error.getMessage().contains("example.json"), error.getMessage());
        }
    }

    @Test void invalidIndexEntriesCannotEscapeTheModelsDirectoryOrHideDuplicateIds() {
        for (String index : new String[]{
                "{\"casino/example\":\"../config.yml\"}",
                "{\"casino/example\":\"/example.json\"}",
                "{\"casino/example\":\"a.json\",\"casino/example\":\"b.json\"}"}) {
            assertThrows(IllegalStateException.class, () -> ModelRepository.read(path ->
                    input(path.endsWith("index.json") ? index : MODEL)));
        }
        assertThrows(IllegalStateException.class, () -> ModelRepository.read(path -> null));
        assertThrows(IllegalStateException.class, () -> ModelRepository.read(path ->
                path.endsWith("index.json") ? input("{\"casino/example\":\"missing.json\"}") : null));
    }

    @Test void tabletopModelNamespaceIsNotPartOfTheCasinoRepository() {
        assertThrows(IllegalStateException.class, () -> ModelRepository.read(path ->
                input(path.endsWith("index.json") ? "{\"tabletop/example\":\"example.json\"}" : MODEL)));
    }

    private static Map<String, ModelDefinition> read(String definition) {
        var resources = new HashMap<String,String>();
        resources.put("/vanilla-models/index.json", "{\"casino/example\":\"example.json\"}");
        resources.put("/vanilla-models/example.json", definition);
        return ModelRepository.read(path -> input(resources.get(path)));
    }

    private static ByteArrayInputStream input(String value) {
        return value == null ? null : new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
