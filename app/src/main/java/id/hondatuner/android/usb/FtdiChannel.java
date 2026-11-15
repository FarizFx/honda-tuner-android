package id.hondatuner.android.usb;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import com.hoho.android.usbserial.driver.FtdiSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import java.util.Arrays;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import id.hondatuner.core.ByteChannel;
import id.hondatuner.core.CancelSignal;
import id.hondatuner.core.DiagnosticLog;
import id.hondatuner.core.FtdiUart;
import id.hondatuner.core.ProtocolException;

/** usb-serial-for-android FTDI driver with bounded Honda wake/session timing. */
public final class FtdiChannel implements ByteChannel {
    public static final int VENDOR_ID = 0x0403, PRODUCT_ID = 0x6001;
    private final FtdiSerialDriver.FtdiSerialPort serial;
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
        FtdiSerialDriver.FtdiSerialPort opened = null;
        try {
            byte[] descriptors = connection.getRawDescriptors();
            if (log != null && descriptors != null) log.wire("USB", "DESCRIPTORS", descriptors, descriptors.length);
            if (descriptors == null || descriptors.length < 14 || ((descriptors[12] & 255) | ((descriptors[13] & 255) << 8)) != 0x0600) {
                throw new ProtocolException("Versi ini mendukung FT232R/FT232RL. Jenis FTDI pada adaptor ini berbeda.");
            }
            if (device.getInterfaceCount() != 1) throw new ProtocolException("Gunakan FT232R satu kanal untuk K-Line.");
            UsbInterface uart = device.getInterface(0);
            FtdiSerialDriver library = new FtdiSerialDriver(device);
            FtdiSerialDriver.FtdiSerialPort serial = library.new FtdiSerialPort(device, 0) {
                @Override protected int readFilter(byte[] buffer, int count) {
                    // Keep status headers for FtdiUart's packet codec and trace.
                    // This preserves UART-error checks during real ECU replies.
                    return count;
                }
            };
            serial.open(connection);
            opened = serial;
            UsbEndpoint input = serial.getReadEndpoint(), output = serial.getWriteEndpoint();
            if (input == null || output == null || input.getMaxPacketSize() <= 2) throw new ProtocolException("Endpoint FT232R tidak tersedia.");
            if (log != null) log.info("USB DRIVER", "usb-serial-for-android 3.12.0; FTDI UART; wake=BREAK 70/130 ms");
            if (log != null) log.info("USB", "UART claimed: interface=" + uart.getId() + ", IN=0x" + Integer.toHexString(input.getAddress()) + ", OUT=0x" + Integer.toHexString(output.getAddress()) + ", packet=" + input.getMaxPacketSize());
            cancel.check();
            return new FtdiChannel(connection, serial, input, log);
        } catch (IOException | RuntimeException error) {
            if (log != null && !(error instanceof InterruptedIOException) && !cancel.isCancelled()) log.error("USB OPEN", error);
            try { if (opened != null) opened.close(); else connection.close(); }
            catch (IOException | RuntimeException cleanup) { if (log != null) log.warn("USB CLOSE", cleanup.toString()); }
            throw error;
        }
    }
    private FtdiChannel(UsbDeviceConnection connection, FtdiSerialDriver.FtdiSerialPort serial, UsbEndpoint input, DiagnosticLog log) {
        this.serial = serial;
        this.log = log;
        driver = new FtdiUart(new FtdiUart.UsbIo() {
            @Override public int controlOut(int request, int value, int index, int timeoutMillis) {
                return connection.controlTransfer(0x40, request, value, index, null, 0, timeoutMillis);
            }
            @Override public int controlIn(int request, int value, int index, byte[] buffer, int timeoutMillis) {
                return connection.controlTransfer(0xc0, request, value, index, buffer, buffer.length, timeoutMillis);
            }
            @Override public void configureUart(int baudRate) throws IOException {
                serial.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                serial.setFlowControl(UsbSerialPort.FlowControl.NONE);
                if (log != null) log.debug("USB DRIVER", "FTDI setParameters(" + baudRate + ", 8N1), flow=NONE");
            }
            @Override public void setBreak(boolean enabled) throws IOException {
                serial.setBreak(enabled);
                if (log != null) log.debug("USB DRIVER", "FTDI setBreak(" + enabled + ")");
            }
            @Override public int write(byte[] data, int offset, int count, int timeoutMillis) throws IOException {
                serial.write(offset == 0 && count == data.length ? data : Arrays.copyOfRange(data, offset, offset + count), timeoutMillis);
                return count;
            }
            @Override public int read(byte[] buffer, int timeoutMillis) throws IOException {
                return serial.read(buffer, timeoutMillis);
            }
            @Override public void close() { closeConnection(); }
        }, serial.getPortNumber() + 1, input.getMaxPacketSize(), log);
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
            try { serial.close(); }
            catch (IOException | RuntimeException error) { if (log != null) log.warn("USB", "close: " + error.getMessage()); }
        }
    }
}
