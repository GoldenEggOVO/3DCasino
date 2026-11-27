package GoldenEggOVO.TDCasino.render.model;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

class ModelCarrierItemsTest {
    @Test
    void sharedNamespaceResolvesTheFullModelNameAndItsPersistentTag() {
        MockBukkit.mock();
        try {
            var item = ModelCarrierItems.resolve("3dcasino:keno_tile_selected");
            assertEquals(Material.ORANGE_CONCRETE, item.getType());
            assertEquals("keno_tile_selected", ModelCarrierItems.modelName(item));
            assertEquals("keno_tile_selected", item.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey("3dcasino", "vanilla_model"), PersistentDataType.STRING));
        } finally {
            MockBukkit.unmock();
        }
    }
}
