package GoldenEggOVO.TDCasino.game.plinko;

public final class PlinkoPath {
    public record Point(double x, double y) {}

    public static Point at(int path, double progress) {
        double time = Math.max(0, Math.min(12, progress));
        int complete = (int) time;
        double x = 0;
        for (int i = 0; i < complete; i++) {
            x += (path & (1 << i)) == 0 ? -.18 : .18;
        }
        double fraction = time - complete;
        if (complete < 12) {
            x += ((path & (1 << complete)) == 0 ? -.18 : .18) * fraction;
        }
        double rebound = complete == 0 ? .27 : .55;
        return new Point(x, 4.5 - time * .27 + rebound * fraction * (1 - fraction));
    }

    public static int slot(int path) {
        return Integer.bitCount(path & 4095);
    }
}
