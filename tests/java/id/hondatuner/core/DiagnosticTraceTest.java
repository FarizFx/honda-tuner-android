package id.hondatuner.core;

/** Error clipboard tails must keep their version and final multiline failure. */
final class DiagnosticTraceTest {
    static int runAll() {
        errorTail(); System.out.println("OK compact clipboard retains app identity and final multiline failure within its budget");
        unstructuredTail(); System.out.println("OK compact legacy trace stays bounded and retains its header and final error");
        return 2;
    }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private static void errorTail() {
        DiagnosticLog log = new DiagnosticLog(2000, 256 * 1024);
        for (int i = 0; i < 500; i++) log.debug("USB RX", "Packet " + i + " " + repeat('A', 180));
        log.error("USB SESSION", new TransportException("Status RX unavailable"));
        String header = "USB SESSION · Status RX unavailable\nHonda Tuner 1.1.5-debug\nECU=0102450F01\n\n";
        String full = header + log.text(0), compact = DiagnosticLog.compactTrace(full, 120, 32768);
        check(compact.length() <= 32768 && compact.startsWith(header), "Compact log lost its header or exceeded the clipboard budget");
        check(!compact.contains("Packet 0 ") && compact.contains("Packet 499 "), "Compact log kept old entries instead of the tail");
        check(compact.contains("#501 ERROR [USB SESSION]") && compact.contains("TransportException: Status RX unavailable") &&
                compact.contains("\tat id.hondatuner.core.DiagnosticTraceTest.errorTail"), "Final failure or stack trace was cut away");
        String small = header + "Short error\n";
        check(DiagnosticLog.compactTrace(small, 120, 32768).equals(small), "Short trace was changed unnecessarily");
    }
    private static void unstructuredTail() {
        String trace = "Honda Tuner legacy header\n" + repeat('x', 40000) + "\nFINAL ERROR\n";
        String compact = DiagnosticLog.compactTrace(trace, 120, 32768);
        check(compact.startsWith("Honda Tuner legacy header\n") && compact.endsWith("FINAL ERROR\n") && compact.length() <= 32768,
                "Legacy trace header/tail/budget was lost");
    }
    private static String repeat(char value, int count) { char[] chars = new char[count]; java.util.Arrays.fill(chars, value); return new String(chars); }
}
