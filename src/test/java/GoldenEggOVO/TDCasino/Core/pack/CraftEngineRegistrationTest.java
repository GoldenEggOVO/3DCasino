package GoldenEggOVO.TDCasino.Core.pack;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class CraftEngineRegistrationTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path folder;

    @Test void casinoInstallsIndependentlyAndNeverTouchesCompanionRegistrations() throws Exception {
        Path companion = folder.resolve("resources/3dgames/configuration/tabletop/items.yml");
        Files.createDirectories(companion.getParent());
        String original = "items: {3dgames:custom: {material: stone}}";
        Files.writeString(companion, original);
        assertEquals(2, CraftEngineRegistration.install(folder));
        Path registration = folder.resolve("resources/3dcasino/configuration/casino/items.yml");
        var items = YamlConfiguration.loadConfiguration(registration.toFile()).getConfigurationSection("items");
        assertEquals(CraftEngineModels.IDS.stream().map(id -> "3dcasino:" + id).collect(Collectors.toSet()), items.getKeys(false));
        assertEquals(105, items.getKeys(false).size());
        assertFalse(Files.exists(folder.resolve("resources/3dcasino/configuration/tabletop")));
        assertEquals(original, Files.readString(companion));
        byte[] installed = Files.readAllBytes(registration);
        assertEquals(0, CraftEngineRegistration.install(folder));
        assertArrayEquals(installed, Files.readAllBytes(registration));
    }

    @Test void customizedCasinoModelsRemainUntouched() throws Exception {
        Path registration = folder.resolve("resources/3dcasino/configuration/casino/items.yml");
        Files.createDirectories(registration.getParent());
        Files.writeString(registration, "items:\n  3dcasino:blackjack_machine:\n    material: stick\n");
        CraftEngineRegistration.install(folder);
        var yaml = YamlConfiguration.loadConfiguration(registration.toFile());
        assertEquals("stick", yaml.getString("items.3dcasino:blackjack_machine.material"));
        assertEquals(105, yaml.getConfigurationSection("items").getKeys(false).size());
    }
}
