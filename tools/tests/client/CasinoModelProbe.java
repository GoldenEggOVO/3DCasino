import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.resources.model.cuboid.CuboidModel;

/** Run with an official client and its libraries on the classpath; no game window is needed. */
public final class CasinoModelProbe {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        int count = 0;
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                try (var reader = Files.newBufferedReader(file)) {
                    if (CuboidModel.fromStream(reader).geometry() == null)
                        throw new IllegalStateException("Missing geometry");
                } catch (RuntimeException exception) {
                    throw new IllegalStateException("Invalid model " + file, exception);
                }
                count++;
            }
        }
        if (count == 0) throw new IllegalStateException("No models found in " + directory);
        System.out.println("Parsed " + count + " Casino models");
    }
}
