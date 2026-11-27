package GoldenEggOVO.TDCasino.machine;

import GoldenEggOVO.TDCasino.Core.platform.PlatformMessages;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinitionRegistry;

import static GoldenEggOVO.TDCasino.text.CasinoMessages.text;
import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.game.blackjack.BlackjackMachine;
import GoldenEggOVO.TDCasino.game.crash.CrashMachine;
import GoldenEggOVO.TDCasino.game.dragon_tower.DragonTowerMachine;
import GoldenEggOVO.TDCasino.game.duck_race.DuckRaceMachine;
import GoldenEggOVO.TDCasino.game.hilo.HiloMachine;
import GoldenEggOVO.TDCasino.game.keno.KenoMachine;
import GoldenEggOVO.TDCasino.game.mines.MinesMachine;
import GoldenEggOVO.TDCasino.game.money_wheel.MoneyWheelMachine;
import GoldenEggOVO.TDCasino.game.penguin_cross.PenguinCrossMachine;
import GoldenEggOVO.TDCasino.game.plinko.PlinkoMachine;
import GoldenEggOVO.TDCasino.game.slots.SlotsMachine;
import GoldenEggOVO.TDCasino.game.wheel_of_fortune.WheelOfFortuneMachine;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import GoldenEggOVO.TDCasino.Core.platform.PlatformEvents;
import GoldenEggOVO.TDCasino.Core.platform.PlatformScheduler;

import java.io.IOException;
import java.util.*;
public final class MachineManager implements Listener {
    private final TDCasinoPlugin plugin;
    private final Map<Key, GameMachine<?>> machines = new LinkedHashMap<>();
    private final Map<Key, MachinePlacementStore.Placement> placements = new LinkedHashMap<>();
    private final MachinePlacementStore placementStore;
    private final PlayerMachineProfileStore playerProfiles;
    private final Set<UUID> profileErrorPlayers = new HashSet<>();
    private boolean restoreScheduled;
    private boolean closed;
    private final MachineDefinitionRegistry definitions = new MachineDefinitionRegistry();
    private final Map<Key, PlatformScheduler.Task> ticks = new HashMap<>();
    private final Set<Key> restoring = new HashSet<>();

    public MachineManager(TDCasinoPlugin plugin) throws IOException {
        this.plugin = plugin;
        playerProfiles = new PlayerMachineProfileStore(plugin.getDataFolder().toPath().resolve("casino/players"));
        definitions.reload(plugin.getDataFolder().toPath().resolve("casino/machines"));
        placementStore = new MachinePlacementStore(plugin.getDataFolder().toPath().resolve("casino/placements.json"));
        for (var placement : placementStore.load()) {
            placements.put(new Key(placement.owner(), placement.definition().game()), placement);
        }
        scheduleRestore();
        PlatformEvents.register(plugin, this);
    }

    void restorePlayerData(UUID owner, String game, UUID player,
            GoldenEggOVO.TDCasino.game.GameRound round, MachineResultState resultState) {
        try {
            var profile = playerProfiles.load(player, owner, game);
            if (profile != null) profile.restore(round, resultState);
            resetInvalidConfiguredStake(round);
        } catch (IOException ex) { reportProfileError(player, ex); }
    }

    void resetInvalidConfiguredStake(GoldenEggOVO.TDCasino.game.GameRound round) {
        if (round.active()) return;
        long configuredStakeCents = round instanceof GoldenEggOVO.TDCasino.game.StartedRound started
                ? started.configuredStake() : round.stake();
        if (configuredStakeCents == 0) return;
        try {
            if (!plugin.casino().economyEnabled()) {
                round.setStake(0);
                return;
            }
            plugin.casino().validateStakeCents(configuredStakeCents);
        } catch (IllegalArgumentException error) {
            round.setStake(0);
        }
    }

    void savePlayerData(UUID owner, String game, UUID player,
            GoldenEggOVO.TDCasino.game.GameRound round, MachineResultState resultState) {
        try { playerProfiles.save(player, owner, game, PlayerMachineProfileStore.Profile.capture(round, resultState)); }
        catch (IOException ex) { reportProfileError(player, ex); }
    }

    private void reportProfileError(UUID player, IOException ex) {
        if (profileErrorPlayers.add(player)) plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Cannot load/save personal data for " + player + "; check casino/players/" + player + ".json", ex);
    }

    TDCasinoPlugin plugin() {
        return plugin;
    }

