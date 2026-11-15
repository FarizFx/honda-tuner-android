package id.hondatuner.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Normal Honda diagnostic session. This client exposes no ECU write command. */
public final class HondaClient implements LiveProfile.Reader, AutoCloseable {
    public interface Progress { void onStage(String message); }
    private final KLineTransport transport;
    private final DiagnosticLog log;
    private volatile boolean connected;
    private String identifier = "";
    public HondaClient(KLineTransport transport) { this(transport, null); }
    public HondaClient(KLineTransport transport, DiagnosticLog log) { this.transport = Objects.requireNonNull(transport); this.log = log; }

    public String connect(CancelSignal cancel) throws IOException {
        return connect(cancel, message -> {}, 1);
    }
    public String connect(CancelSignal cancel, Progress progress, int attempts) throws IOException {
        if (attempts < 1 || attempts > 3) throw new IllegalArgumentException("Scan attempts must be 1..3.");
        connected = false;
        identifier = "";
        String stage = "inisialisasi K-Line";
        for (int attempt = 1; attempt <= attempts; attempt++) {
            cancel.check();
            try {
                stage = "inisialisasi K-Line";
                progress.onStage("Menyiapkan K-Line · percobaan " + attempt + "/" + attempts);
                if (log != null) log.info("SCAN", "Percobaan " + attempt + "/" + attempts + ": " + stage);
                transport.initialize(cancel);
                stage = "wake-up ECU";
                progress.onStage("Membangunkan ECU · percobaan " + attempt + "/" + attempts);
                transport.exchange(0xfe, new byte[]{(byte) 0xff}, true, cancel);
                stage = "diagnostic entry";
                progress.onStage("Menunggu balasan ECU · percobaan " + attempt + "/" + attempts);
                transport.exchange(0x72, new byte[]{0, (byte) 0xf0}, false, cancel);
                if (log != null) log.info("SCAN", "Diagnostic entry mendapat balasan valid dari ECU.");
                break;
            } catch (InterruptedIOException error) { throw error; }
            catch (TransportException error) { throw error; }
            catch (IOException error) {
                requireHealthy();
                if (log != null) log.warn("SCAN", "Gagal pada " + stage + ": " + error.getMessage());
                if (attempt == attempts) {
                    ProtocolException failed = new ProtocolException("Gagal pada " + stage + " setelah " + attempts + " percobaan. " + error.getMessage());
                    failed.initCause(error); throw failed;
                }
                long until = System.nanoTime() + 150000000L;
                while (System.nanoTime() < until) {
                    cancel.check();
                    try { Thread.sleep(10); }
                    catch (InterruptedException stopped) { Thread.currentThread().interrupt(); throw new InterruptedIOException("Scan ECU dibatalkan."); }
                }
            }
        }
        progress.onStage("ECU merespons. Membaca identitas ECU...");
        try {
            KLineTransport.Packet reply = transport.exchange(0x72, new byte[]{0x72, 0, 0, 5}, false, cancel);
            byte[] data = reply.payload();
            if (reply.totalLength == 11 && data.length == 8) {
                StringBuilder value = new StringBuilder();
                for (int i = 3; i < 8; i++) value.append(String.format(Locale.ROOT, "%02X", data[i] & 255));
                identifier = value.toString();
                if (log != null) log.info("ECU ID", identifier);
            } else {
                if (log != null) log.warn("ECU ID", "Balasan ID memiliki layout berbeda: total=" + reply.totalLength + ", payload=" + data.length);
            }
        } catch (InterruptedIOException error) { throw error; }
        catch (TransportException error) { throw error; }
        catch (IOException error) { if (log != null) log.warn("ECU ID", "ID opsional belum terbaca: " + error.getMessage()); }
        cancel.check();
        requireHealthy();
        connected = true;
        return identifier;
    }

    /** A volatile health snapshot; callers must not wait for an in-flight exchange. */
    @Override public boolean isHealthy() { return transport.isHealthy(); }

    private void requireHealthy() throws TransportException {
        if (!isHealthy()) throw new TransportException("Kanal USB tidak dapat digunakan. Hubungkan ulang adaptor.");
    }

    private void requireConnection() throws ProtocolException {
        requireHealthy();
        if (!connected) throw new ProtocolException("ECU belum terhubung.");
    }

    @Override public SensorDecoder.Result readTable(int table, CancelSignal cancel) throws IOException {
        requireConnection();
        byte[] payload = transport.exchange(0x72, new byte[]{0x71, (byte) table}, false, cancel).payload();
        if (payload.length < 2 || payload[0] != 0x71) throw new ProtocolException("Balasan live data tidak cocok.");
        SensorDecoder.Result decoded = SensorDecoder.decode(payload[1] & 255, Arrays.copyOfRange(payload, 2, payload.length));
        if (log != null) log.debug("SENSOR", "Request 0x" + Integer.toHexString(table) + " -> tabel 0x" + Integer.toHexString(decoded.tableId) + ", body=" + decoded.bodyLength + ", fields=" + decoded.named().size());
        return decoded;
    }

    public static final class DtcCode {
        public final int major, minor;
        public DtcCode(int major, int minor) { this.major = major; this.minor = minor; }
        public String code() { return major + "-" + minor; }
        public String description() { return DtcDescriptions.describe(code()); }
    }
    public static final class DtcPage {
        public final List<DtcCode> codes;
        public final boolean more;
        private DtcPage(List<DtcCode> codes, boolean more) { this.codes = Collections.unmodifiableList(codes); this.more = more; }
        public static DtcPage parse(byte[] payload) throws ProtocolException {
            if (payload.length < 9) throw new ProtocolException("Halaman DTC terpotong.");
            List<DtcCode> codes = new ArrayList<>();
            for (int offset = 3; offset <= 7; offset += 2) {
                int major = payload[offset] & 255, minor = payload[offset + 1] & 255;
                if (major != 0 || minor != 0) codes.add(new DtcCode(major, minor));
            }
            return new DtcPage(codes, payload[2] != 0);
        }
    }
    public static final class DtcReport {
        public final List<DtcCode> current, stored;
        public final long completedMillis;
        DtcReport(List<DtcCode> current, List<DtcCode> stored) {
            this.current = Collections.unmodifiableList(current); this.stored = Collections.unmodifiableList(stored);
            completedMillis = System.currentTimeMillis();
        }
    }

    public DtcReport readDtcs(CancelSignal cancel) throws IOException {
        requireConnection();
        DtcReport result = new DtcReport(readDtcGroup(0x74, cancel), readDtcGroup(0x73, cancel));
        if (log != null) log.info("DTC", "current=" + result.current.size() + ", stored=" + result.stored.size());
        return result;
    }

    private List<DtcCode> readDtcGroup(int command, CancelSignal cancel) throws IOException {
        List<DtcCode> codes = new ArrayList<>();
        for (int page = 1; page < 256; page++) {
            cancel.check();
            byte[] payload = transport.exchange(0x72, new byte[]{(byte) command, (byte) page}, false, cancel).payload();
            if (payload.length < 2 || (payload[0] & 255) != command || (payload[1] & 255) != page) {
                throw new ProtocolException("Halaman DTC bukan balasan perintah saat ini.");
            }
            DtcPage result = DtcPage.parse(payload);
            if (log != null) log.debug("DTC", "command=0x" + Integer.toHexString(command) + ", page=" + page + ", codes=" + result.codes.size() + ", more=" + result.more);
            codes.addAll(result.codes);
            if (!result.more) return codes;
        }
        throw new ProtocolException("ECU mengirim DTC melampaui 255 halaman.");
    }

    @Override public void close() { connected = false; transport.close(); }
}
