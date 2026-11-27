package GoldenEggOVO.TDCasino.Core.pack;

import GoldenEggOVO.TDCasino.Core.render.model.ModelRepository;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems.Definition;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;

import org.bukkit.Material;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

class CraftEngineModelsTest {
    @Test
    void sharedCatalogIncludesEveryCasinoModelWithoutDuplicateIdentifiers() throws Exception {
        for (String model : ModelRepository.ids("casino"))
            assertTrue(CraftEngineModels.IDS.contains(model), "Missing Casino model: " + model);
        assertEquals(CraftEngineModels.IDS.size(), CraftEngineModels.IDS.stream().distinct().count());
    }

    private CraftEngineModels models;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
        MockBukkit.createMockPlugin("CraftEngine");
        CraftEngineItems.REGISTRY.clear();
        CraftEngineItems.builds = 0;
        for (String id : CraftEngineModels.IDS)
            CraftEngineItems.REGISTRY.put("3dcasino:" + id, new Definition(Material.PAPER));
        models = new CraftEngineModels();
    }

    @AfterEach
    void cleanup() {
        CraftEngineItems.REGISTRY.clear();
        MockBukkit.unmock();
    }

    private void poll() throws Exception {
        var checked = CraftEngineModels.class.getDeclaredField("checked");
        checked.setAccessible(true);
        checked.setLong(models, 0);
    }

    @Test
    void unchangedRegistryReusesBuiltItemsAndReturnsIndependentCopies() throws Exception {
        assertTrue(models.ready());
        int initial = CraftEngineItems.builds;
        models.item("blackjack_machine").setAmount(12);
        assertEquals(1, models.item("blackjack_machine").getAmount());
        poll();
        assertTrue(models.ready());
        assertEquals(initial, CraftEngineItems.builds);
    }

    @Test
    void replacingOneDefinitionRebuildsOnlyItsItem() throws Exception {
        assertTrue(models.ready());
        int initial = CraftEngineItems.builds;
        CraftEngineItems.REGISTRY.put("3dcasino:blackjack_machine", new Definition(Material.BOOK));
        poll();
        assertTrue(models.ready());
        assertEquals(Material.BOOK, models.item("blackjack_machine").getType());
        assertEquals(initial + 1, CraftEngineItems.builds);
    }

    @Test
    void incompleteRegistryFailsClosedAndRecoversOnTheNextPoll() throws Exception {
        assertTrue(models.ready());
        CraftEngineItems.REGISTRY.remove("3dcasino:blackjack_machine");
        poll();
        assertFalse(models.ready());
        assertThrows(IllegalStateException.class, () -> models.item("blackjack_machine"));
        CraftEngineItems.REGISTRY.put("3dcasino:blackjack_machine", new Definition(Material.BOOK));
        poll();
        assertTrue(models.ready());
        assertEquals(Material.BOOK, models.item("blackjack_machine").getType());
    }
}
