package id.hondatuner.core;

import java.io.ByteArrayOutputStream;

/** FT232R single-channel divisor and USB packet format; no Android dependency. */
public final class FtdiCodec {
    private static final int[] FRACTIONS = {0, 3, 2, 4, 1, 5, 6, 7};
    private FtdiCodec() {}

    public static final class UartException extends ProtocolException {
        private static final long serialVersionUID = 1L;
        public final int lineStatus;
        private UartException(int lineStatus) {
            super("FTDI melaporkan kesalahan UART (LSR 0x" + Integer.toHexString(lineStatus) + "); sampel tidak digunakan.");
            this.lineStatus = lineStatus;
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
        if (count < 0 || count > buffer.length || packetSize < 2) throw new IllegalArgumentException("Panjang paket USB tidak valid.");
        ByteArrayOutputStream data = new ByteArrayOutputStream(count);
        for (int offset = 0; offset < count; offset += packetSize) {
            int length = Math.min(packetSize, count - offset);
            if (length < 2) throw new ProtocolException("Status USB FTDI terpotong.");
            // A status-only packet carries no UART sample. A previous wake-up
            // BREAK indication in it must not reject a later, valid reply.
            if (length > 2 && (buffer[offset + 1] & 0x1e) != 0) throw new UartException(buffer[offset + 1] & 255);
            data.write(buffer, offset + 2, length - 2);
        }
        return data.toByteArray();
    }
}
