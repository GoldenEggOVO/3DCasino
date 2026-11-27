package GoldenEggOVO.TDCasino.machine;

import GoldenEggOVO.TDCasino.Core.platform.PlatformMessages;
import GoldenEggOVO.TDCasino.Core.render.DisplayPlacement;
import GoldenEggOVO.TDCasino.machine.definition.ButtonDefinition;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import GoldenEggOVO.TDCasino.machine.definition.MachineTransform;
import GoldenEggOVO.TDCasino.render.model.ModelCarrierItems;

import GoldenEggOVO.TDCasino.audio.MachineSounds;
import GoldenEggOVO.TDCasino.render.MachineModelDisplay;
import GoldenEggOVO.TDCasino.Core.render.DisplayAudience;

import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.render.MachineGeometry;
import GoldenEggOVO.TDCasino.game.StartedRound;
import GoldenEggOVO.TDCasino.game.GameRound;
import GoldenEggOVO.TDCasino.economy.RoundFunding;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.*;
public abstract class GameMachine<R extends GameRound> {
    protected static final double PITCH = -Math.toRadians(35);
    protected final TDCasinoPlugin plugin;
    protected final MachineManager manager;
    protected final UUID owner;
    protected final Location origin;
    protected final MachineDefinition definition;
    protected R round;
    private MachinePlayers<R> players;
    private MachinePlayers.Data<R> playerData;
    private UUID displayedPlayer;
    private RoundFunding funding;
    protected final List<Entity> parts = new ArrayList<>();
    private final List<Target> targets = new ArrayList<>();
    private final Map<ItemDisplay, ButtonDefinition> buttons = new LinkedHashMap<>();
    private final Map<ItemDisplay, String> buttonActions = new LinkedHashMap<>();
    private final Map<ItemDisplay, Integer> pressed = new HashMap<>();
    private final List<MachineModelDisplay> modelDisplays = new ArrayList<>();
    private final DisplayAudience audience;
    private final List<Runnable> translatedLabels = new ArrayList<>();
    private long languageRevision = GoldenEggOVO.TDCasino.text.CasinoMessages.revision();
    private ItemDisplay highlighted;
    protected int age;
    private long lastClick;
    private final Map<UUID, Long> lastOccupiedNotice = new HashMap<>();
    protected MachineResultState resultState;
    private final MachineSounds sounds;
    private MachineScreen screen;
    private int minChunkX = Integer.MAX_VALUE, maxChunkX = Integer.MIN_VALUE;
    private int minChunkZ = Integer.MAX_VALUE, maxChunkZ = Integer.MIN_VALUE;


    protected GameMachine(
            MachineManager manager,
            UUID owner,
            Location origin,
            MachineDefinition definition,
            java.util.function.Supplier<R> rounds) {
        this.manager = manager;
        this.plugin = manager.plugin();
        this.owner = owner;
        this.origin = origin.clone();
        audience = DisplayAudience.withNativeFallback(plugin, this.origin);
        this.definition = definition;
        players = new MachinePlayers<>(rounds, (id, data) ->
                manager.restorePlayerData(owner, definition.game(), id, data.round, data.resultState),
                plugin.sessions(), sessionKey());
        selectPlayer(owner);
        sounds = new MachineSounds(plugin, this.origin);
    }

    private void selectPlayer(UUID id) {
        displayedPlayer = id;
        playerData = players.data(id);
        round = playerData.round;
        resultState = playerData.resultState;
        manager.resetInvalidConfiguredStake(round);
        funding = plugin.casino().payments().bind(id, sessionKey());
        round.funding(funding);
    }

    protected final void savePlayerData() {
        manager.savePlayerData(owner, game(), displayedPlayer, round, resultState);
    }

    protected final void touchPlayerInput() {
        var id = players.player();
        if (id != null) players.claim(id);
    }

    protected final UUID playingPlayer() { return players.player(); }
    final boolean acceptsPlayer(Player player) { return players.accepts(player.getUniqueId()); }
    final boolean sessionExpired() { return players.expired(); }
    final boolean occupiedBy(UUID id) { return id.equals(players.player()); }
    public final boolean managementIdle() { return players.player() == null && canEditStake(); }

