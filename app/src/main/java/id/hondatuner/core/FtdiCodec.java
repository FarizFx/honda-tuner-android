package id.hondatuner.core;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** FT232R single-channel divisor and USB packet format; no Android dependency. */
public final class FtdiCodec {
    private static final int[] FRACTIONS = {0, 3, 2, 4, 1, 5, 6, 7};
    private FtdiCodec() {}

    public static final class UartException extends ProtocolException {
        private static final long serialVersionUID = 1L;
        public final int lineStatus;
        private final RxData samples;
        private UartException(int lineStatus, RxData samples) {
            super("FTDI melaporkan kesalahan UART (LSR 0x" + Integer.toHexString(lineStatus) + "); lokasi sampel RX ditandai rusak.");
            this.lineStatus = lineStatus;
            this.samples = samples;
        }
        /** All UART bytes in this read, including clean packets and marked samples. */
        public RxData samples() { return samples; }
    }

    /** Immutable UART samples with the LSR of their enclosing USB packet. */
    public static final class RxData implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final byte[] data, status;
        private RxData(byte[] data, byte[] status) { this.data = data; this.status = status; }
        public int size() { return data.length; }
        public byte[] data() { return data.clone(); }
        public byte[] lineStatuses() { return status.clone(); }
        public RxData slice(int start, int end) {
            if (start < 0 || end < start || end > data.length) throw new IndexOutOfBoundsException("Invalid UART sample range");
            return new RxData(Arrays.copyOfRange(data, start, end), Arrays.copyOfRange(status, start, end));
        }
        /** Strict callers cannot accidentally consume samples from a bad UART packet. */
        public byte[] cleanBytes() throws UartException {
            for (byte flags : status) if ((flags & 0x1e) != 0) throw new UartException(flags & 255, this);
            return data();
        }
    }

    public static int baudDivisor(int baud) {
        if (baud < 183 || baud > 3000000) throw new IllegalArgumentException("Baud FT232R tidak didukung.");
        int eighths = (24000000 + baud / 2) / baud;
        int encoded = (eighths >> 3) | (FRACTIONS[eighths & 7] << 14);
        if (encoded == 1) return 0;
        if (encoded == 0x4001) return 1;
        return encoded;
    }

    public static byte[] stripStatus(byte[] buffer, int count, int packetSize) throws ProtocolException {
        return decodeStatus(buffer, count, packetSize).cleanBytes();
    }

    public static RxData decodeStatus(byte[] buffer, int count, int packetSize) throws ProtocolException {
        if (count < 0 || count > buffer.length || packetSize < 2) throw new IllegalArgumentException("Panjang paket USB tidak valid.");
        ByteArrayOutputStream data = new ByteArrayOutputStream(count);
        ByteArrayOutputStream status = new ByteArrayOutputStream(count);
        for (int offset = 0; offset < count; offset += packetSize) {
            int length = Math.min(packetSize, count - offset);
            if (length < 2) throw new ProtocolException("Status USB FTDI terpotong.");
            // A status-only packet carries no UART sample. A previous wake-up
            // BREAK indication in it must not reject a later, valid reply.
            data.write(buffer, offset + 2, length - 2);
            for (int index = 2; index < length; index++) status.write(buffer[offset + 1] & 255);
        }
        return new RxData(data.toByteArray(), status.toByteArray());
    }
}
