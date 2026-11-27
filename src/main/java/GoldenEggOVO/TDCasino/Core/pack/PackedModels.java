package GoldenEggOVO.TDCasino.Core.pack;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class PackedModels {
    private final Map<String, NamespacedKey> models = load();

    boolean ready() {
        return models.size() == CraftEngineModels.IDS.size();
    }

    ItemStack item(String id) {
        NamespacedKey model = models.get(id);
        if (model == null) throw new IllegalArgumentException("Unknown casino model: " + id);
        ItemStack item = new ItemStack(Material.PAPER);
        var meta = item.getItemMeta();
        meta.setItemModel(model);
        item.setItemMeta(meta);
        return item;
    }

    private static Map<String, NamespacedKey> load() {
        Map<String, NamespacedKey> models = new HashMap<>();
        String path = "/craftengine/resources/3dcasino/configuration/casino/items.yml";
        try (var input = PackedModels.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing packed model catalog: " + path);
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            var items = yaml.getConfigurationSection("items");
            for (String key : items.getKeys(false)) {
                var model = NamespacedKey.fromString(items.getString(key + ".item_model"));
                if (model == null || !key.startsWith("3dcasino:")
                        || models.putIfAbsent(key.substring("3dcasino:".length()), model) != null)
                    throw new IllegalStateException("Invalid packed model registration: " + key);
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load packed model catalog", exception);
        }
        if (!models.keySet().equals(java.util.Set.copyOf(CraftEngineModels.IDS)))
            throw new IllegalStateException("Packed model catalog does not match runtime models");
        return Map.copyOf(models);
    }
}