    private String sessionKey() { return "casino:" + owner + ":" + definition.game(); }

    @SuppressWarnings("unchecked")
    final void inheritPlayers(GameMachine<?> previous) {
        var old = (GameMachine<R>) previous;
        old.players.abandon();
        players = old.players;
        selectPlayer(old.displayedPlayer);
    }

    final void confirmInput(Player player) {
        if (occupiedBy(player.getUniqueId())) confirmInput();
    }

    public final UUID owner() {
        return owner;
    }

    public final String game() {
        return definition.game();
    }

    public final Location origin() {
        return origin.clone();
    }

    protected double scale() {
        return game().equals("plinko") ? .75 : MachineGeometry.machineScale(game());
    }

    public final void build() {
        buildGame();
        screen = new MachineScreen(this);
        for (var part : definition.parts()) {
            var transform = part.transform();
            var display =
                    DisplayPlacement.spawn(
                                    worldAt(transform.x(), transform.y(), transform.z()),
                                    ItemDisplay.class,
                                    entity -> {
                                        common(entity);
                                        initializeItem(entity, ModelCarrierItems.resolve(part.model()));
                                        entity.setTransformation(
                                                new Transformation(
                                                        new Vector3f(),
                                                        transform.rotation(),
                                                        new Vector3f((float) (4 * transform.scale() * scale())),
                                                        new Quaternionf().rotateY((float) Math.PI)));
                                    });
            attachModel(display);
        }
        var b = settingsBounds();
        double s = scale();
        plugin.machineSettings()
                .register(
                        this,
                        owner,
                        origin,
                        new BoundingBox(
                                b.get(0) * s,
                                b.get(1) * s,
                                b.get(2) * s,
                                b.get(3) * s,
                                b.get(4) * s,
                                b.get(5) * s),
                        this::settings);
        refresh();
        updateFeedback();
        syncMachineModelDisplays();
    }

    protected abstract void buildGame();

    protected abstract void action(String action);

    protected abstract boolean available(String action);

    protected abstract void refresh();

    protected List<Double> settingsBounds() {
        return definition.settingsBounds();
    }

    protected void animate() {}

    protected boolean busy() {
        return false;
    }

    protected boolean canEditStake() {
        return !round.active() && !busy();
    }

    protected boolean rowAvailable(int row) {
        return true;
    }

    protected void confirmInput() {}

    public final boolean expired() {
        return parts.stream().anyMatch(e -> !plugin.scheduler().isOwned(e) || !e.isValid());
    }

    public final boolean touchesChunk(Chunk chunk) {
        if (origin.getWorld() != chunk.getWorld()) return false;
        int x = chunk.getX(), z = chunk.getZ();
        if ((origin.getBlockX() >> 4) == x && (origin.getBlockZ() >> 4) == z) return true;
        if (x < minChunkX || x > maxChunkX || z < minChunkZ || z > maxChunkZ) return false;
        return parts.stream().filter(plugin.scheduler()::isOwned).anyMatch(entity -> {
            Location location = entity.getLocation();
            return (location.getBlockX() >> 4) == x && (location.getBlockZ() >> 4) == z;
        });
    }

    public final void tick() {
        age++;
        for (var iterator = pressed.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            boolean done = age >= entry.getValue();
            if (done) {
                entry.getKey().setTransformation(MachineGeometry.buttonPose(buttons.get(entry.getKey()), scale(), 0));
                iterator.remove();
            }
        }
        animate();
        if (languageRevision != GoldenEggOVO.TDCasino.text.CasinoMessages.revision() && !busy()) {
            translatedLabels.forEach(Runnable::run);
            refresh();
            languageRevision = GoldenEggOVO.TDCasino.text.CasinoMessages.revision();
        }
        updateFeedback();
        players.update(round.active() || busy() || resultState.pending() > 0 || !canEditStake());
        players.tick();
        sounds.tick(age);
        if (age % 4 == 0) hover();
        syncMachineModelDisplays();
    }
    protected boolean revealing() { return busy(); }
    protected boolean feedbackFinished() { return round.finished(); }
    protected Long cashoutAmount() { return null; }

