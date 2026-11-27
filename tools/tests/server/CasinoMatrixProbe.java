import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.Core.economy.EconomyAccess;
import GoldenEggOVO.TDCasino.game.GameRound;
import GoldenEggOVO.TDCasino.game.blackjack.BlackjackRound;
import GoldenEggOVO.TDCasino.game.money_wheel.MoneyWheelRound;
import GoldenEggOVO.TDCasino.game.penguin_cross.PenguinCrossRound;
import GoldenEggOVO.TDCasino.machine.GameMachine;
import GoldenEggOVO.TDCasino.machine.MachineResultState;
import GoldenEggOVO.TDCasino.machine.definition.ButtonDefinition;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;

/** Test plugin for an isolated loopback server. Its player is an actual protocol connection. */
public final class CasinoMatrixProbe extends JavaPlugin implements Listener {
    private final List<String> games = MachineDefinition.games().stream().sorted().toList();
    private final boolean restore = "restore".equals(System.getProperty("casino.matrix.stage"));
    private TDCasinoPlugin casino;
    private Player player;
    private int placements, displays, buttons, hits, settlements;
    private boolean separateRegions;
    private int debits, credits;
    private long balance = 10_000, expectedBalance = balance;

    @Override public void onEnable() {
        if (!Bukkit.getIp().equals("127.0.0.1") || Bukkit.getPort() < 26880 || Bukkit.getPort() > 26899)
            throw new IllegalStateException("CasinoMatrixProbe requires its own loopback fixture");
        casino = (TDCasinoPlugin) Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("3DCasino"));
        casino.getConfig().set("economy.enabled", true);
        setField(casino.integrations(), casino.integrations().getClass(), "economy", new EconomyAccess() {
            public boolean available() { return true; }
            public OptionalDouble balance(UUID id) { return OptionalDouble.of(balance / 100d); }
            public String format(double value) { return Double.toString(value); }
            public Transfer withdraw(UUID id, double value) {
                require(player.getUniqueId().equals(id) && casino.scheduler().isOwned(player), "debit owner");
                balance -= Math.round(value * 100);
                debits++;
                return Transfer.SUCCESS;
            }
            public Transfer deposit(UUID id, double value) {
                require(player.getUniqueId().equals(id) && casino.scheduler().isOwned(player), "credit owner");
                balance += Math.round(value * 100);
                credits++;
                return Transfer.SUCCESS;
            }
        });
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler public void joined(PlayerJoinEvent event) {
        if (!event.getPlayer().getName().equals("CasinoMatrix")) return;
        player = event.getPlayer();
        guard(() -> {
            player.addAttachment(this, "3dcasino.admin", true);
            player.setGameMode(GameMode.CREATIVE);
            player.setAllowFlight(true);
            player.setFlying(true);
            World world = player.getWorld();
            casino.scheduler().execute(() -> guard(() -> {
                for (int i = 0; i < games.size(); i++) world.setChunkForceLoaded(chunkX(i), 0, true);
                later(4, () -> CasinoMatrixDialogs.open(casino, player, () -> later(1, () -> visit(0, 0))));
            }));
        });
    }

