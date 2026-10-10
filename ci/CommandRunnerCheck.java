import com.sysopbp.engineeringprops.CommandRunner;
import java.util.Arrays;

public class CommandRunnerCheck {
    public static void main(String[] args) throws Exception {
        CommandRunner.Result ok = CommandRunner.run(Arrays.asList("sh", "-c", "printf verified"), 3);
        check(ok.succeeded() && ok.output.equals("verified"), "normal output");
        CommandRunner.Result failure = CommandRunner.run(Arrays.asList("sh", "-c", "echo denied >&2; exit 7"), 3);
        check(!failure.succeeded() && failure.exitCode == 7 && failure.output.contains("denied"), "stderr and exit");
        CommandRunner.Result large = CommandRunner.run(Arrays.asList("sh", "-c", "head -c 600000 /dev/zero"), 3);
        check(large.succeeded() && large.truncated && large.output.length() == CommandRunner.OUTPUT_LIMIT, "bounded drain");
        long start = System.nanoTime();
        CommandRunner.Result timeout = CommandRunner.run(Arrays.asList("sh", "-c", "exec sleep 20"), 1);
        check(timeout.timedOut && !timeout.succeeded() && (System.nanoTime() - start) < 5_000_000_000L, "timeout");
        System.out.println("Command runner checks passed");
    }
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }
}