    private void updateFeedback() {
        long sequence = round.sequence();
        if (sequence != playerData.observedRound) {
            playerData.observedRound = sequence;
            resultState.launch(sequence, round.stake());
            sounds.start();
        }
        resultState.reviseStake(sequence, round.stake());
        if (feedbackFinished()) {
            accountFeedback(sequence, round.payout());
            if (!revealing()) settleFeedback(sequence, round.payout());
        }
        long stake = round instanceof StartedRound started && sequence == 0 ? started.configuredStake() : round.stake();
        Long cashout = revealing() ? null : cashoutAmount();
        if (screen != null) screen.update(resultState, stake, cashout == null ? null : normalizeReturn(cashout));
    }

    protected final void launchFeedback(Object id, long stake) {
        resultState.launch(id, stake);
        sounds.start();
    }
    protected final void accountFeedback(Object id, long returned) {
        if (resultState.settle(id, returned)) savePlayerData();
    }
    protected final void settleFeedback(Object id, long returned) {
        var result = resultState.reveal(id, returned);
        if (result != null) {
            resultSound(result);
            savePlayerData();
        }
    }
    protected void resultSound(MachineResultState.Result result) { sounds.result(result, age); }
    protected final void resultCue(MachineResultState.Result result, Sound sound, float volume, float pitch) {
        sounds.resultSingle(result, sound, volume, pitch);
    }
    protected final void sound(Sound sound, float volume, float pitch) { sounds.play(sound, volume, pitch); }

    protected final long normalizeReturn(long amount) { return funding.normalize(amount); }

    private void syncMachineModelDisplays() {
        audience.refresh();
        modelDisplays.removeIf(display -> !display.sync());
    }

    private void attachModel(ItemDisplay display) {
        if (ModelCarrierItems.modelName(display.getItemStack()) != null)
            modelDisplays.add(new MachineModelDisplay(display, this::common, parts,
                    audience, id -> plugin.pack().item(id)));
    }

    protected final Location worldAt(double x, double y, double z) {
        var p = MachineGeometry.rotate(x * scale(), y * scale(), z * scale(), origin.getYaw());
        Location location = origin.clone().add(p.x(), p.y(), p.z());
        trackLocation(location);
        return location;
    }

    private void trackLocation(Location location) {
        manager.requireOwned(location);
        int x = location.getBlockX() >> 4, z = location.getBlockZ() >> 4;
        minChunkX = Math.min(minChunkX, x);
        maxChunkX = Math.max(maxChunkX, x);
        minChunkZ = Math.min(minChunkZ, z);
        maxChunkZ = Math.max(maxChunkZ, z);
    }

    protected final Location at(double x, double y, double z) {
        var p = definition.anchor("playfield").apply(x, y, z);
        return worldAt(p.x(), p.y(), p.z());
    }

    protected final void common(Entity entity) {
        entity.setPersistent(false);
        entity.setGravity(false);
        entity.setInvulnerable(true);
        parts.add(entity);
        if (entity instanceof Display display) {
            display.setBrightness(
                    new Display.Brightness(
                            game().equals("plinko") ? 15 : 12, game().equals("plinko") ? 15 : 12));
            display.setViewRange(.6f);
            display.setTeleportDuration(1);
        }
    }

    protected final ItemStack model(String name) {
        String fallback =
                switch (name) {
                    case "mine_hidden" -> "material:POLISHED_DEEPSLATE";
                    case "mine_gem" -> "material:EMERALD_BLOCK";
                    case "mine_bomb" -> "material:TNT";
                    default -> "3dcasino:" + name;
                };
        return ModelCarrierItems.resolve(definition.models().getOrDefault(name, fallback));
    }

    private void initializeItem(ItemDisplay display, ItemStack stack) {
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setItemStack(stack);
        display.setVisibleByDefault(ModelCarrierItems.modelName(stack) == null);
    }

    protected final ItemDisplay item(
            ItemStack stack, double x, double y, double z, double size, double pitch) {
        var result = DisplayPlacement.spawn(
                        at(x, y, z),
                        ItemDisplay.class,
                        display -> {
                            common(display);
                            initializeItem(display, stack);
                            pose(display, MachineGeometry.itemPose(size, pitch, 0));
                            display.setInterpolationDuration(1);
                        });
        attachModel(result);
        return result;
    }