    public List<String> complete(Player player, String[] args) {
        boolean admin = GoldenEggOVO.TDCasino.Core.player.GamePermissions.admin(player);
        List<String> choices;
        if (args.length <= 1) choices = admin ? List.of("create", "remove", "reload-models") : List.of("remove");
        else if (args.length == 2 && (args[0].equalsIgnoreCase("create") && admin || args[0].equalsIgnoreCase("remove")))
            choices = MachineDefinition.games().stream().sorted().toList();
        else if (args.length == 3 && admin && args[0].equalsIgnoreCase("create")) choices = definitions.ids(args[1]);
        else return List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.startsWith(prefix)).toList();
    }
    public void command(Player player, String[] args) {
        if (!plugin.allowed(player)) {
            PlatformMessages.send(player, GoldenEggOVO.TDCasino.text.CasinoMessages.component("machine.denied"));
            return;
        }
        try {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload-models")) {
                requireAdmin(player);
                definitions.reload(plugin.getDataFolder().toPath().resolve("casino/machines"));
                PlatformMessages.send(player, GoldenEggOVO.TDCasino.text.CasinoMessages.component("machine.models-reloaded"));
                return;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("remove")) {
                if (args.length > 2) throw new IllegalArgumentException("error.remove-usage");
                if (GoldenEggOVO.TDCasino.Core.player.GamePermissions.admin(player) && args.length == 2) {
                    var machine = managedNearby(player, args[1]);
                    if (machine == null) throw new IllegalArgumentException("error.machine-distance");
                    remove(machine);
                    PlatformMessages.send(player, GoldenEggOVO.TDCasino.text.CasinoMessages.component("machine.removed"));
                    return;
                }
                for (var machine : machines.values())
                    if (machine.owner().equals(player.getUniqueId())
                            && (args.length == 1 || machine.game().equalsIgnoreCase(args[1]))
                            && !machine.managementIdle())
                        throw new IllegalArgumentException("error.machine-busy");
                var candidate = new LinkedHashMap<>(placements);
                candidate
                        .keySet()
                        .removeIf(
                                key ->
                                        key.owner.equals(player.getUniqueId())
                                                && (args.length == 1
                                                        || key.game.equalsIgnoreCase(args[1])));
                save(candidate);
                for (var machine : List.copyOf(machines.values())) {
                    if (!placements.containsKey(new Key(machine.owner(), machine.game())))
                        detach(machine);
                }
                PlatformMessages.send(player, GoldenEggOVO.TDCasino.text.CasinoMessages.component("machine.removed"));
                return;
            }
            if (args.length < 2 || args.length > 3 || !args[0].equalsIgnoreCase("create")) {
                PlatformMessages.send(player, GoldenEggOVO.TDCasino.text.CasinoMessages.component("machine.usage"));
                return;
            }
            requireAdmin(player);
            String game = args[1].toLowerCase(Locale.ROOT);
            var definition = definitions.get(game, args.length == 3 ? args[2] : game);
            var key = new Key(player.getUniqueId(), game);
            if (placements.containsKey(key)) {
                PlatformMessages.send(player, GoldenEggOVO.TDCasino.text.CasinoMessages.component("machine.exists"));
                return;
            }
            var origin = player.getLocation();
            float yaw = Math.round(origin.getYaw() / 90f) * 90f;
            origin.setYaw(yaw);
            origin.setPitch(0);
            origin.add(origin.getDirection().multiply(4));
            origin.setX(Math.floor(origin.getX()) + .5);
            origin.setY(Math.floor(origin.getY()));
            origin.setZ(Math.floor(origin.getZ()) + .5);
            origin.setYaw(yaw + 180);
            if (origin.getY() + 6 >= origin.getWorld().getMaxHeight()) {
                PlatformMessages.send(player, GoldenEggOVO.TDCasino.text.CasinoMessages.component("machine.height"));
                return;
            }
            requireOwned(origin);
            var machine = create(game, player.getUniqueId(), origin, definition);
            try {
                machine.build();
                var candidate = new LinkedHashMap<>(placements);
                candidate.put(
                        key,
                        new MachinePlacementStore.Placement(
                                player.getUniqueId(),
                                origin.getWorld().getUID(),
                                origin.getX(),
                                origin.getY(),
                                origin.getZ(),
                                origin.getYaw(),
                                definition,
                                0));
                activate(key, machine);
                save(candidate);
            } catch (RuntimeException ex) {
                if (contains(machine)) detach(machine);
                else machine.close();
                throw ex;
            }
            PlatformMessages.send(player, GoldenEggOVO.TDCasino.text.CasinoMessages.component("machine.created"));
        } catch (IOException | IllegalArgumentException ex) {
            PlatformMessages.send(player, GoldenEggOVO.TDCasino.text.CasinoMessages.component("machine.failed", "error", GoldenEggOVO.TDCasino.text.CasinoMessages.error(ex)));
            if (ex.getMessage() == null || !ex.getMessage().startsWith("error."))
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Machine command failed", ex);
        }
    }

    private GameMachine<?> create(
            String game, UUID owner, Location origin, MachineDefinition definition) {
        return switch (game) {
            case "mines" -> new MinesMachine(this, owner, origin, definition);
            case "blackjack" -> new BlackjackMachine(this, owner, origin, definition);
            case "crash" -> new CrashMachine(this, owner, origin, definition);
            case "plinko" -> new PlinkoMachine(this, owner, origin, definition);
            case "slots" -> new SlotsMachine(this, owner, origin, definition);
            case "duck_race" -> new DuckRaceMachine(this, owner, origin, definition);
            case "wheel_of_fortune" -> new WheelOfFortuneMachine(this, owner, origin, definition);
            case "money_wheel" -> new MoneyWheelMachine(this, owner, origin, definition);
            case "penguin_cross" -> new PenguinCrossMachine(this, owner, origin, definition);
            case "keno" -> new KenoMachine(this, owner, origin, definition);
            case "hilo" -> new HiloMachine(this, owner, origin, definition);
            case "dragon_tower" -> new DragonTowerMachine(this, owner, origin, definition);
            default -> throw new IllegalArgumentException("Unknown game: " + game);
        };
    }

    public boolean contains(GameMachine<?> machine) {
        return machines.get(new Key(machine.owner(), machine.game())) == machine;
    }

    private void save(Map<Key, MachinePlacementStore.Placement> candidate) {
        try {
            placementStore.save(candidate.values());
        } catch (IOException ex) {
            throw new IllegalArgumentException("error.machine-save", ex);
        }
        placements.clear();
        placements.putAll(candidate);
    }

    public void remove(GameMachine<?> machine) {
        if (!contains(machine)) return;
        if (!machine.managementIdle()) throw new IllegalArgumentException("error.machine-busy");
        var candidate = new LinkedHashMap<>(placements);
        candidate.remove(new Key(machine.owner(), machine.game()));
        save(candidate);
        detach(machine);
    }

    private void detach(GameMachine<?> machine) {
        Key key = new Key(machine.owner(), machine.game());
        if (!machines.remove(key, machine)) return;
        var task = ticks.remove(key);
        if (task != null) task.cancel();
        machine.close();
    }

    private void scheduleRestore() {
        if (closed || restoreScheduled) return;
        restoreScheduled = true;
        plugin.scheduler().later(() -> {
            restoreScheduled = false;
            if (closed) return;
            for (var entry : List.copyOf(placements.entrySet())) {
                Key key = entry.getKey();
                var saved = entry.getValue();
                if (machines.containsKey(key) || restoring.contains(key)) continue;
                World world = plugin.getServer().getWorld(saved.world());
                int x = ((int) Math.floor(saved.x())) >> 4, z = ((int) Math.floor(saved.z())) >> 4;
                if (world == null || !world.isChunkLoaded(x, z)) continue;
                var origin = new Location(world, saved.x(), saved.y(), saved.z(), saved.yaw(), 0);
                restoring.add(key);
                plugin.scheduler().execute(origin, () -> {
                    restoring.remove(key);
                    if (closed || placements.get(key) != saved || machines.containsKey(key)
                            || plugin.getServer().getWorld(saved.world()) != world || !world.isChunkLoaded(x, z)) return;
                    restore(key, saved, origin);
                });
            }
        }, 1);
    }

    private void restore(Key key, MachinePlacementStore.Placement saved, Location origin) {
        var machine = create(saved.definition().game(), saved.owner(), origin, saved.definition());
        try {
            machine.build();
            activate(key, machine);
        } catch (RuntimeException ex) {
            machine.close();
            plugin.getLogger().log(java.util.logging.Level.WARNING, text("machine.restore-failed", "placement", key), ex);
        }
    }

    private void activate(Key key, GameMachine<?> machine) {
        var task = plugin.scheduler().repeat(machine.origin(), () -> tick(machine), 1, 1);
        ticks.put(key, task);
        machines.put(key, machine);
    }

    void requireOwned(Location location) {
        if (plugin.scheduler().folia() && !plugin.scheduler().isOwned(location))
            throw new IllegalArgumentException("error.machine-distance");
    }

    public boolean canUse(Player player, GameMachine<?> machine) {
        return (!plugin.scheduler().folia() || plugin.scheduler().isOwned(player) && plugin.scheduler().isOwned(machine.origin()))
                && player.getWorld().equals(machine.origin().getWorld()) && plugin.allowed(player);
    }

    private GameMachine<?> managedNearby(Player player, String game) {
        return machines.values().stream()
                .filter(machine -> machine.game().equalsIgnoreCase(game) && canManage(player, machine))
                .min(Comparator.comparingDouble(machine -> machine.origin().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    public boolean canManage(Player player, GameMachine<?> machine) {
        return canUse(player, machine)
                && (GoldenEggOVO.TDCasino.Core.player.GamePermissions.admin(player) || player.getUniqueId().equals(machine.owner()))
                && machine.nearSettings(player);
    }

    double blockDistance(Player player) {
        var block =
                player.getWorld()
                        .rayTraceBlocks(
                                player.getEyeLocation(),
                                player.getEyeLocation().getDirection(),
                                5,
                                FluidCollisionMode.NEVER,
                                true);
        return block == null
                ? 5
                : block.getHitPosition().distance(player.getEyeLocation().toVector()) + .001;
    }

    @EventHandler
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getPlayer().isSneaking()
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                        && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        click(event.getPlayer(), () -> event.setCancelled(true));
    }

    @EventHandler
    public void interactEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getPlayer().isSneaking()) return;
        click(event.getPlayer(), () -> event.setCancelled(true));
    }

    @EventHandler
    public void interactAtEntity(PlayerInteractAtEntityEvent event) {
        interactEntity(event);
    }

    private void click(Player player, Runnable cancel) {
        double nearest = blockDistance(player);
        GameMachine<?> chosen = null;
        GameMachine.TargetHit chosenHit = null;
        for (var machine : machines.values()) {
            if (!canUse(player, machine)) continue;
            var hit = machine.ray(player, nearest);
            if (hit != null && hit.distance() <= nearest) {
                nearest = hit.distance();
                chosen = machine;
                chosenHit = hit;
            }
        }
        if (chosen != null) {
            cancel.run();
            chosen.click(player, chosenHit);
        } else
            for (var machine : machines.values())
                if (canUse(player, machine)) machine.confirmInput(player);
    }

    private void resetSession(GameMachine<?> machine) {
        var saved = placements.get(new Key(machine.owner(), machine.game()));
        if (saved == null) return;
        var replacement = create(machine.game(), machine.owner(), machine.origin(), saved.definition());
        detach(machine);
        replacement.inheritPlayers(machine);
        try {
            replacement.build();
            activate(new Key(machine.owner(), machine.game()), replacement);
        } catch (RuntimeException ex) {
            replacement.close();
            machines.remove(new Key(machine.owner(), machine.game()));
            throw ex;
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) { releasePlayer(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void death(org.bukkit.event.entity.PlayerDeathEvent event) { releasePlayer(event.getEntity()); }

    @EventHandler
    public void changeWorld(PlayerChangedWorldEvent event) { releasePlayer(event.getPlayer()); }

    private void releasePlayer(Player player) {
        UUID id = player.getUniqueId();
        for (var machine : List.copyOf(machines.values())) {
            if (!machine.occupiedBy(id)) continue;
            plugin.scheduler().execute(machine.origin(), () -> {
                if (!closed && contains(machine) && machine.occupiedBy(id)) resetSession(machine);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unload(ChunkUnloadEvent event) {
        for (var machine : List.copyOf(machines.values()))
            if (machine.touchesChunk(event.getChunk())) detach(machine);
    }

    @EventHandler
    public void load(ChunkLoadEvent event) {
        scheduleRestore();
    }

    @EventHandler
    public void loadWorld(WorldLoadEvent event) {
        scheduleRestore();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unloadWorld(WorldUnloadEvent event) {
        restoring.removeIf(key -> {
            var saved = placements.get(key);
            return saved == null || saved.world().equals(event.getWorld().getUID());
        });
        for (var machine : List.copyOf(machines.values()))
            if (machine.origin().getWorld().equals(event.getWorld())) detach(machine);
    }

    private void tick(GameMachine<?> machine) {
        if (closed || !contains(machine)) return;
        try {
            if (machine.expired()) {
                detach(machine);
                return;
            }
            machine.tick();
            if (machine.sessionExpired()) resetSession(machine);
        } catch (RuntimeException ex) {
            detach(machine);
            plugin.getLogger().log(java.util.logging.Level.WARNING, text("machine.animation-failed", "game", machine.game()), ex);
        }
    }

    public void close() {
        closed = true;
        ticks.values().forEach(PlatformScheduler.Task::cancel);
        ticks.clear();
        restoring.clear();
        for (var machine : machines.values()) {
            try { machine.close(); }
            catch (RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Cannot close Casino machine " + machine.game(), error);
            }
        }
        machines.clear();
        HandlerList.unregisterAll(this);
    }

    private static void requireAdmin(Player player) {
        if (!GoldenEggOVO.TDCasino.Core.player.GamePermissions.admin(player)) throw new IllegalArgumentException("machine.denied");
    }

    public List<GameMachine<?>> machines() { return List.copyOf(machines.values()); }

    private record Key(UUID owner, String game) {}
}
