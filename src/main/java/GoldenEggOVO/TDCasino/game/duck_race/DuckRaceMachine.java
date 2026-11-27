package GoldenEggOVO.TDCasino.game.duck_race;

import GoldenEggOVO.TDCasino.Core.render.DisplayMovement;
import GoldenEggOVO.TDCasino.render.MachineGeometry;
import GoldenEggOVO.TDCasino.machine.*;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import GoldenEggOVO.TDCasino.machine.definition.ButtonDefinition;

import org.bukkit.*;
import org.bukkit.entity.*;

import java.security.SecureRandom;
import java.util.*;
public final class DuckRaceMachine extends AnimatedMachine<DuckRaceRound> {
    private static final double START_Z = .7;
    private static final double FINISH_Z = -2.3;

    final List<ItemDisplay> figures = new ArrayList<>();

    public DuckRaceMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, () -> new DuckRaceRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("duck_race_machine");
        boolean builtin = definition.equals(MachineDefinition.builtin("duck_race"));
        for (int i = 0; i < 4; i++) {
            var original = definition.button("select:" + i);
            var pose = original.transform();
            button("select:" + i, "duck_race_button_" + (i + 1), builtin
                    ? ButtonDefinition.at(pose.x(), pose.y(), pose.z() + .22,
                            original.width(), pose.pitch(), original.size())
                    : original);
        }
        button("play", "casino_button_play", builtin
                ? ButtonDefinition.at(0, .31, 1.73, .65, -35, 1.2)
                : definition.button("play"));
        for (int i = 0; i < 4; i++)
            figures.add(model("duck_race_duck_" + (i + 1), -.9 + i * .6, 1.08, START_Z, 4));
    }

    @Override
    protected List<Double> settingsBounds() {
        if (!definition.equals(MachineDefinition.builtin("duck_race"))) return super.settingsBounds();
        var bounds = new ArrayList<>(super.settingsBounds());
        bounds.set(2, -2.85);
        return bounds;
    }

    private double finishZ(int lane) {
        return lane == round.winner() ? FINISH_Z : FINISH_Z + .8 + lane * .18;
    }

    @Override
    protected boolean available(String action) {
        return round.available(action);
    }

    @Override
    protected void perform(String action) {
        round.action(action);
    }

    @Override
    protected void refresh() {
        selectedButtons(a -> a.equals("select:" + round.choice()));
        for (int i = 0; i < 4; i++) {
            DisplayMovement.move(figures.get(i),
                            at(
                                    -.9 + i * .6,
                                    1.08,
                                    round.finished()
                                            ? finishZ(i)
                                            : START_Z));
            pose(figures.get(i), MachineGeometry.itemPose(4, 0, 0));
            figures.get(i).setInterpolationDelay(0);
        }
    }

    @Override
    protected void movementSound(double progress) {
        if (age % 8 == 0) sound(Sound.BLOCK_WOOD_STEP, .12f, 1.1f + (float) progress * .4f);
    }

    @Override
    protected void animateFrame(double progress, double ease) {
        double travel = progress * progress * (3 - 2 * progress);
        double envelope = 4 * travel * (1 - travel);
        for (int i = 0; i < 4; i++) {
            double stroke = Math.sin(progress * 9 * Math.PI + i * 1.6);
            double z = START_Z - 2 * travel + (finishZ(i) - START_Z + 2) * travel * travel;
            var duck = figures.get(i);
            DisplayMovement.move(duck, at(-.9 + i * .6, 1.08 + .055 * envelope * stroke * stroke, z));
            var tilt = MachineGeometry.itemPose(4, .065 * envelope * stroke * stroke, 0);
            tilt.getLeftRotation().rotateZ((float) (.09 * envelope * stroke));
            pose(duck, tilt);
            duck.setInterpolationDelay(0);
        }
    }
}
