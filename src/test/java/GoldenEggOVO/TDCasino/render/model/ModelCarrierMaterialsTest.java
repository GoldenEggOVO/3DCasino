package GoldenEggOVO.TDCasino.render.model;

import GoldenEggOVO.TDCasino.Core.render.model.ModelRepository;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ModelCarrierMaterialsTest {
    @Test
    void allGameplayVisualsHaveVisibleVanillaMaterials() {
        assertEquals(Material.PAPER, ModelCarrierMaterials.material("3dcasino:card_51"));
        assertEquals(Material.ORANGE_CONCRETE, ModelCarrierMaterials.material("3dcasino:keno_tile_selected"));
        assertEquals(Material.BROWN_CONCRETE, ModelCarrierMaterials.material("3dcasino:keno_tile"));
        assertEquals(Material.SLIME_BALL, ModelCarrierMaterials.material("3dcasino:plinko_ball"));
        assertEquals(Material.CLOCK, ModelCarrierMaterials.material("3dcasino:wheel_of_fortune_disc"));
        assertEquals(Material.STONE_BUTTON, ModelCarrierMaterials.material("3dcasino:casino_button_play"));
        assertEquals(Material.STONE_BUTTON, ModelCarrierMaterials.material("3dcasino:duck_race_button_1"));
        assertEquals(Material.STONE_BUTTON, ModelCarrierMaterials.material("3dcasino:money_wheel_button_10x"));
        assertEquals(Material.EGG, ModelCarrierMaterials.material("3dcasino:duck_race_machine"));
        assertEquals(Material.PAPER, ModelCarrierMaterials.material("other:unknown_skin"));
    }

    @Test
    void gameplayModelsUseOriginalGeometryRatherThanShrunkenPlaceholderItems() {
        for (String name : java.util.List.of("keno_tile", "wheel_of_fortune_disc",
                "duck_race_duck_1", "penguin_cross_penguin", "crash_rocket", "hilo_number_panel"))
            assertNotNull(ModelRepository.get("casino/" + name), name);
    }
}
