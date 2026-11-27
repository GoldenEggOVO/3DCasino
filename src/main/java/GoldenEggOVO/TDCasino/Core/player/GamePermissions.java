package GoldenEggOVO.TDCasino.Core.player;

import org.bukkit.entity.Player;

public final class GamePermissions {
    private GamePermissions() {}

    public static boolean use(Player player) { return player.hasPermission("3dcasino.use"); }
    public static boolean admin(Player player) { return player.hasPermission("3dcasino.admin"); }
}
