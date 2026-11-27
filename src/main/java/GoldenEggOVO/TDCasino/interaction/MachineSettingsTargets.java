package GoldenEggOVO.TDCasino.interaction;

import GoldenEggOVO.TDCasino.TDCasinoPlugin;
import GoldenEggOVO.TDCasino.render.MachineGeometry;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Consumer;
public final class MachineSettingsTargets implements Listener {
    private record Target(UUID owner, Location origin, BoundingBox bounds, Consumer<Player> open) {}

    private final TDCasinoPlugin plugin;
    private final Map<Object, Target> targets = new HashMap<>();
    private final Map<UUID, Long> lastOpen = new HashMap<>();

    public MachineSettingsTargets(TDCasinoPlugin plugin) {
        this.plugin = plugin;
        GoldenEggOVO.TDCasino.Core.platform.PlatformEvents.register(plugin, this);
    }

    public void register(
            Object machine,
            UUID owner,
            Location origin,
            BoundingBox bounds,
            Consumer<Player> open) {
        targets.put(machine, new Target(owner, origin.clone(), bounds.clone(), open));
    }

    public void unregister(Object machine) {
        var removed = targets.remove(machine);
        if (removed != null && targets.values().stream().noneMatch(t -> t.owner.equals(removed.owner)))
            lastOpen.remove(removed.owner);
    }

    public static double distance(
            Location origin, BoundingBox bounds, Location eye, Vector direction) {
        var start =
                MachineGeometry.rotate(
                        eye.getX() - origin.getX(),
                        eye.getY() - origin.getY(),
                        eye.getZ() - origin.getZ(),
                        -origin.getYaw());
        var ray =
                MachineGeometry.rotate(
                        direction.getX(), direction.getY(), direction.getZ(), -origin.getYaw());
        var hit =
                bounds.rayTrace(
                        new Vector(start.x(), start.y(), start.z()),
                        new Vector(ray.x(), ray.y(), ray.z()),
                        5);
        return hit == null
                ? Double.POSITIVE_INFINITY
                : hit.getHitPosition().distance(new Vector(start.x(), start.y(), start.z()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR
                        && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        open(event.getPlayer(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void interactEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND)
            open(event.getPlayer(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void interactAtEntity(PlayerInteractAtEntityEvent event) {
        interactEntity(event);
    }

    private void open(Player p, Runnable cancel) {
        if (!p.isSneaking() || !plugin.allowed(p)) return;
        var eye = p.getEyeLocation();
        var direction = eye.getDirection();
        double nearest = 5.001;
        Target chosen = null;
        var block =
                p.getWorld()
                        .rayTraceBlocks(
                                eye, direction, 5, org.bukkit.FluidCollisionMode.NEVER, true);
        if (block != null) nearest = block.getHitPosition().distance(eye.toVector()) + .01;
        for (var target : targets.values()) {
            if (!target.origin.getWorld().equals(p.getWorld()) || !plugin.scheduler().isOwned(target.origin)) continue;
            double distance = distance(target.origin, target.bounds, eye, direction);
            if (distance < nearest) {
                nearest = distance;
                chosen = target;
            }
        }
        if (chosen != null) {
            cancel.run();
            long now = System.currentTimeMillis();
            if (now - lastOpen.getOrDefault(p.getUniqueId(), 0L) < 150) return;
            lastOpen.put(p.getUniqueId(), now);
            chosen.open.accept(p);
        }
    }

    public void close() {
        targets.clear();
        lastOpen.clear();
        HandlerList.unregisterAll(this);
    }
}
