package GoldenEggOVO.TDCasino;

import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDirFactory;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkspaceTempFactory implements TempDirFactory {
    @Override public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context) throws Exception {
        Path parent = Path.of(System.getProperty("basedir", System.getProperty("user.dir"))).toAbsolutePath().normalize().resolve("target/test-temp");
        Files.createDirectories(parent);
        return Files.createTempDirectory(parent, "junit-");
    }
}
