package tools;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A minimal assertion harness used ONLY by the offline verification runner.
 *
 * <p>The project's real tests are JUnit 5 (see {@code backend/src/test/java}).
 * This exists because the sandbox in which the project was assembled had no
 * access to Maven Central and therefore could not resolve the JUnit artifacts,
 * yet the recommendation engine still had to be genuinely executed and asserted
 * rather than merely compiled. It intentionally mirrors the JUnit assertions it
 * stands in for so the two test bodies read the same.
 */
public final class MiniTest {

    private static final List<String> FAILURES = new ArrayList<>();
    private static int passed;
    private static int total;
    private static String currentSuite = "";

    private MiniTest() {
    }

    public static void suite(String name) {
        currentSuite = name;
        System.out.println("\n\u001b[1m" + name + "\u001b[0m");
    }

    public static void check(String name, BooleanSupplier condition) {
        total++;
        boolean ok;
        String detail = "";
        try {
            ok = condition.getAsBoolean();
        } catch (RuntimeException ex) {
            ok = false;
            detail = " threw " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
        report(name, ok, detail);
    }

    public static void assertTrue(String name, boolean condition) {
        total++;
        report(name, condition, "");
    }

    public static void assertEquals(String name, Object expected, Object actual) {
        total++;
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        report(name, ok, ok ? "" : " expected=" + expected + " actual=" + actual);
    }

    public static void assertClose(String name, double expected, double actual, double epsilon) {
        total++;
        boolean ok = Math.abs(expected - actual) <= epsilon;
        report(name, ok, ok ? "" : " expected=" + expected + " actual=" + actual);
    }

    public static void assertInRange(String name, double value, double min, double max) {
        total++;
        boolean ok = value >= min && value <= max;
        report(name, ok, ok ? "" : " value=" + value + " not in [" + min + ", " + max + "]");
    }

    public static void assertThrows(String name, Class<? extends Throwable> expected, Runnable action) {
        total++;
        try {
            action.run();
            report(name, false, " no exception thrown");
        } catch (Throwable thrown) {
            boolean ok = expected.isInstance(thrown);
            report(name, ok, ok ? "" : " threw " + thrown.getClass().getSimpleName());
        }
    }

    private static void report(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("  \u001b[32mPASS\u001b[0m  " + name);
        } else {
            FAILURES.add(currentSuite + " > " + name + detail);
            System.out.println("  \u001b[31mFAIL\u001b[0m  " + name + detail);
        }
    }

    public static int summarise() {
        System.out.println("\n" + "=".repeat(64));
        System.out.printf("%d/%d assertions passed%n", passed, total);
        if (!FAILURES.isEmpty()) {
            System.out.println("\nFailures:");
            FAILURES.forEach(f -> System.out.println("  - " + f));
        }
        System.out.println("=".repeat(64));
        return FAILURES.isEmpty() ? 0 : 1;
    }
}
