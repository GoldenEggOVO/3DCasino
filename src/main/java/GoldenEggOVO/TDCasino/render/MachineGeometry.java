package GoldenEggOVO.TDCasino.render;

import GoldenEggOVO.TDCasino.machine.definition.ButtonDefinition;

public final class MachineGeometry {
    public static org.joml.Matrix4f nativePose(org.bukkit.util.Transformation pose) {
        return new org.joml.Matrix4f().translation(pose.getTranslation()).rotate(pose.getLeftRotation())
                .scale(pose.getScale()).rotate(pose.getRightRotation()).rotateY((float)Math.PI);
    }

    public static double machineScale(String kind) {
        return kind.equals("mines") ? 2.0 / 3.5 : kind.equals("crash") ? .8 : 1;
    }

    public static org.bukkit.util.Transformation itemPose(
            double scale, double pitch, double inward) {
        return new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0, 0, (float) inward),
                new org.joml.Quaternionf().rotateX((float) pitch),
                new org.joml.Vector3f((float) scale),
                new org.joml.Quaternionf().rotateY((float) Math.PI));
    }

    public static org.bukkit.util.Transformation buttonPose(
            ButtonDefinition button,
            double machineScale, double press) {
        var rotation = button.transform().rotation();
        double factor = machineScale * button.transform().scale();
        var translation = new org.joml.Vector3f(0, 0, (float) (-press * factor));
        rotation.transform(translation);
        return new org.bukkit.util.Transformation(
                translation,
                rotation,
                new org.joml.Vector3f(
                        (float) (4 * button.width() * factor / .6),
                        (float) (4 * button.size() * factor),
                        (float) (4 * button.size() * factor)),
                new org.joml.Quaternionf().rotateY((float) Math.PI));
    }

    public static Point panelPoint(double x, double y, double z, double pitch) {
        return new Point(
                x,
                y * Math.cos(pitch) - z * Math.sin(pitch),
                y * Math.sin(pitch) + z * Math.cos(pitch));
    }

    public record Point(double x, double y, double z) {}

    public static Point rotate(double x, double y, double z, float yaw) {
        double a = Math.toRadians(yaw);
        return new Point(x * Math.cos(a) - z * Math.sin(a), y, x * Math.sin(a) + z * Math.cos(a));
    }

    public static Point mineCell(int i) {
        return new Point((i % 5 - 2) * .53, 1.29, -1.02 + (i / 5) * .51);
    }

    public static double cardX(int i, int n) {
        return (i - (n - 1) / 2.0) * Math.min(.43, 2.4 / Math.max(1, n - 1));
    }

    public static double rocketHeight(int multiplier) {
        double distance = Math.log(Math.max(100, multiplier) / 100.0);
        return 1.65 + distance / (1 + distance);
    }
}
