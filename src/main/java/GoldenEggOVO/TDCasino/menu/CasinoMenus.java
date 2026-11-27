package GoldenEggOVO.TDCasino.menu;

import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.CasinoModule;
import GoldenEggOVO.TDCasino.machine.GameMachine;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import GoldenEggOVO.TDCasino.text.CasinoMessages;
import GoldenEggOVO.TDCasino.Core.menu.MenuView;
import GoldenEggOVO.TDCasino.Core.menu.DialogMenus;
import GoldenEggOVO.TDCasino.Core.platform.NativeDialogs;
import GoldenEggOVO.TDCasino.Core.platform.PlatformMessages;
import GoldenEggOVO.TDCasino.Core.text.MessageText;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiConsumer;

public final class CasinoMenus implements AutoCloseable {
    private final TDCasinoPlugin plugin;
    private final CasinoModule module;
    private final Map<UUID, Session> sessions = new HashMap<>();

    private static final class Session {
        final Player player;
        final boolean preferences;
        boolean closing;
        Session(Player player, boolean preferences) {
            this.player = player;
            this.preferences = preferences;
        }
    }

    public CasinoMenus(TDCasinoPlugin plugin, CasinoModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public void main(Player player) {
        var buttons = new ArrayList<>(MachineDefinition.games().stream().sorted().map(game ->
                new MenuView.Button(CasinoMessages.component("games." + game), CasinoMessages.component("menu.play." + game),
                        174, "game:" + game)).toList());
        buttons.add(button(CasinoMessages.component("menu.settings"), "preferences"));
        open(player, new MenuView(CasinoMessages.component("menu.title"),
                List.of(), List.of(), buttons,
                closeButton(350), 2, "casino"), (action, values) -> {
                    if (action.equals("preferences")) preferences(player);
                    else if (action.startsWith("game:")) game(player, action.substring(5));
                });
    }

    public void preferences(Player player) {
        var buttons = new ArrayList<MenuView.Button>();
        if (plugin.pack().hasToggle()) buttons.add(new MenuView.Button(plugin.pack().button(player), null, 330, "pack"));
        buttons.add(backButton());
        open(player, new MenuView(CasinoMessages.component("menu.settings"), List.of(), List.of(), buttons,
                closeButton(330), 1, "settings"), (action, values) -> {
                    if (action.equals("back")) main(player);
                    else if (action.equals("pack")) {
                        plugin.pack().toggle(player);
                        preferences(player);
                    }
                });
    }

    public void refresh(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.closing) return;
        if (session.preferences) preferences(player);
        else main(player);
    }

    private void game(Player player, String game) {
        if (!MachineDefinition.games().contains(game)) return;
        var buttons = new ArrayList<MenuView.Button>();
        var nearby = module.machines().machines().stream()
                .filter(machine -> machine.game().equals(game) && module.machines().canUse(player, machine) && machine.nearSettings(player)).toList();
        for (int index = 0; index < nearby.size(); index++)
            buttons.add(button(CasinoMessages.component("menu.machine-entry", "number", index + 1), "machine:" + index));
        if (GoldenEggOVO.TDCasino.Core.player.GamePermissions.admin(player))
            buttons.add(button(CasinoMessages.component("menu.place"), "create"));
        var description = CasinoMessages.component("menu.play." + game);
        if (nearby.isEmpty()) description = description.append(Component.newline()).append(CasinoMessages.component("menu.none-nearby"));
        buttons.add(backButton());
        open(player, new MenuView(CasinoMessages.component("games." + game), List.of(body(description)), List.of(), buttons,
                closeButton(330), 1), (action, values) -> {
                    if (action.equals("back")) main(player);
                    else if (action.equals("create") && GoldenEggOVO.TDCasino.Core.player.GamePermissions.admin(player)) {
                        module.machines().command(player, new String[] {"create", game});
                        game(player, game);
                    } else if (action.startsWith("machine:")) {
                        int index = Integer.parseInt(action.substring(8));
                        if (index >= 0 && index < nearby.size()) settings(player, nearby.get(index));
                    }
                });
    }

    public void settings(Player player, GameMachine<?> machine) {
        if (!live(player, machine)) return;
        var buttons = new ArrayList<MenuView.Button>();
        if (module.canConfigureStake()) buttons.add(button(CasinoMessages.component("menu.amount"), "amount"));
        if (module.machines().canManage(player, machine))
            buttons.add(button(CasinoMessages.component("menu.remove"), "remove"));
        buttons.add(backButton());
        open(player, new MenuView(CasinoMessages.component("games." + machine.game()),
                List.of(body(CasinoMessages.component("menu.personal-amount", "amount", module.formatCents(machine.stake(player)))),
                        body(CasinoMessages.component("menu.controls"))), List.of(), buttons,
                closeButton(330), 1), (action, values) -> {
                    if (!live(player, machine)) return;
                    if (action.equals("amount") && module.canConfigureStake()) amount(player, machine);
                    else if (action.equals("remove") && module.machines().canManage(player, machine)) remove(player, machine);
                    else if (action.equals("back")) game(player, machine.game());
                });
    }