    protected final ItemDisplay model(String name, double x, double y, double z, double size) {
        return item(model(name), x, y, z, size, 0);
    }

    protected final void body(String name) {
        var anchor = definition.anchor("body");
        var point = anchor.apply(0, 0, 0);
        var display = DisplayPlacement.spawn(
                        worldAt(point.x(), point.y(), point.z()),
                        ItemDisplay.class,
                        entity -> {
                            common(entity);
                            initializeItem(entity, model(name));
                            entity.setTransformation(
                                    new Transformation(
                                            new Vector3f(),
                                            anchor.rotation(),
                                            new Vector3f((float) (4 * scale() * anchor.scale())),
                                            new Quaternionf().rotateY((float) Math.PI)));
                        });
        attachModel(display);
    }

    protected final void translatedLabel(TextDisplay display, java.util.function.Supplier<Component> text) {
        Runnable update = () -> PlatformMessages.text(display, text.get());
        translatedLabels.add(update);
        update.run();
    }

    protected final void pose(Display display, Transformation local) {
        var anchor = definition.anchor("playfield");
        float factor = (float) (scale() * anchor.scale());
        var rotation = anchor.rotation();
        var translation = new Vector3f(local.getTranslation()).mul(factor);
        rotation.transform(translation);
        display.setTransformation(
                new Transformation(
                        translation,
                        new Quaternionf(rotation).mul(local.getLeftRotation()),
                        new Vector3f(local.getScale()).mul(factor),
                        new Quaternionf(local.getRightRotation())));
    }

    protected final TextDisplay text(double x, double y, double z, double size) {
        return DisplayPlacement.spawn(
                        at(x, y, z),
                        TextDisplay.class,
                        display -> {
                            common(display);
                            display.setBillboard(Display.Billboard.FIXED);
                            display.setBackgroundColor(Color.fromARGB(0));
                            display.setDefaultBackground(false);
                            display.setLineWidth(1100);
                            pose(
                                    display,
                                    new Transformation(
                                            new Vector3f(),
                                            new Quaternionf(),
                                            new Vector3f((float) size),
                                            new Quaternionf()));
                        });
    }

    protected final void hit(
            String action,
            ItemDisplay visual,
            double x,
            double y,
            double z,
            double width,
            double height,
            int row) {
        target(
                        action,
                        visual,
                        targetMatrix(definition.anchor("playfield")),
                        new BoundingBox(
                                x - width / 2,
                                y,
                                z - width / 2,
                                x + width / 2,
                                y + height,
                                z + width / 2),
                        row);
    }

    protected final void button(String action, String modelName) {
        button(action, modelName, definition.button(action));
    }

    protected final void button(String action, String modelName, ButtonDefinition button) {
        var transform = button.transform();
        var visual =
                DisplayPlacement.spawn(
                                worldAt(transform.x(), transform.y(), transform.z()),
                                ItemDisplay.class,
                                display -> {
                                    common(display);
                                    initializeItem(display, model(modelName));
                                    display.setTransformation(MachineGeometry.buttonPose(button, scale(), 0));
                                    display.setInterpolationDuration(1);
                                });
        attachModel(visual);
        buttons.put(visual, button);
        buttonActions.put(visual, action);
        target(
                        action,
                        visual,
                        targetMatrix(transform),
                        new BoundingBox(
                                -button.width() / 2,
                                0,
                                -button.press(),
                                button.width() / 2,
                                button.height(),
                                button.depth()),
                        -1);
    }

    private Matrix4d targetMatrix(MachineTransform transform) {
        return new Matrix4d().translation(origin.getX(), origin.getY(), origin.getZ())
                .rotateY(-Math.toRadians(origin.getYaw())).scale(scale())
                .translate(transform.x(), transform.y(), transform.z())
                .rotate(transform.rotation()).scale(transform.scale());
    }
    protected final void hitDisplay(String action, ItemDisplay visual, BoundingBox bounds, int row) {
        var location = visual.getLocation();
        var matrix = new Matrix4d().translation(location.getX(), location.getY(), location.getZ())
                .rotateY(-Math.toRadians(location.getYaw()))
                .mul(new Matrix4d(MachineGeometry.nativePose(visual.getTransformation())));
        target(action, visual, matrix, bounds, row);
    }

