package GoldenEggOVO.TDCasino.render.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import GoldenEggOVO.TDCasino.Core.render.model.ModelRepository;

public final class ModelCarrierItems {
    private static final NamespacedKey MODEL_KEY = new NamespacedKey("3dcasino", "vanilla_model");

    public static ItemStack resolve(String reference) {
        if (reference.startsWith("material:"))
            return new ItemStack(Material.valueOf(reference.substring(9)));
        if (reference.startsWith("3dcasino:") && ModelRepository.get("casino/" + reference.substring("3dcasino:".length())) != null) {
            var item = new ItemStack(ModelCarrierMaterials.material(reference));
            String name = reference.substring("3dcasino:".length());
            var meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(MODEL_KEY, PersistentDataType.STRING, name);
            item.setItemMeta(meta);
            return item;
        }
        return new ItemStack(ModelCarrierMaterials.material(reference));
    }

    public static String modelName(ItemStack item) {
        return !item.hasItemMeta() ? null
                : item.getItemMeta().getPersistentDataContainer().get(MODEL_KEY, PersistentDataType.STRING);
    }

    private ModelCarrierItems() {}
}
