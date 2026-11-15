package id.hondatuner.android.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import id.hondatuner.core.ByteChannel;
import id.hondatuner.core.CancelSignal;
import id.hondatuner.core.DiagnosticLog;
import id.hondatuner.core.FtdiUart;
import id.hondatuner.core.ProtocolException;

/** Direct Android USB-host driver for the single-channel FT232R (0403:6001). */
public final class FtdiChannel implements ByteChannel {
    public static final int VENDOR_ID = 0x0403, PRODUCT_ID = 0x6001;
    private final UsbDeviceConnection connection;
    private final UsbInterface uart;
    private final DiagnosticLog log;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final FtdiUart driver;

    public static boolean matches(UsbDevice device) {
        return device.getVendorId() == VENDOR_ID && device.getProductId() == PRODUCT_ID;
    }
    public static FtdiChannel open(UsbManager manager, UsbDevice device, CancelSignal cancel) throws IOException {
        return open(manager, device, cancel, null);
    }
    public static FtdiChannel open(UsbManager manager, UsbDevice device, CancelSignal cancel, DiagnosticLog log) throws IOException {
        cancel.check();
        if (log != null) log.info("USB", "Buka VID=0403 PID=6001 device=" + device.getDeviceName() + ", permission=" + manager.hasPermission(device) + ", interfaces=" + device.getInterfaceCount());
        if (!matches(device) || !manager.hasPermission(device)) throw new ProtocolException("Izin USB FT232R belum diberikan.");
        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) throw new ProtocolException("USB tidak bisa dibuka. Lepas aplikasi lain yang memakai adaptor ini.");
        UsbInterface claimed = null;
        try {
            byte[] descriptors = connection.getRawDescriptors();
            if (log != null && descriptors != null) log.wire("USB", "DESCRIPTORS", descriptors, descriptors.length);
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
            if (log != null) log.info("USB", "UART claimed: interface=" + uart.getId() + ", IN=0x" + Integer.toHexString(input.getAddress()) + ", OUT=0x" + Integer.toHexString(output.getAddress()) + ", packet=" + input.getMaxPacketSize());
            cancel.check();
            return new FtdiChannel(connection, uart, input, output, log);
        } catch (IOException | RuntimeException error) {
            if (log != null && !(error instanceof InterruptedIOException) && !cancel.isCancelled()) log.error("USB OPEN", error);
            try { if (claimed != null) connection.releaseInterface(claimed); }
            catch (RuntimeException cleanup) { if (log != null) log.warn("USB CLOSE", cleanup.toString()); }
            try { connection.close(); }
            catch (RuntimeException cleanup) { if (log != null) log.warn("USB CLOSE", cleanup.toString()); }
            throw error;
        }
    }
    private FtdiChannel(UsbDeviceConnection connection, UsbInterface uart, UsbEndpoint input, UsbEndpoint output, DiagnosticLog log) {
        this.connection = connection; this.uart = uart;
        this.log = log;
        driver = new FtdiUart(new FtdiUart.UsbIo() {
            @Override public int controlOut(int request, int value, int index, int timeoutMillis) {
                return connection.controlTransfer(0x40, request, value, index, null, 0, timeoutMillis);
            }
            @Override public int controlIn(int request, int value, int index, byte[] buffer, int timeoutMillis) {
                return connection.controlTransfer(0xc0, request, value, index, buffer, buffer.length, timeoutMillis);
            }
            @Override public int write(byte[] data, int offset, int count, int timeoutMillis) {
                return connection.bulkTransfer(output, data, offset, count, timeoutMillis);
            }
            @Override public int read(byte[] buffer, int timeoutMillis) {
                return connection.bulkTransfer(input, buffer, buffer.length, timeoutMillis);
            }
            @Override public void close() { closeConnection(); }
        }, uart.getId() + 1, input.getMaxPacketSize(), log);
    }
    @Override public void initialize(CancelSignal cancel) throws IOException { driver.initialize(cancel); }
    @Override public void purge() throws IOException { driver.purge(); }
    @Override public void write(byte[] data, int timeoutMillis, CancelSignal cancel) throws IOException {
        driver.write(data, timeoutMillis, cancel);
    }
    @Override public byte[] read(int maximumBytes, int timeoutMillis) throws IOException {
        return driver.read(maximumBytes, timeoutMillis);
    }
    @Override public void close() { driver.close(); }
    private void closeConnection() {
        if (closed.compareAndSet(false, true)) {
            if (log != null) log.info("USB", "Menutup adaptor");
            try { connection.releaseInterface(uart); }
            catch (RuntimeException error) { if (log != null) log.warn("USB", "releaseInterface: " + error.getMessage()); }
            finally { try { connection.close(); } catch (RuntimeException error) { if (log != null) log.warn("USB", "close: " + error.getMessage()); } }
        }
    }
}
