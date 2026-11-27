package GoldenEggOVO.TDCasino.Core.platform;

import GoldenEggOVO.TDCasino.Core.menu.MenuView;
import GoldenEggOVO.TDCasino.Core.text.MessageText;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class NativeDialogs {
    private static final boolean PAPER = paper();
    private NativeDialogs() {}

    public static void open(Plugin plugin, Player player, MenuView view,
                            BiConsumer<String, Map<String, String>> dispatch) {
        if (PAPER) NativeDialogsPaper.open(player, view, dispatch);
        else NativeDialogsBukkit.open(plugin, player, view, dispatch);
    }

    public static void forget(Plugin plugin, Player player) {
        if (!PAPER) NativeDialogsBukkit.forget(plugin, player);
    }

    public static void clear(Plugin plugin, Player player) {
        if (PAPER) NativeDialogsPaper.clear(player);
        else {
            NativeDialogsBukkit.forget(plugin, player);
            PlatformMessages.nativeCall(player, "clearDialog");
        }
    }

    static void renderBody(MenuView view, Consumer<MenuView.Body> description, ObjIntConsumer<MenuView.Button> link) {
        int backIndex = -1;
        for (int i = 0; i < view.buttons().size(); i++) {
            var button = view.buttons().get(i);
            if (button.key().equals("back")) backIndex = i;
            else if (header(view, button)) link.accept(button, i);
        }
        for (var line : view.body())
            if (!MessageText.plain(line.text()).isBlank()) description.accept(line);
        if (backIndex >= 0) link.accept(view.buttons().get(backIndex), backIndex);
    }

    static boolean textLink(MenuView view, MenuView.Button button) {
        return button.key().equals("back") || header(view, button);
    }

    private static boolean header(MenuView view, MenuView.Button button) {
        return view.page().equals("casino") && button.key().equals("preferences");
    }

    private static boolean paper() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog", false, Player.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