    private void visit(int game, int facing) {
        if (game == games.size()) {
            require(casino.casino().machines().machines().size() == 12, "twelve live placements");
            require(!casino.scheduler().folia() || separateRegions, "Folia regions are independent");
            require(settlements == 2, "both persisted game results");
            require(restore ? debits == 0 && credits == 0 : debits == 2 && credits >= 1 && balance == expectedBalance,
                    "provider debits and credits match rounds");
            getLogger().info("CASINO_MATRIX_PASS stage=" + (restore ? "restore" : "create")
                    + " placements=" + placements + " displays=" + displays + " buttons=" + buttons
                    + " packet_clicks=" + hits + " settlements=" + settlements + " distant_regions="
                    + separateRegions + " test_economy_debits=" + debits + " credits=" + credits + " server=" + Bukkit.getVersion());
            return;
        }
        var center = new Location(player.getWorld(), chunkX(game) * 16 + 8.5, 83, 8.5);
        float yaw = Math.floorMod(game + (restore ? 3 : facing), 4) * 90f;
        var placement = center.clone();
        placement.setYaw(yaw);
        placement.subtract(placement.getDirection().multiply(4));
        move(placement, () -> {
            if (casino.scheduler().folia() && game >= 6)
                separateRegions |= !casino.scheduler().isOwned(new Location(center.getWorld(), 8.5, 83, 8.5));
            String name = games.get(game);
            if (!restore) casino.casino().machines().command(player, new String[]{"create", name});
            var machine = casino.casino().machines().machines().stream().filter(m -> m.game().equals(name))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing " + name));
            require(machine.owner().equals(player.getUniqueId()), "persisted owner " + name);
            require(machine.origin().distanceSquared(center) < 1e-8, "placement origin " + name);
            inspect(machine, yaw + 180);
            placements++;
            if (!restore && facing < 3) {
                var entities = List.copyOf(parts(machine));
                casino.casino().machines().remove(machine);
                require(entities.stream().noneMatch(Entity::isValid), "removed displays " + name);
                visit(game, facing + 1);
                return;
            }
            Runnable next = () -> visit(game + 1, 0);
            if (!restore && name.equals("penguin_cross")) {
                var round = (PenguinCrossRound) field(machine, "round");
                setField(round, GameRound.class, "random", new Random(0)); // first step is safe
                packetClick(machine, "play", () -> {
                    require(round.active(), "penguin play button starts the round");
                    packetClick(machine, "step", () -> later(20, () -> {
                        require(round.steps() == 1, "penguin advances one floe");
                        packetClick(machine, "cash", () -> {
                            require(round.finished() && !round.active(), "penguin cash button finishes the round");
                            next.run();
                        });
                    }));
                });
            } else if (!restore && name.equals("wheel_of_fortune")) {
                setField(field(machine, "round"), GameRound.class, "random", new Random(6));
                spinAndCheckDisc(machine, () -> later(85, next));
            } else if (!Set.of("blackjack", "money_wheel").contains(name)) {
                next.run();
            } else if (restore) {
                verifyResult(machine, true);
                next.run();
            } else if (name.equals("blackjack")) {
                machine.setStake(player, 125);
                packetClick(machine, "start", () -> {
                    var round = (BlackjackRound) field(machine, "round");
                    require(round.sequence() == 1 && round.player().size() == 2 && round.dealer().size() >= 2,
                            "blackjack dealt real cards");
                    Runnable finished = () -> later(32, () -> { verifyResult(machine, false); next.run(); });
                    if (round.active()) packetClick(machine, "stand", finished);
                    else finished.run();
                });
            } else {
                machine.setStake(player, 125);
                // A reproducible winning wedge exercises positive credits on every server.
                setField(field(machine, "round"), GameRound.class, "random", new Random(6));
                packetClick(machine, "select:2", () -> {
                    require(((MoneyWheelRound) field(machine, "round")).choice() == 2, "wheel selection");
                    spinAndCheckDisc(machine, () -> {
                        require(((GameRound) field(machine, "round")).sequence() == 1, "wheel started");
                        later(85, () -> { verifyResult(machine, false); next.run(); });
                    });
                });
            }
        });
    }

    private static int chunkX(int game) { return game % 6 + (game >= 6 ? 512 : 0); }

    private void spinAndCheckDisc(GameMachine<?> machine, Runnable next) {
        var discMaterial = machine.game().equals("money_wheel") ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE;
        var firstWedge = parts(machine).stream().filter(BlockDisplay.class::isInstance).map(BlockDisplay.class::cast)
                .filter(block -> block.getBlock().getMaterial() == discMaterial)
                .filter(block -> Math.abs(block.getLocation().getY() - machine.origin().getY() - 2) < .001)
                .filter(block -> {
                    var center = blockCenter(block);
                    return Math.abs(Math.atan2(center.x, center.y) - Math.PI / 20) < .01;
                }).findFirst().orElseThrow(() -> new AssertionError("missing first colored wedge " + machine.game()));
        var firstLabel = parts(machine).stream().filter(TextDisplay.class::isInstance).map(TextDisplay.class::cast)
                .min(Comparator.comparingDouble(label -> Math.abs(labelDirection(firstWedge, label) - Math.PI / 20)))
                .orElseThrow();
        var initial = blockCenter(firstWedge);
        packetClick(machine, "play", () -> {
            var moved = blockCenter(firstWedge);
            require(moved.distance(initial) > .05, machine.game() + " colored face rotates during the spin");
            double error = Math.IEEEremainder(Math.atan2(moved.x, moved.y) - labelDirection(firstWedge, firstLabel), Math.TAU);
            require(Math.abs(error) < .01, machine.game() + " colored wedge stays under its label");
            next.run();
        });
    }

    private static Vector3f blockCenter(BlockDisplay block) {
        var t = block.getTransformation();
        return new Vector3f(.5f).rotate(t.getRightRotation()).mul(t.getScale())
                .rotate(t.getLeftRotation()).add(t.getTranslation());
    }

    private static double labelDirection(BlockDisplay wedge, TextDisplay label) {
        var delta = label.getLocation().subtract(wedge.getLocation()).toVector()
                .rotateAroundY(Math.toRadians(wedge.getLocation().getYaw()));
        return Math.atan2(delta.getX(), delta.getY() + .02);
    }

    private void verifyResult(GameMachine<?> machine, boolean restored) {
        var round = (GameRound) field(machine, "round");
        var result = (MachineResultState) field(machine, "resultState");
        require(result.last() != null && result.pending() == 0, "settled result " + machine.game());
        if (restored) {
            require(round.sequence() == 0, "fresh round after restart");
        } else {
            require(round.finished() && round.payout() == result.last().returned()
                    && round.stake() == result.last().stake(), "payout matches finished round");
        }
        require(result.last().stake() == 125 && result.totalNet() == result.last().net(), "saved personal result");
        if (round instanceof MoneyWheelRound wheel) {
            require(wheel.choice() == 2 && result.last().returned() == 625, "saved wheel selection and payout");
        }
        if (!restored) expectedBalance += result.last().net();
        require(casino.sessions().reservation(player.getUniqueId()) == null, "player session released");
        settlements++;
    }

