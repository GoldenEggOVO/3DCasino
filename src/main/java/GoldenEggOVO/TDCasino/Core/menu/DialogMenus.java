package GoldenEggOVO.TDCasino.Core.menu;

import GoldenEggOVO.TDCasino.Core.platform.NativeDialogs;
import GoldenEggOVO.TDCasino.Core.platform.PlatformMessages;
import GoldenEggOVO.TDCasino.Core.platform.PlatformScheduler;
import GoldenEggOVO.TDCasino.Core.text.Language;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public final class DialogMenus {
    private DialogMenus() {}

    public static boolean open(Plugin plugin, Player player, MenuView view,
                               BiConsumer<String, Map<String, String>> handler) {
        if (!plugin.getConfig().getBoolean("menu-enabled", true)) {
            PlatformMessages.send(player, Language.component("casino.menu.disabled"));
            return false;
        }
        var world = player.getWorld().getUID();
        var used = new AtomicBoolean();
        NativeDialogs.open(plugin, player, view, (action, values) -> {
            if (!plugin.isEnabled() || !used.compareAndSet(false, true)) return;
            new PlatformScheduler(plugin).later(player, () -> {
                if (plugin.isEnabled() && player.isOnline() && world.equals(player.getWorld().getUID())
                        && plugin.getConfig().getBoolean("menu-enabled", true)
                        && System.currentTimeMillis() < view.expiresAt() && action != null)
                    handler.accept(action, values);
            }, 1);
        });
        return true;
    }
}