    private void target(String action, ItemDisplay visual, Matrix4d matrix, BoundingBox bounds, int row) {
        targets.add(new Target(action, visual, new Matrix4d(matrix).invert(), bounds, row));
        var worldBounds = new BoundingBox();
        boolean first = true;
        for (double x : new double[] {bounds.getMinX(), bounds.getMaxX()})
            for (double y : new double[] {bounds.getMinY(), bounds.getMaxY()})
                for (double z : new double[] {bounds.getMinZ(), bounds.getMaxZ()}) {
                    var p = matrix.transformPosition(new Vector3d(x, y, z));
                    if (first) {
                        worldBounds.resize(p.x, p.y, p.z, p.x, p.y, p.z);
                        first = false;
                    } else worldBounds.union(p.x, p.y, p.z);
                }
        var location = new Location(origin.getWorld(), worldBounds.getCenterX(), worldBounds.getMinY(), worldBounds.getCenterZ());
        trackLocation(location);
        origin.getWorld().spawn(location, Interaction.class, entity -> {
            common(entity);
            entity.setInteractionWidth((float) Math.max(worldBounds.getWidthX(), worldBounds.getWidthZ()) + .002f);
            entity.setInteractionHeight((float) worldBounds.getHeight() + .002f);
            entity.setResponsive(true);
        });
    }

    protected final void selectedButtons(java.util.function.Predicate<String> selected) {
        buttonActions.forEach(
                (display, action) -> {
                    display.setGlowing(selected.test(action) || display == highlighted);
                    display.setGlowColorOverride(Color.YELLOW);
                });
    }

    final TargetHit ray(Player player, double maximum) {
        var eye = player.getEyeLocation();
        var direction = eye.getDirection();
        TargetHit nearest = null;
        for (var target : targets) {
            var a = target.inverse.transformPosition(new Vector3d(eye.getX(), eye.getY(), eye.getZ()));
            var d = target.inverse.transformDirection(new Vector3d(direction.getX(), direction.getY(), direction.getZ()));
            double factor = d.length();
            var vector = new Vector(d.x, d.y, d.z).normalize();
            var hit =
                    target.bounds.rayTrace(
                            new Vector(a.x(), a.y(), a.z()),
                            vector,
                            maximum * factor);
            if (hit == null) continue;
            double distance =
                    hit.getHitPosition().distance(new Vector(a.x(), a.y(), a.z()))
                            / factor;
            if (nearest == null || distance < nearest.distance)
                nearest = new TargetHit(target, distance);
        }
        return nearest;
    }

    final void click(Player player, TargetHit hit) {
        if (!acceptsPlayer(player)) {
            occupiedNotice(player);
            return;
        }
        if (!player.getUniqueId().equals(displayedPlayer)) {
            selectPlayer(player.getUniqueId());
            confirmInput();
            refresh();
        }
        long now = System.currentTimeMillis();
        if (!busy()) manager.resetInvalidConfiguredStake(round);
        if (now - lastClick < 150
                || busy()
                || !rowAvailable(hit.target.row)
                || !available(hit.target.action)) return;
        if (!players.claim(player.getUniqueId())) {
            occupiedNotice(player);
            return;
        }
        lastClick = now;
        long sequence = round.sequence();
        if (buttons.containsKey(hit.target.visual)) {
            var button = buttons.get(hit.target.visual);
            hit.target.visual.setTransformation(MachineGeometry.buttonPose(button, scale(), button.press()));
            pressed.put(hit.target.visual, age + 4);
        }
        sound(Sound.BLOCK_STONE_BUTTON_CLICK_ON, .25f, 1.1f);
        try {
            action(hit.target.action);
        } catch (IllegalArgumentException ex) {
            PlatformMessages.send(player, GoldenEggOVO.TDCasino.text.CasinoMessages.component("machine.failed", "error",
                    GoldenEggOVO.TDCasino.text.CasinoMessages.error(ex)));
            if (!round.active() && !busy() && resultState.pending() == 0) {
                players.releaseClaim();
                refresh();
            }
            return;
        }
        updateFeedback();
        savePlayerData();
        if (round.sequence() != sequence || resultState.pending() > 0) players.started();
        players.update(round.active() || busy() || resultState.pending() > 0 || !canEditStake());
        syncMachineModelDisplays();
    }

