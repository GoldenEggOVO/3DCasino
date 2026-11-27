package GoldenEggOVO.TDCasino.game.dragon_tower;

import GoldenEggOVO.TDCasino.render.MachineGeometry;
import GoldenEggOVO.TDCasino.render.MachinePanelGeometry;
import GoldenEggOVO.TDCasino.machine.*;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import GoldenEggOVO.TDCasino.machine.definition.ButtonDefinition;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.bukkit.util.BoundingBox;

import java.security.SecureRandom;
import java.util.*;
public final class DragonTowerMachine extends GameMachine<DragonTowerRound> {

    final List<ItemDisplay> tiles = new ArrayList<>();
    private final boolean compact;
    private final Map<Integer, Integer> revealStarted = new HashMap<>();
    private DragonTowerRound displayedRound;

    public DragonTowerMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, () -> new DragonTowerRound(new SecureRandom()));
        compact = definition.equals(MachineDefinition.builtin("dragon_tower"));
    }

    public static ButtonDefinition vanillaButton(String action) {
        return ButtonDefinition.at(action.equals("play") ? -.33 : .33, .12, .63, .60, -10, 1);
    }

    public static MachineGeometry.Point vanillaCell(int row, int column) {
        var point = MachinePanelGeometry.dragonCell(row, column);
        return new MachineGeometry.Point(point.x(), point.y()-.5, point.z());
    }

    @Override
    protected List<Double> settingsBounds() {
        return compact ? List.of(-.9, 0.0, -.32, .9, 2.85, .84) : super.settingsBounds();
    }

    @Override
    protected void buildGame() {
        body(compact ? "dragon_tower_machine_compact" : "dragon_tower_machine");
        for (String action : List.of("play", "cash"))
            button(action, "casino_button_" + (action.equals("cash") ? "cashout" : action),
                    compact ? vanillaButton(action) : definition.button(action));
        for (int row = 0; row < 6; row++)
            for (int column = 0; column < 4; column++) {
                var point = compact ? vanillaCell(row, column) : MachinePanelGeometry.dragonCell(row, column);
                var tile =
                        item(
                                compact ? model("dragon_tile_hidden") : new ItemStack(Material.GRAY_TERRACOTTA),
                                point.x(),
                                point.y(),
                                point.z(),
                                compact ? 4 : .28,
                                0);
                pose(tile, dragonPose(column));
                tiles.add(tile);
                hitDisplay("select:" + column, tile, compact
                        ? new BoundingBox(-.035, -.035, -.035, .035, .035, .035)
                        : new BoundingBox(-.5025, -.5025, -.5025, .5025, .5025, .5025), row);
            }
    }

    @Override
    protected Long cashoutAmount() { return round.active() ? (round.floors() > 0 ? round.payout() : null) : null; }

    @Override
    protected boolean available(String action) {
        return round.available(action);
    }

    @Override
    protected void action(String action) {
        int previous = round.floors();
        int revealed = round.traps().size();
        round.action(action);
        if (action.equals("play")) revealStarted.clear();
        if (action.startsWith("select:") && round.traps().size() > revealed)
            revealStarted.put(revealed * 4 + Integer.parseInt(action.substring(7)), age);
        if (round.floors() > previous && round.active())
            sound(Sound.BLOCK_NOTE_BLOCK_PLING, .23f, 1f + round.floors() * .08f);
        refresh();
    }

    @Override
    protected void refresh() {
        if (displayedRound != round) {
            revealStarted.clear();
            displayedRound = round;
        }
        var traps = round.traps();
        for (int i = 0; i < tiles.size(); i++) {
            int row = i / 4, column = i % 4;
            boolean revealed = row < traps.size();
            tiles.get(i).setItemStack(compact
                            ? model(!revealed ? "dragon_tile_hidden"
                                    : traps.get(row) == column ? "dragon_tile_trap" : "dragon_tile_safe")
                            :
                            new ItemStack(
                                    revealed
                                            ? (traps.get(row) == column
                                                    ? Material.TNT
                                                    : Material.EMERALD_BLOCK)
                                            : Material.GRAY_TERRACOTTA));
            pose(tiles.get(i), revealPose(i));
            tiles.get(i).setGlowing(round.active() && row == round.floors());
            tiles.get(i).setGlowColorOverride(Color.YELLOW);
        }
    }

    Transformation dragonPose(int column) {
        var pose = MachineGeometry.itemPose(compact ? 4 : .28, 0, 0);
        pose.getLeftRotation().rotateY((float) MachinePanelGeometry.dragonAngle(column));
        return pose;
    }

    private Transformation revealPose(int index) {
        var pose = dragonPose(index % 4);
        Integer start = revealStarted.get(index);
        if (start == null) return pose;
        double progress = Math.clamp((age - start) / 6.0, 0, 1);
        double lift = Math.pow(Math.sin(progress * Math.PI), 2);
        boolean trap = round.traps().get(index / 4) == index % 4;
        var normal = new Vector3f(0, 0, (float) ((trap ? .055 : .04) * lift))
                .rotate(pose.getLeftRotation());
        pose.getTranslation().add(normal);
        pose.getLeftRotation().rotateZ((float) ((trap ? -.14 : .07) * lift));
        return pose;
    }

    @Override
    protected void animate() {
        for (var iterator = revealStarted.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (age - entry.getValue() >= 6) {
                pose(tiles.get(entry.getKey()), dragonPose(entry.getKey() % 4));
                iterator.remove();
            } else pose(tiles.get(entry.getKey()), revealPose(entry.getKey()));
            tiles.get(entry.getKey()).setInterpolationDelay(0);
        }
    }

    @Override
    protected boolean rowAvailable(int row) {
        return row < 0 || row == round.floors();
    }
}