    private void inspect(GameMachine<?> machine, float expectedYaw) {
        boolean hasModel = false, hasText = false, hasHit = false;
        var locations = new ArrayList<Location>();
        for (Entity entity : parts(machine)) {
            require(entity.isValid(), "invalid entity " + machine.game());
            if (entity instanceof Display display) {
                var pos = display.getLocation();
                require(Math.abs(Math.IEEEremainder(pos.getYaw() - expectedYaw, 360)) < .01
                        && Math.abs(pos.getPitch()) < .01, machine.game() + " " + entity.getType()
                        + " facing " + pos.getYaw() + " expected " + expectedYaw);
                var t = display.getTransformation();
                require(t.getScale().isFinite() && t.getTranslation().isFinite(), "finite pose");
                hasModel |= display instanceof BlockDisplay;
                hasText |= display instanceof TextDisplay;
                displays++;
            } else if (entity instanceof Interaction hit) {
                var bounds = hit.getBoundingBox();
                locations.add(bounds.getCenter().toLocation(entity.getWorld()));
                hasHit |= hit.isResponsive() && bounds.getVolume() > 0;
            }
        }
        require(hasModel && hasText && hasHit, "model, text and hitboxes " + machine.game());
        for (var entry : controls(machine).entrySet()) {
            var center = buttonCenter(machine, entry.getKey(), entry.getValue());
            require(locations.stream().anyMatch(p -> p.distanceSquared(center) < .00002),
                    "button model disagrees with Interaction center " + machine.game() + " at " + center);
            buttons++;
        }
    }

    private Location buttonCenter(GameMachine<?> machine, ItemDisplay visual, ButtonDefinition button) {
        double scale = machine.game().equals("plinko") ? .75 : machine.game().equals("mines") ? 2 / 3.5
                : machine.game().equals("crash") ? .8 : 1;
        // Independently reconstruct the hit center from the actual visual rotation and its local geometry.
        var local = new Vector3f(0, (float)(button.height() / 2), (float)((button.depth() - button.press()) / 2));
        button.transform().rotation().transform(local).mul((float)(scale * button.transform().scale()));
        var pos = visual.getLocation();
        return pos.add(new Vector(local.x, local.y, local.z).rotateAroundY(-Math.toRadians(pos.getYaw())));
    }

    private void packetClick(GameMachine<?> machine, String action, Runnable next) {
        @SuppressWarnings("unchecked") var actions = (Map<ItemDisplay, String>) field(machine, "buttonActions");
        var visual = actions.entrySet().stream().filter(e -> e.getValue().equals(action))
                .findFirst().orElseThrow().getKey();
        var center = buttonCenter(machine, visual, controls(machine).get(visual));
        var hit = parts(machine).stream().filter(Interaction.class::isInstance).map(Interaction.class::cast)
                .min(Comparator.comparingDouble(e -> e.getBoundingBox().getCenter().distanceSquared(center.toVector())))
                .orElseThrow();
        var normal = visual.getLocation().getDirection().multiply(.35);
        var eye = center.clone().add(normal).add(0, .35, 0);
        eye.setDirection(center.toVector().subtract(eye.toVector()));
        var feet = eye.subtract(0, player.getEyeHeight(), 0);
        move(feet, () -> {
            require(hit.getBoundingBox().getCenter().distanceSquared(center.toVector()) < .00002, "actual hitbox");
            getLogger().info("CASINO_MATRIX_CLICK id=" + hit.getEntityId() + " game=" + machine.game() + " action=" + action);
            hits++;
            later(15, next);
        });
    }

    private void move(Location location, Runnable action) {
        casino.scheduler().teleport(player, location, ok -> guard(() -> {
            require(ok, "player teleport " + location);
            later(3, action);
        }));
    }

    private void later(int ticks, Runnable action) {
        casino.scheduler().later(player, () -> guard(action), ticks);
    }

    private void guard(Runnable action) {
        try { action.run(); }
        catch (Throwable error) { getLogger().log(Level.SEVERE, "CASINO_MATRIX_FAIL " + error, error); }
    }

    private static Object field(GameMachine<?> machine, String name) {
        try {
            Field f = GameMachine.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(machine);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void setField(Object target, Class<?> type, String name, Object value) {
        try {
            var f = type.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @SuppressWarnings("unchecked") private static List<Entity> parts(GameMachine<?> m) {
        return (List<Entity>) field(m, "parts");
    }
    @SuppressWarnings("unchecked") private static Map<ItemDisplay, ButtonDefinition> controls(GameMachine<?> m) {
        return (Map<ItemDisplay, ButtonDefinition>) field(m, "buttons");
    }
    private static void require(boolean test, String message) { if (!test) throw new AssertionError(message); }
}
