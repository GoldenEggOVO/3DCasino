import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.game.GameRound;
import GoldenEggOVO.TDCasino.game.blackjack.BlackjackRound;
import GoldenEggOVO.TDCasino.machine.GameMachine;
import GoldenEggOVO.TDCasino.machine.definition.ButtonDefinition;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.nio.file.*;
import java.util.*;

public final class PackLayerProbe extends JavaPlugin implements Listener {
    private TDCasinoPlugin casino;
    private Player first, second;
    private int facing;
    private GameMachine<?> machine;
    private Location center;
    private final Set<UUID> pastWindow = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean declinedInWindow;
    private final boolean early = Boolean.getBoolean("packprobe.early");

    @Override
    public void onEnable() {
        if (!Bukkit.getIp().equals("127.0.0.1")
                || Bukkit.getPort() < 26910
                || Bukkit.getPort() > 26912)
            throw new IllegalStateException("Dedicated loopback fixture required");
        casino = (TDCasinoPlugin) Bukkit.getPluginManager().getPlugin("3DCasino");
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!Set.of("PackFirst", "PackSecond").contains(p.getName())) return;
        p.addAttachment(this, "3dcasino.admin", true);
        p.setGameMode(GameMode.CREATIVE);
        p.setAllowFlight(true);
        p.setFlying(true);
        synchronized (casino) {
            if (early) {
                casino.scheduler().later(p, () -> casino.pack().toggle(p), 1);
                casino.scheduler().later(p, () -> pastWindow.add(p.getUniqueId()), 19);
            }
            if (p.getName().equals("PackFirst")) first = p;
            else second = p;
            if (first != null && second != null)
                later(
                        35,
                        () -> {
                            center = new Location(first.getWorld(), 8.5, 84, 8.5);
                            move(
                                    first,
                                    center,
                                    () ->
                                            move(
                                                    second,
                                                    center,
                                                    () ->
                                                            later(
                                                                    5,
                                                                    () -> {
                                                                        if (!early) {
                                                                            casino.pack()
                                                                                    .toggle(first);
                                                                            casino.pack()
                                                                                    .toggle(second);
                                                                        }
                                                                        later(32, this::place);
                                                                    })));
                        });
        }
    }

    @EventHandler
    public void status(PlayerResourcePackStatusEvent event) {
        if (early
                && event.getPlayer().getName().equals("PackSecond")
                && event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED) {
            declinedInWindow = !pastWindow.contains(event.getPlayer().getUniqueId());
            getLogger().info("PACK_LAYER_EARLY_DECLINE before_join_tick_19=" + declinedInWindow);
        }
    }

    private void move(Player p, Location loc, Runnable done) {
        casino.scheduler()
                .teleport(
                        p,
                        loc,
                        ok ->
                                guarded(
                                        () -> {
                                            check(ok, "fixture teleport");
                                            done.run();
                                        }));
    }

    private void later(int ticks, Runnable task) {
        casino.scheduler().later(first, () -> guarded(task), ticks);
    }

    private void guarded(Runnable work) {
        try {
            work.run();
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "PACK_LAYER_FAIL", failure);
        }
    }

    private static void check(boolean condition, String issue) {
        if (!condition) throw new AssertionError(issue);
    }

    private void place() {
        check(!early || declinedInWindow, "decline arrived before delayed join task");
        check(
                casino.pack().packed(first) && !casino.pack().packed(second),
                "two different resource backends");
        var start = center.clone();
        start.setYaw(facing * 90);
        start.subtract(start.getDirection().multiply(4));
        move(
                first,
                start,
                () ->
                        later(
                                3,
                                () -> {
                                    casino.casino()
                                            .machines()
                                            .command(first, new String[] {"create", "blackjack"});
                                    machine = casino.casino().machines().machines().getFirst();
                                    check(
                                            machine.origin().distanceSquared(center) < .001,
                                            "machine placed at aimed position");
                                    later(
                                            16,
                                            () -> {
                                                mark("facing" + facing, false);
                                                if (facing < 3) {
                                                    casino.casino().machines().remove(machine);
                                                    facing++;
                                                    later(5, this::place);
                                                } else {
                                                    var round =
                                                            (BlackjackRound)
                                                                    field(
                                                                            machine,
                                                                            GameMachine.class,
                                                                            "round");
                                                    try {
                                                        var random =
                                                                GameRound.class.getDeclaredField(
                                                                        "random");
                                                        random.setAccessible(true);
                                                        random.set(round, new Random(4));
                                                    } catch (ReflectiveOperationException e) {
                                                        throw new AssertionError(e);
                                                    }
                                                    click(
                                                            "start",
                                                            () -> {
                                                                check(
                                                                        round.active()
                                                                                && round.player()
                                                                                                .size()
                                                                                        == 2
                                                                                && round.dealer()
                                                                                                .size()
                                                                                        == 2,
                                                                        "real opening hand");
                                                                mark("cards", true);
                                                                casino.pack().toggle(first);
                                                                casino.pack().toggle(second);
                                                                later(
                                                                        32,
                                                                        () -> {
                                                                            check(
                                                                                    !casino.pack()
                                                                                                    .packed(
                                                                                                            first)
                                                                                            && casino.pack()
                                                                                                    .packed(
                                                                                                            second),
                                                                                    "swap player"
                                                                                        + " backends");
                                                                            mark("swap", true);
                                                                            click(
                                                                                    "stand",
                                                                                    () -> {
                                                                                        check(
                                                                                                round
                                                                                                        .finished(),
                                                                                                "stand"
                                                                                                    + " from"
                                                                                                    + " native"
                                                                                                    + " viewer");
                                                                                        mark(
                                                                                                "finish",
                                                                                                true);
                                                                                        later(
                                                                                                5,
                                                                                                () ->
                                                                                                        getLogger()
                                                                                                                .info(
                                                                                                                        "PACK_LAYER_PASS"));
                                                                                    });
                                                                        });
                                                            });
                                                }
                                            });
                                }));
    }

    private static Object field(Object o, Class<?> type, String name) {
        try {
            var f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Entity> parts() {
        return (List<Entity>) field(machine, GameMachine.class, "parts");
    }

    @SuppressWarnings("unchecked")
    private Interaction control(String name) {
        var actions = (Map<ItemDisplay, String>) field(machine, GameMachine.class, "buttonActions");
        var buttons =
                (Map<ItemDisplay, ButtonDefinition>) field(machine, GameMachine.class, "buttons");
        var visible =
                actions.entrySet().stream()
                        .filter(e -> e.getValue().equals(name))
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        var b = buttons.get(visible);
        var local =
                new Vector3f(0, (float) (b.height() / 2), (float) ((b.depth() - b.press()) / 2));
        b.transform().rotation().transform(local).mul((float) b.transform().scale());
        var pos = visible.getLocation();
        var pt =
                pos.clone()
                        .add(
                                new Vector(local.x, local.y, local.z)
                                        .rotateAroundY(-Math.toRadians(pos.getYaw())));
        var hit =
                parts().stream()
                        .filter(Interaction.class::isInstance)
                        .map(Interaction.class::cast)
                        .min(
                                Comparator.comparingDouble(
                                        e ->
                                                e.getBoundingBox()
                                                        .getCenter()
                                                        .distanceSquared(pt.toVector())))
                        .orElseThrow();
        check(
                hit.getBoundingBox().getCenter().distanceSquared(pt.toVector()) < .00002,
                "button center " + name);
        return hit;
    }

    private void click(String action, Runnable done) {
        Interaction hit = control(action);
        var at = hit.getBoundingBox().getCenter().toLocation(center.getWorld());
        var eye = at.clone().add(machine.origin().getDirection().multiply(.35)).add(0, .35, 0);
        eye.setDirection(at.toVector().subtract(eye.toVector()));
        move(
                first,
                eye.subtract(0, first.getEyeHeight(), 0),
                () ->
                        later(
                                4,
                                () -> {
                                    getLogger().info("PACK_LAYER_CLICK id=" + hit.getEntityId());
                                    later(32, done);
                                }));
    }

    @SuppressWarnings("unchecked")
    private void mark(String phase, boolean cards) {
        var audience = field(machine, GameMachine.class, "audience");
        var layers = (Map<Entity, Boolean>) field(audience, audience.getClass(), "layers");
        Map<Entity, String> nativeTiles = new HashMap<>();
        for (Object model : (List<?>) field(machine, GameMachine.class, "modelDisplays")) {
            String name = (String) field(model, model.getClass(), "name");
            if (name == null || !name.startsWith("card_") || name.equals("card_52")) continue;
            int card = Integer.parseInt(name.substring(5));
            String face =
                    List.of("spades", "hearts", "clubs", "diamonds").get(card / 13)
                            + "_"
                            + List.of(
                                            "ace", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                                            "jack", "queen", "king")
                                    .get(card % 13);
            List<?> children = (List<?>) field(model, model.getClass(), "children");
            check(children.size() == 24, "expected a 4 by 6 native card");
            for (int tile = 0; tile < children.size(); tile++) {
                var child = children.get(tile);
                nativeTiles.put(
                        (Entity) field(child, child.getClass(), "display"),
                        "head/" + face + "/" + tile);
            }
        }
        List<String> rows = new ArrayList<>();
        int models = 0, blocks = 0, heads = 0, cardItems = 0;
        for (var entry : layers.entrySet()) {
            var e = entry.getKey();
            var layer = entry.getValue();
            if (layer == null || !e.isValid()) continue;
            var display = (Display) e;
            check(
                    Math.abs(
                                    Math.IEEEremainder(
                                            display.getLocation().getYaw() - (facing * 90 + 180),
                                            360))
                            < .001,
                    "display yaw");
            String item = "-";
            if (display instanceof ItemDisplay id) {
                var stack = id.getItemStack();
                if (stack.hasItemMeta() && stack.getItemMeta().hasItemModel())
                    item = stack.getItemMeta().getItemModel().toString();
                if (layer && item.contains("/cards/poker_")) cardItems++;
                if (!layer && stack.getType() == Material.PLAYER_HEAD) {
                    item = Objects.requireNonNull(nativeTiles.get(e));
                    heads++;
                }
            }
            if (layer) {
                check(item.startsWith("3dcasino:"), "packed model key: " + item);
                models++;
            } else if (display instanceof BlockDisplay) blocks++;
            check(
                    first.canSee(e) == (casino.pack().packed(first) == layer),
                    "first visibility " + e.getEntityId());
            check(
                    second.canSee(e) == (casino.pack().packed(second) == layer),
                    "second visibility " + e.getEntityId());
            rows.add((layer ? "P" : "N") + " " + e.getEntityId() + " " + item);
        }
        for (String action : List.of("start", "hit", "stand", "double"))
            rows.add("H " + control(action).getEntityId() + " " + action);
        check(
                models > 0 && blocks > 0 && (!cards || heads >= 72 && cardItems >= 4),
                "body and card layers: " + heads + "/" + cardItems);
        try {
            var folder = getDataFolder().toPath();
            Files.createDirectories(folder);
            Files.write(folder.resolve(phase + ".txt"), rows);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        first.sendMessage("PACK_TEST_MARK/" + phase);
        second.sendMessage("PACK_TEST_MARK/" + phase);
        getLogger()
                .info(
                        "PACK_LAYER_SAMPLE phase="
                                + phase
                                + " nativeBlocks="
                                + blocks
                                + " packedModels="
                                + models
                                + " nativeHeads="
                                + heads
                                + " packedCards="
                                + cardItems);
    }
}