    private void occupiedNotice(Player player) {
        long now = System.currentTimeMillis();
        Long previous = lastOccupiedNotice.get(player.getUniqueId());
        if (previous == null || now - previous >= 150) {
            lastOccupiedNotice.put(player.getUniqueId(), now);
            PlatformMessages.send(player, GoldenEggOVO.TDCasino.text.CasinoMessages.component("machine.occupied"));
        }
    }

    private void hover() {
        var id = playingPlayer();
        var player = id == null ? null : Bukkit.getPlayer(id);
        ItemDisplay next = null;
        if (player != null && manager.canUse(player, this)) {
            var hit = ray(player, manager.blockDistance(player));
            if (hit != null
                    && !busy()
                    && rowAvailable(hit.target.row)
                    && available(hit.target.action)) next = hit.target.visual;
        }
        if (next != highlighted) {
            if (highlighted != null) highlighted.setGlowing(false);
            highlighted = next;
            if (!busy()) refresh();
            if (next != null) {
                next.setGlowing(true);
                next.setGlowColorOverride(Color.YELLOW);
            }
        }
    }

    public final void setStake(Player player, long amount) {
        if (!manager.contains(this) || !manager.canUse(player, this) || !nearSettings(player))
            throw new IllegalArgumentException("error.machine-distance");
        plugin.casino().validateStakeCents(amount);
        var state = players.data(player.getUniqueId());
        if (occupiedBy(player.getUniqueId()) && !canEditStake())
            throw new IllegalArgumentException("error.machine-busy");
        state.round.setStake(amount);
        manager.savePlayerData(owner, game(), player.getUniqueId(), state.round, state.resultState);
        if (player.getUniqueId().equals(displayedPlayer)) refresh();
    }

    public final long stake(Player player) {
        var playerRound = players.data(player.getUniqueId()).round;
        manager.resetInvalidConfiguredStake(playerRound);
        return playerRound instanceof StartedRound started ? started.configuredStake() : playerRound.stake();
    }

    public final void settings(Player player) {
        if (!manager.contains(this) || !manager.canUse(player, this) || !nearSettings(player)) return;
        plugin.casino().menus().settings(player, this);
    }

    public final boolean nearSettings(Player player) {
        var eye = player.getEyeLocation();
        var local =
                MachineGeometry.rotate(
                        eye.getX() - origin.getX(),
                        eye.getY() - origin.getY(),
                        eye.getZ() - origin.getZ(),
                        -origin.getYaw());
        var bounds = settingsBounds();
        double[] point = {local.x() / scale(), local.y() / scale(), local.z() / scale()};
        double distanceSquared = 0;
        for (int axis = 0; axis < 3; axis++) {
            double difference =
                    point[axis] - Math.clamp(point[axis], bounds.get(axis), bounds.get(axis + 3));
            distanceSquared += difference * difference * scale() * scale();
        }
        return distanceSquared <= 100;
    }

    public final void close() {
        try {
            try {
                round.abandon();
            } finally {
                playerData.settleFinishedRound();
                savePlayerData();
            }
        } finally {
            try {
                players.abandon();
            } finally {
                sounds.clear();
                plugin.machineSettings().unregister(this);
                for (Entity entity : parts) audience.remove(entity);
                parts.clear();
                modelDisplays.clear();
                translatedLabels.clear();
                targets.clear();
                buttons.clear();
                pressed.clear();
                buttonActions.clear();
                highlighted = null;
            }
        }
    }

    private record Target(
            String action,
            ItemDisplay visual,
            Matrix4d inverse,
            BoundingBox bounds,
            int row) {}

    record TargetHit(Target target, double distance) {}
}
