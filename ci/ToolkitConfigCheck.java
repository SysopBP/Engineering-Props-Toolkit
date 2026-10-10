import com.sysopbp.engineeringprops.CommandRunner;
import com.sysopbp.engineeringprops.ToolkitConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ToolkitConfigCheck {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("engineering config's ");
        Path config = directory.resolve("config.conf");
        String original = "# User configuration\nKEEP_ME=1\nGRAPHICS_PROFILE=0\nGRAPHICS_PROFILE=0\n";
        Files.write(config, original.getBytes(StandardCharsets.UTF_8));
        check(run(ToolkitConfig.update(directory.toString(), "GRAPHICS_PROFILE", "1")).succeeded(), "update");
        String updated = Files.readString(config);
        check(updated.contains("KEEP_ME=1") && updated.contains("# User configuration"), "preserve unrelated values");
        check(updated.split("GRAPHICS_PROFILE=1", -1).length == 2, "deduplicate key");
        check(Files.readString(directory.resolve("config.conf.app-backup")).equals(original), "backup");
        check(run(ToolkitConfig.restore(directory.toString())).succeeded() && Files.readString(config).equals(original), "restore");
        check(run(ToolkitConfig.update(directory.toString(), "GRAPHICS_OVERDRAW", "0")).succeeded()
                && Files.readString(config).contains("GRAPHICS_OVERDRAW=0"), "append absent key");
        try { ToolkitConfig.update(directory.toString(), "bad; echo injected", "1"); throw new AssertionError("invalid key accepted"); }
        catch (IllegalArgumentException expected) { }
        try { ToolkitConfig.update(directory.toString(), "GRAPHICS_PROFILE", "1; echo injected"); throw new AssertionError("invalid value accepted"); }
        catch (IllegalArgumentException expected) { }
        Files.delete(config);
        check(!run(ToolkitConfig.update(directory.toString(), "GRAPHICS_PROFILE", "1")).succeeded(), "missing config fails");
        System.out.println("Toolkit configuration checks passed");
    }
    private static CommandRunner.Result run(String command) throws Exception {
        return CommandRunner.run(Arrays.asList("sh", "-c", command), 3);
    }
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
}
