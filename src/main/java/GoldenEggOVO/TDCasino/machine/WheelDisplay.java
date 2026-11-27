package GoldenEggOVO.TDCasino.machine;

import GoldenEggOVO.TDCasino.Core.render.DisplayMovement;
import GoldenEggOVO.TDCasino.render.MachineGeometry;
import java.util.List;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class WheelDisplay {
    private final GameMachine<?> machine;
    private final ItemDisplay disc;
    private final ItemDisplay pointer;
    private final List<TextDisplay> labels;
    private final double labelRadius;
    private final float labelSize;
    private double angle, spinFrom, spinTo;
    private int lastClickTick = -3;
    private double pointerAngle;

    public WheelDisplay(GameMachine<?> machine, ItemDisplay disc, ItemDisplay pointer, List<TextDisplay> labels,
            double labelRadius, float labelSize) {
        this.machine = machine;
        this.disc = disc;
        this.pointer = pointer;
        this.labels = List.copyOf(labels);
        this.labelRadius = labelRadius;
        this.labelSize = labelSize;
    }

    public void refresh(boolean finished, int segment) {
        tiltPointer(0);
        rotate(finished ? segmentAngle(segment) : angle);
    }

    public void planSpin(int segment) {
        spinFrom = angle;
        double delta = (segmentAngle(segment) - spinFrom) % (2 * Math.PI);
        if (delta < 0) delta += 2 * Math.PI;
        spinTo = spinFrom + 10 * Math.PI + delta;
    }

    public void animate(double progress) {
        double next = spinFrom + (spinTo - spinFrom) * spinTravel(progress);
        boolean crossedPeg = Math.floor(angle * labels.size() / (2 * Math.PI))
                != Math.floor(next * labels.size() / (2 * Math.PI));
        boolean click = crossedPeg && machine.age - lastClickTick >= 3;
        if (click) {
            machine.sound(Sound.BLOCK_WOOD_HIT, .16f, .95f + (float) (1 - progress) * .3f);
            lastClickTick = machine.age;
        }
        double deflection = click ? .24 : pointerAngle * .58;
        tiltPointer(progress >= 1 || deflection < .002 ? 0 : deflection);
        rotate(next);
    }

    static double spinTravel(double progress) {
        // Integral of a continuous speed curve: spin up, coast, brake to rest.
        double up = .12, brake = .30, distance = (1 + brake - up) / 2;
        if (progress <= up) return progress * progress / (2 * up * distance);
        if (progress <= brake) return (progress - up / 2) / distance;
        double remaining = 1 - progress;
        return 1 - remaining * remaining / (2 * (1 - brake) * distance);
    }

    public void finishSpin() {
        tiltPointer(0);
        rotate(spinTo);
    }

    private void tiltPointer(double radians) {
        if (pointerAngle == radians) return;
        pointerAngle = radians;
        var pose = MachineGeometry.itemPose(4, 0, 0);
        pose.getLeftRotation().rotateZ((float) -radians);
        machine.pose(pointer, pose);
        pointer.setInterpolationDelay(0);
    }

    private double segmentAngle(int segment) {
        return 2 * Math.PI * (segment + .5) / labels.size();
    }

    private void rotate(double next) {
        angle = next;
        var transform = MachineGeometry.itemPose(4, 0, 0);
        transform.getLeftRotation().rotateZ((float) angle);
        machine.pose(disc, transform);
        disc.setInterpolationDelay(0);
        for (int i = 0; i < labels.size(); i++) {
            double theta = segmentAngle(i) - angle;
            var label = labels.get(i);
            DisplayMovement.move(label, machine.at(Math.sin(theta) * labelRadius,
                    2 + Math.cos(theta) * labelRadius - .02, .321));
            machine.pose(label, new Transformation(new Vector3f(),
                    new Quaternionf().rotateZ((float) -theta),
                    new Vector3f(labelSize), new Quaternionf()));
        }
    }
}