    private void amount(Player player, GameMachine<?> machine) {
        if (!live(player, machine) || !module.canConfigureStake()) return;
        long shown = machine.stake(player);
        open(player, new MenuView(CasinoMessages.component("menu.amount"),
                List.of(body(CasinoMessages.component("menu.amount-help", "minimum", module.formatCents(module.stakeLimitCents("minimum", 1)),
                        "maximum", module.formatCents(module.stakeLimitCents("maximum", 1000))))),
                List.of(new MenuView.Input("amount", CasinoMessages.component("menu.amount"), module.formatCents(shown), 16)),
                List.of(button(CasinoMessages.component("menu.save"), "save"), backButton()), closeButton(330), 1),
                (action, values) -> {
                    if (!live(player, machine)) return;
                    if (action.equals("save")) {
                        if (machine.stake(player) != shown) throw new CasinoMessages.Failure("error.expired");
                        String text = values.getOrDefault("amount", "").trim();
                        if (!text.matches("[0-9]{1,8}(\\.[0-9]{1,2})?")) throw new CasinoMessages.Failure("error.amount");
                        long cents = new BigDecimal(text).movePointRight(2).longValueExact();
                        module.validateStakeCents(cents);
                        machine.setStake(player, cents);
                        PlatformMessages.send(player, CasinoMessages.component("machine.stake-saved", "amount", module.formatCents(cents)));
                    }
                    settings(player, machine);
                });
    }

    private void remove(Player player, GameMachine<?> machine) {
        open(player, new MenuView(CasinoMessages.component("menu.remove"), List.of(body(CasinoMessages.component("menu.remove-confirm"))),
                List.of(), List.of(button(CasinoMessages.component("menu.remove"), "confirm"), backButton()),
                closeButton(330), 1), (action, values) -> {
                    if (!live(player, machine)) return;
                    if (action.equals("confirm") && module.machines().canManage(player, machine))
                        module.machines().remove(machine);
                    else settings(player, machine);
                });
    }

    private boolean live(Player player, GameMachine<?> machine) {
        return plugin.allowed(player) && module.machines().contains(machine) && module.machines().canUse(player, machine) && machine.nearSettings(player);
    }

    private void open(Player player, MenuView view, BiConsumer<String, Map<String, String>> handler) {
        if (!plugin.allowed(player)) return;
        UUID id = player.getUniqueId();
        var session = new Session(player, view.page().equals("settings"));
        boolean shown = DialogMenus.open(plugin, player, view, (action, values) -> {
            if (session.closing || !sessions.remove(id, session) || action.equals("close") || !plugin.allowed(player)) return;
            try { handler.accept(action, values); }
            catch (IllegalArgumentException exception) { PlatformMessages.send(player, CasinoMessages.component("machine.failed", "error", CasinoMessages.error(exception))); }
        });
        if (shown) sessions.put(id, session);
    }

    public void closeOpenMenus() {
        var open = sessions.values().stream().filter(session -> !session.closing).toList();
        open.forEach(session -> session.closing = true);
        for (Session session : open) {
            Player player = session.player;
            if (!player.isOnline()) {
                forget(player);
                continue;
            }
            plugin.scheduler().execute(player, () -> {
                if (sessions.remove(player.getUniqueId(), session) && player.isOnline())
                    NativeDialogs.clear(plugin, player);
            });
        }
    }

    private static MenuView.Body body(Component text) { return new MenuView.Body(text, 350); }
    private static MenuView.Button button(Component text, String action) {
        String style = switch (action) {
            case "save", "create", "amount" -> "<green><bold>{label}</bold>";
            case "remove", "confirm" -> "<red>{label}";
            case "back" -> "<yellow>{label}";
            default -> "<white>{label}";
        };
        return new MenuView.Button(MessageText.render(style, "label", text), null, 330, action);
    }
    private static MenuView.Button backButton() { return button(CasinoMessages.component("menu.back"), "back"); }
    private static MenuView.Button closeButton(int width) {
        return new MenuView.Button(MessageText.render("<gray>[ {label} ]", "label", CasinoMessages.component("menu.close")), null, width, "close");
    }
    public void forget(Player player) {
        sessions.remove(player.getUniqueId());
        NativeDialogs.forget(plugin, player);
    }

    @Override public void close() { sessions.clear(); }
}
