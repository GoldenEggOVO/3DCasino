import GoldenEggOVO.TDCasino.Core.menu.DialogMenus;
import GoldenEggOVO.TDCasino.Core.menu.MenuView;
import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

final class CasinoMatrixDialogs {
    private CasinoMatrixDialogs() {}

    static void open(TDCasinoPlugin casino, Player player, Runnable next) {
        var form = new MenuView(Component.text("Casino matrix amount"), List.of(),
                List.of(new MenuView.Input("amount", Component.text("Amount"), "1.00", 16)),
                List.of(new MenuView.Button(Component.text("Save"), null, 150, "save"),
                        new MenuView.Button(Component.text("Back"), null, 150, "back")),
                new MenuView.Button(Component.text("Close"), null, 150, "close"), 1);
        DialogMenus.open(casino, player, form, (action, values) -> {
            if (!"save".equals(action) || !Map.of("amount", "12.34").equals(values)) {
                casino.getLogger().severe("CASINO_MATRIX_FAIL unexpected dialog response " + action + " " + values);
                return;
            }
            casino.getLogger().info("CASINO_MATRIX_DIALOG_PASS action=save amount=12.34");
            next.run();
        });
    }
}
