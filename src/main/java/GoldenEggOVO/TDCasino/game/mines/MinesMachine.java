package GoldenEggOVO.TDCasino.game.mines;

import GoldenEggOVO.TDCasino.Core.platform.PlatformMessages;
import GoldenEggOVO.TDCasino.text.CasinoMessages;
import GoldenEggOVO.TDCasino.render.MachineGeometry;
import GoldenEggOVO.TDCasino.machine.*;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import GoldenEggOVO.TDCasino.machine.definition.ButtonDefinition;
import GoldenEggOVO.TDCasino.machine.definition.MachineTransform;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.BoundingBox;

import java.security.SecureRandom;
import java.util.*;

public final class MinesMachine extends GameMachine<MinesRound> {
    private final List<ItemDisplay> cells = new ArrayList<>();
    private final Map<ItemDisplay, String> faces = new HashMap<>();
    private final Map<ItemDisplay, Flip> flips = new HashMap<>();
    private TextDisplay setting;
    private final boolean refined;
    private static final double FLIP_INTERPOLATION_MARGIN = .03;
    private static final MachineTransform CONSOLE = new MachineTransform(0, .67, 1.96, -35, 0, 0, 1);

    public MinesMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, () -> new MinesRound(new SecureRandom()));
        refined = definition.equals(MachineDefinition.builtin("mines"));
    }

    public static ButtonDefinition vanillaButton(String action) {
        boolean small = action.equals("minus") || action.equals("plus");
        double x = (action.equals("minus") || action.equals("start") ? -1 : 1) * (small ? .78 : .60);
        var point = CONSOLE.apply(x, small ? .51 : .075, .065);
        return ButtonDefinition.at(point.x(), point.y(), point.z(), small ? .31 : 1.03, -35, small ? .34 : .86);
    }

    @Override
    protected void buildGame() {
        body(refined ? "mines_machine_sloped" : "mines_machine");
        String[] actions = {"minus", "plus", "start", "cash"};
        String[] models = {"minus", "plus", "play", "cashout"};
        for (int i = 0; i < actions.length; i++)
            button(actions[i], "casino_button_" + models[i], refined ? vanillaButton(actions[i]) : definition.button(actions[i]));
        for (int i = 0; i < 25; i++) {
            var point = MachineGeometry.mineCell(i);
            var cell = model("mine_hidden", point.x(), refined ? 1.115 : point.y(), point.z(), .32);
            if (refined) pose(cell, cellPose(0));
            cells.add(cell);
            faces.put(cell, "mine_hidden");
            if (refined) hitDisplay("cell:" + i, cell, new BoundingBox(-.5, -.5, -.5, .5, .5, .5), -1);
            else hit("cell:" + i, cell, point.x(), point.y() - .16, point.z(), .43, .33, -1);
        }
        if (refined) {
            var point = CONSOLE.apply(0, .56, .04);
            setting = text(point.x(), point.y(), point.z(), .30);
            pose(setting, new org.bukkit.util.Transformation(new org.joml.Vector3f(), CONSOLE.rotation(),
                    new org.joml.Vector3f(.30f), new org.joml.Quaternionf()));
        } else setting = text(-.55, 1.43, 1.62, .23);
    }

    private org.bukkit.util.Transformation cellPose(double pitch) {
        var pose = MachineGeometry.itemPose(.32, pitch, 0);
        if (refined) {
            pose.getScale().set(.48f, .13f, .46f);
            pose.getTranslation().y = (float) (Math.max(0,
                    .065 * Math.abs(Math.cos(pitch)) + .23 * Math.abs(Math.sin(pitch)) - .065)
                    + FLIP_INTERPOLATION_MARGIN * Math.abs(Math.sin(pitch)));
        }
        return pose;
    }

    @Override
    protected Long cashoutAmount() { return round.active() ? (round.mines() != null && round.mines().safeCount() > 0 ? round.cashoutAmount() : null) : null; }

    @Override
    protected boolean available(String action) {
        return switch (action) {
            case "minus" -> !round.active() && round.mineCount() > 1;
            case "plus" -> !round.active() && round.mineCount() < 24;
            case "start" -> !round.active();
            case "cash" -> round.active() && round.mines().safeCount() > 0;
            default ->
                    action.startsWith("cell:")
                            && round.active()
                            && (round.mines().revealed()
                                            & (1 << Integer.parseInt(action.substring(5))))
                                    == 0;
        };
    }

    @Override
    protected void action(String action) {
        switch (action) {
            case "minus" -> round.changeMines(-1);
            case "plus" -> round.changeMines(1);
            case "start" -> round.start(System.currentTimeMillis());
            case "cash" -> round.cash(System.currentTimeMillis());
            default -> round.reveal(Integer.parseInt(action.substring(5)));
        }
        refresh();
    }

    @Override
    protected void refresh() {
        var state = round.mines();
        for (int i = 0; i < cells.size(); i++) {
            boolean shown =
                    state != null && ((state.revealed() & (1 << i)) != 0 || !round.active());
            String face =
                    !shown
                            ? "mine_hidden"
                            : (state.mask() & (1 << i)) != 0 ? "mine_bomb" : "mine_gem";
            var cell = cells.get(i);
            if (!face.equals(faces.put(cell, face))) flips.put(cell, new Flip(face, age));
        }
        PlatformMessages.text(setting, CasinoMessages.component("mines.count", "count", round.mineCount()));
    }

    @Override
    protected boolean busy() { return round.finished() && !flips.isEmpty(); }

    @Override
    protected boolean revealing() { return !flips.isEmpty(); }

    @Override
    protected void animate() {
        for (var iterator = flips.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var flip = entry.getValue();
            double t = Math.min(1, (age - flip.start) / 8.0);
            if (age - flip.start == 4 && flip.model.equals("mine_gem") && round.active())
                sound(Sound.BLOCK_NOTE_BLOCK_PLING, .2f, 1.15f);
            if (age - flip.start == 4) entry.getKey().setItemStack(model(flip.model));
            pose(entry.getKey(), cellPose(flipPitch(t, flip.model.equals("mine_bomb"))));
            entry.getKey().setInterpolationDelay(0);
            if (t >= 1) iterator.remove();
        }
    }

    static double flipPitch(double progress, boolean bomb) {
        double eased = progress * progress * (3 - 2 * progress);
        return (bomb ? -1.3 : 1.3) * Math.sin(Math.PI * eased);
    }

    private record Flip(String model, int start) {}
}
