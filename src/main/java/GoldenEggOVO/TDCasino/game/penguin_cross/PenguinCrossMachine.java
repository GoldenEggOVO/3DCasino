package GoldenEggOVO.TDCasino.game.penguin_cross;

import GoldenEggOVO.TDCasino.Core.render.DisplayMovement;
import GoldenEggOVO.TDCasino.render.MachineGeometry;
import GoldenEggOVO.TDCasino.machine.*;
import GoldenEggOVO.TDCasino.machine.definition.MachineDefinition;
import GoldenEggOVO.TDCasino.machine.definition.ButtonDefinition;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import java.security.SecureRandom;
import java.util.*;
public final class PenguinCrossMachine extends AnimatedMachine<PenguinCrossRound> {

    private static final int STEP_TICKS = 30, TAKEOFF_TICK = 4, LANDING_TICK = 18, SPLASH_TICK = 24;
    final List<ItemDisplay> figures = new ArrayList<>(), ice = new ArrayList<>();
    int previousStage;

    public PenguinCrossMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, () -> new PenguinCrossRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("penguin_cross_machine");
        boolean builtin = definition.equals(MachineDefinition.builtin("penguin_cross"));
        for (String action : List.of("play", "step", "cash")) {
            var original = definition.button(action);
            var pose = original.transform();
            button(action, "casino_button_" + (action.equals("cash") ? "cashout" : action), builtin
                    ? ButtonDefinition.at(pose.x(), pose.y(), pose.z() + .20,
                            original.width(), pose.pitch(), original.size())
                    : original);
        }
        figures.add(model("penguin_cross_penguin", -1.1, 1.08, 0, 4));
        for (int i = 0; i < 8; i++) {
            var block =
                    item(
                            new ItemStack(Material.PACKED_ICE),
                            -1.1 + (i + 1) * 2.2 / 8,
                            1,
                            0,
                            .22,
                            0);
            var pose = MachineGeometry.itemPose(.22, 0, 0);
            pose.getScale().set(.22f, .16f, .30f);
            pose(block, pose);
            ice.add(block);
        }
    }

    @Override
    protected Long cashoutAmount() { return round.active() ? (round.steps() > 0 ? round.payout() : null) : null; }

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
        boolean fallen = round.finished() && round.lastRoll() < 20;
        DisplayMovement.move(figures.getFirst(),
                        at(
                                -1.1 + 2.2 * (round.steps() + (fallen ? 1 : 0)) / 8.0,
                                fallen ? .55 : 1.08,
                                0));
        posePenguin(fallen ? 0 : 1, 0);
        for (int i = 0; i < ice.size(); i++)
            DisplayMovement.move(ice.get(i),
                            at(
                                    -1.1 + (i + 1) * 2.2 / 8,
                                    fallen && i == round.steps() ? .45 : 1,
                                    0));
    }

    @Override
    protected void movementSound(double progress) {
        if (age - animationStart == LANDING_TICK) sound(Sound.BLOCK_SNOW_STEP, .22f, 1.1f);
        if (age - animationStart == SPLASH_TICK && round.finished() && round.lastRoll() < 20)
            sound(Sound.ENTITY_GENERIC_SPLASH, .22f, 1.2f);
    }

    @Override
    protected void animateFrame(double progress, double ease) {
        boolean fallen = round.finished() && round.lastRoll() < 20;
        double tick = progress * STEP_TICKS;
        double crouch = pulse(tick / TAKEOFF_TICK);
        double flight = Math.clamp((tick - TAKEOFF_TICK) / (LANDING_TICK - TAKEOFF_TICK), 0, 1);
        double landing = Math.clamp((tick - LANDING_TICK) / (STEP_TICKS - LANDING_TICK), 0, 1);
        double bob = fallen ? 0 : pulse(landing);
        double sink = fallen ? smooth(landing) : 0;
        double jump = 4 * flight * (1 - flight);
        double x = -1.1 + 2.2 * (previousStage + smooth(flight)) / 8;
        DisplayMovement.move(figures.getFirst(), at(x, 1.08 + .22 * jump - .038 * bob - .53 * sink, 0));
        double submerge = fallen ? smooth(Math.clamp((tick - SPLASH_TICK) / (STEP_TICKS - SPLASH_TICK), 0, 1)) : 0;
        posePenguin((1 - .09 * crouch - .045 * bob) * (1 - submerge),
                -.12 * crouch + .18 * jump * (1 - 2 * flight) + .08 * bob + .32 * sink);
        if (tick >= LANDING_TICK) {
            double floeX = -1.1 + 2.2 * (previousStage + 1) / 8;
            DisplayMovement.move(ice.get(previousStage), at(floeX, 1 - .038 * bob - .55 * sink, 0));
        }
    }

    private void posePenguin(double scale, double tilt) {
        var transform = MachineGeometry.itemPose(4 * scale, 0, 0);
        transform.getLeftRotation().rotateY((float) (Math.PI / 2)).rotateX((float) tilt);
        pose(figures.getFirst(), transform);
        figures.getFirst().setInterpolationDelay(0);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double pulse(double t) {
        if (t <= 0 || t >= 1) return 0;
        double wave = Math.sin(Math.PI * t);
        return wave * wave;
    }

    @Override
    protected boolean shouldAnimate(String action) {
        return action.equals("step");
    }

    @Override
    protected void beforeAction(String action) {
        previousStage = round.steps();
    }
}
