package id.hondatuner.android.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import id.hondatuner.core.ByteChannel;
import id.hondatuner.core.CancelSignal;
import id.hondatuner.core.FtdiCodec;
import id.hondatuner.core.ProtocolException;

/** Direct Android USB-host driver for the single-channel FT232R (0403:6001). */
public final class FtdiChannel implements ByteChannel {
    public static final int VENDOR_ID = 0x0403, PRODUCT_ID = 0x6001;
    private final UsbDeviceConnection connection;
    private final UsbInterface uart;
    private final UsbEndpoint input, output;
    private final AtomicBoolean closed = new AtomicBoolean();
    private byte[] pending = new byte[0];

    public static boolean matches(UsbDevice device) {
        return device.getVendorId() == VENDOR_ID && device.getProductId() == PRODUCT_ID;
    }
    public static FtdiChannel open(UsbManager manager, UsbDevice device, CancelSignal cancel) throws IOException {
        cancel.check();
        if (!matches(device) || !manager.hasPermission(device)) throw new ProtocolException("Izin USB FT232R belum diberikan.");
        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) throw new ProtocolException("USB tidak bisa dibuka. Lepas aplikasi lain yang memakai adaptor ini.");
        UsbInterface claimed = null;
        try {
            byte[] descriptors = connection.getRawDescriptors();
            if (descriptors == null || descriptors.length < 14 || ((descriptors[12] & 255) | ((descriptors[13] & 255) << 8)) != 0x0600) {
                throw new ProtocolException("Versi ini mendukung FT232R/FT232RL. Jenis FTDI pada adaptor ini berbeda.");
            }
            if (device.getInterfaceCount() != 1) throw new ProtocolException("Gunakan FT232R satu kanal untuk K-Line.");
            UsbInterface uart = device.getInterface(0);
            UsbEndpoint input = null, output = null;
            for (int i = 0; i < uart.getEndpointCount(); i++) {
                UsbEndpoint endpoint = uart.getEndpoint(i);
                if (endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) input = endpoint;
                else output = endpoint;
            }
            if (input == null || output == null || input.getMaxPacketSize() <= 2) throw new ProtocolException("Endpoint FT232R tidak tersedia.");
            if (!connection.claimInterface(uart, true)) throw new ProtocolException("Adaptor USB sedang dipakai aplikasi lain.");
            claimed = uart;
            cancel.check();
            return new FtdiChannel(connection, uart, input, output);
        } catch (IOException | RuntimeException error) {
            try { if (claimed != null) connection.releaseInterface(claimed); }
            finally { connection.close(); }
            throw error;
        }
    }
    private FtdiChannel(UsbDeviceConnection connection, UsbInterface uart, UsbEndpoint input, UsbEndpoint output) {
        this.connection = connection; this.uart = uart; this.input = input; this.output = output;
    }
    private void requireOpen() throws ProtocolException {
        if (closed.get()) throw new ProtocolException("Adaptor USB sudah ditutup atau dilepas.");
    }
    private void control(int request, int value, int index) throws IOException {
        requireOpen();
        int result = connection.controlTransfer(0x40, request, value, index, null, 0, 500);
        requireOpen();
        if (result != 0) throw new ProtocolException("Pengaturan FT232R gagal (USB request " + request + ").");
    }
    private void control(int request, int value) throws IOException { control(request, value, uart.getId() + 1); }
    private void baud10400() throws IOException {
        int divisor = FtdiCodec.baudDivisor(10400);
        // FT232R baud uses the divisor high bit in wIndex, unlike port controls.
        control(3, divisor & 0xffff, (divisor >> 16) & 1);
    }
    @Override public void initialize(CancelSignal cancel) throws IOException {
        cancel.check();
        control(0, 0); // reset UART
        purge();
        control(11, 0); // UART mode
        control(4, 8); // 8 data bits, no parity, one stop bit
        baud10400();
        control(9, 12); // latency timer
        control(11, 0x0101); // asynchronous bit-bang, TXD output
        write(new byte[]{0}, 500, cancel);
        pause(70, cancel);
        write(new byte[]{1}, 500, cancel);
        control(11, 0);
        baud10400();
        pause(130, cancel);
        purge();
        control(9, 8);
    }
    @Override public void purge() throws IOException {
        pending = new byte[0];
        control(0, 1); // RX purge
        control(0, 2); // TX purge
    }
    private static void pause(long millis, CancelSignal cancel) throws InterruptedIOException {
        long deadline = System.nanoTime() + millis * 1000000L;
        while (System.nanoTime() < deadline) {
            cancel.check();
            try { Thread.sleep(Math.min(10, Math.max(1, (deadline - System.nanoTime()) / 1000000L))); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new InterruptedIOException("Inisialisasi USB dibatalkan."); }
        }
        cancel.check();
    }
    @Override public void write(byte[] data, int timeoutMillis, CancelSignal cancel) throws IOException {
        requireOpen();
        long deadline = System.nanoTime() + timeoutMillis * 1000000L;
        int sent = 0;
        while (sent < data.length) {
            cancel.check();
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new ProtocolException("Batas waktu kirim USB habis.");
            int timeout = (int) Math.min(50, Math.max(1, (remaining + 999999) / 1000000));
            int count = connection.bulkTransfer(output, data, sent, data.length - sent, timeout);
            requireOpen();
            if (count <= 0 || count > data.length - sent) throw new ProtocolException("USB tidak menyelesaikan pengiriman.");
            sent += count;
        }
        cancel.check();
    }
    @Override public byte[] read(int maximumBytes, int timeoutMillis) throws IOException {
        requireOpen();
        if (maximumBytes <= 0) return new byte[0];
        if (pending.length != 0) return takePending(maximumBytes);
        int packetSize = input.getMaxPacketSize();
        byte[] raw = new byte[packetSize * Math.max(1, Math.min(4, maximumBytes / (packetSize - 2)))];
        int count = connection.bulkTransfer(input, raw, raw.length, Math.max(1, timeoutMillis));
        requireOpen();
        if (count <= 0) return new byte[0];
        pending = FtdiCodec.stripStatus(raw, count, packetSize);
        return takePending(maximumBytes);
    }
    private byte[] takePending(int maximumBytes) {
        int count = Math.min(maximumBytes, pending.length);
        byte[] result = Arrays.copyOfRange(pending, 0, count);
        pending = Arrays.copyOfRange(pending, count, pending.length);
        return result;
    }
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            try { connection.releaseInterface(uart); }
            catch (RuntimeException ignored) { /* device detach may already release the interface */ }
            finally { connection.close(); }
        }
    }
}
