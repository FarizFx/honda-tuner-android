package id.hondatuner.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Normal Honda diagnostic session. This client exposes no ECU write command. */
public final class HondaClient implements LiveProfile.Reader, AutoCloseable {
    private final KLineTransport transport;
    private boolean connected;
    private String identifier = "";
    public HondaClient(KLineTransport transport) { this.transport = transport; }

    public String connect(CancelSignal cancel) throws IOException {
        connected = false;
        identifier = "";
        transport.initialize(cancel);
        transport.exchange(0xfe, new byte[]{(byte) 0xff}, true, cancel);
        transport.exchange(0x72, new byte[]{0, (byte) 0xf0}, false, cancel);
        try {
            KLineTransport.Packet reply = transport.exchange(0x72, new byte[]{0x72, 0, 0, 5}, false, cancel);
            byte[] data = reply.payload();
            if (reply.totalLength == 11 && data.length == 8) {
                StringBuilder value = new StringBuilder();
                for (int i = 3; i < 8; i++) value.append(String.format(Locale.ROOT, "%02X", data[i] & 255));
                identifier = value.toString();
            }
        } catch (InterruptedIOException error) { throw error; }
        catch (IOException ignored) { /* normal diagnostics can work without identity */ }
        cancel.check();
        connected = true;
        return identifier;
    }

    private void requireConnection() throws ProtocolException {
        if (!connected) throw new ProtocolException("ECU belum terhubung.");
    }

    @Override public SensorDecoder.Result readTable(int table, CancelSignal cancel) throws IOException {
        requireConnection();
        byte[] payload = transport.exchange(0x72, new byte[]{0x71, (byte) table}, false, cancel).payload();
        if (payload.length < 2 || payload[0] != 0x71) throw new ProtocolException("Balasan live data tidak cocok.");
        return SensorDecoder.decode(payload[1] & 255, Arrays.copyOfRange(payload, 2, payload.length));
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
        return new DtcReport(readDtcGroup(0x74, cancel), readDtcGroup(0x73, cancel));
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
            codes.addAll(result.codes);
            if (!result.more) return codes;
        }
        throw new ProtocolException("ECU mengirim DTC melampaui 255 halaman.");
    }

    @Override public void close() { connected = false; transport.close(); }
}
