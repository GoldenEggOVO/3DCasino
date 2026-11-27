package GoldenEggOVO.TDCasino.render;
public final class MachinePanelGeometry {
    public static final double HILO_PITCH = -Math.toRadians(35);

    public static double dragonAngle(int column) {
        return Math.toRadians(-18 + 12 * column);
    }

    public static MachineGeometry.Point dragonCell(int row, int column) {
        double angle = dragonAngle(column);
        return new MachineGeometry.Point(
                1.65 * Math.sin(angle), 1.25 + row * .32, .40 + 1.65 * (Math.cos(angle) - 1));
    }

    public static double sliderX(int threshold) {
        return sliderX((double) threshold);
    }

    public static double sliderX(double threshold) {
        return -1.1 + Math.clamp(threshold, 0, 100) * 2.2 / 100;
    }

    public static int threshold(double x) {
        return (int) Math.clamp(Math.round((x + 1.1) * 100 / 2.2), 1, 98);
    }

    public static int aimedThreshold(MachineGeometry.Point eye, MachineGeometry.Point direction) {
        double ny = -Math.sin(HILO_PITCH), nz = Math.cos(HILO_PITCH);
        double divisor = direction.y() * ny + direction.z() * nz;
        if (Math.abs(divisor) < 1e-6) return -1;
        double t = ((1.1 - eye.y()) * ny - eye.z() * nz) / divisor;
        if (t < 0 || t > 6) return -1;
        double y = eye.y() + t * direction.y() - 1.1;
        double z = eye.z() + t * direction.z();
        double along = y * Math.cos(HILO_PITCH) + z * Math.sin(HILO_PITCH);
        if (Math.abs(along) > .75) return -1;
        return threshold(eye.x() + t * direction.x());
    }

    private MachinePanelGeometry() {}
}
