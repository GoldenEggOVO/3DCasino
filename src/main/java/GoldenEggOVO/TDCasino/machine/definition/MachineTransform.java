package GoldenEggOVO.TDCasino.machine.definition;

import GoldenEggOVO.TDCasino.text.CasinoMessages;
import org.joml.Quaternionf;
import org.joml.Vector3f;
public record MachineTransform(
        double x, double y, double z, double pitch, double yaw, double roll, double scale) {
    public static final MachineTransform IDENTITY = new MachineTransform(0, 0, 0, 0, 0, 0, 1);

    public MachineTransform {
        for (double value : new double[] {x, y, z, pitch, yaw, roll, scale}) {
            if (!Double.isFinite(value))
                throw new CasinoMessages.Failure("error.transform-finite");
        }
        if (scale < .001 || scale > 32)
            throw new CasinoMessages.Failure("error.transform-scale");
        if (Math.max(Math.max(Math.abs(pitch), Math.abs(yaw)), Math.abs(roll)) > 360)
            throw new CasinoMessages.Failure("error.vector-number", "field", "rotation");
        if (Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z)) > 64)
            throw new CasinoMessages.Failure("error.transform-position");
    }

    public Quaternionf rotation() {
        return new Quaternionf()
                .rotateZ((float) Math.toRadians(roll))
                .rotateY((float) Math.toRadians(yaw))
                .rotateX((float) Math.toRadians(pitch));
    }

    public Point apply(double px, double py, double pz) {
        var point = new Vector3f((float) (px * scale), (float) (py * scale), (float) (pz * scale));
        rotation().transform(point);
        return new Point(x + point.x, y + point.y, z + point.z);
    }

    public Point inverse(double px, double py, double pz) {
        var point = new Vector3f((float) (px - x), (float) (py - y), (float) (pz - z));
        rotation().conjugate().transform(point);
        return new Point(point.x / scale, point.y / scale, point.z / scale);
    }

    public record Point(double x, double y, double z) {}
}
