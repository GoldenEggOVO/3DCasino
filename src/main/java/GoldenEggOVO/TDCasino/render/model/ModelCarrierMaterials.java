package GoldenEggOVO.TDCasino.render.model;

import org.bukkit.Material;
public final class ModelCarrierMaterials {
    public static Material material(String reference) {
        String name = reference.substring(reference.indexOf(':') + 1);
        if (name.startsWith("card_")) return Material.PAPER;
        if (name.startsWith("casino_button_") || name.startsWith("duck_race_button_")
                || name.startsWith("money_wheel_button_"))
            return Material.STONE_BUTTON;
        if (name.startsWith("duck_race_")) return Material.EGG;
        return switch (name) {
            case "mine_hidden" -> Material.POLISHED_DEEPSLATE;
            case "mine_gem" -> Material.EMERALD_BLOCK;
            case "mine_bomb" -> Material.TNT;
            case "plinko_ball" -> Material.SLIME_BALL;
            case "crash_rocket" -> Material.FIREWORK_ROCKET;
            case "keno_tile" -> Material.BROWN_CONCRETE;
            case "keno_tile_selected" -> Material.ORANGE_CONCRETE;
            case "wheel_of_fortune_disc", "money_wheel_disc" -> Material.CLOCK;
            case "penguin_cross_penguin" -> Material.BLACK_CONCRETE;
            case "hilo_number_panel" -> Material.GRAY_CONCRETE;
            case "hilo_slider" -> Material.REDSTONE_TORCH;
            default -> Material.PAPER;
        };
    }

    private ModelCarrierMaterials() {}
}
