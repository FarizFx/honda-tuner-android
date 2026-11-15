package id.hondatuner.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** USB fixtures model an asynchronous FIFO and pin transitions, never a vehicle. */
final class FtdiUartTest {
    private interface Checked { void run() throws Exception; }
    private static int passed;
    static int runAll() throws Exception {
        run("FTDI wake holds confirmed LOW and waits for delayed HIGH", FtdiUartTest::wakePins);
        run("FT232R USB controls configure 10400/8N1 on the correct indices", FtdiUartTest::configuration);
        run("initially stale TX-empty status cannot truncate a queued frame", FtdiUartTest::txDrain);
        run("partial USB writes preserve every command byte until UART completion", FtdiUartTest::partialWrites);
        run("TX timeout prevents appending a retry until full reinitialization", FtdiUartTest::txTimeout);
        run("short modem status cannot establish UART completion", FtdiUartTest::shortStatus);
        run("failed and invalid USB write counts prevent subsequent commands", FtdiUartTest::invalidWrites);
        run("cancellation during TX guard stops without another frame", FtdiUartTest::cancelTx);
        run("cancellation during LOW restores UART idle without another byte", FtdiUartTest::cancelLow);
        run("missing and stuck FTDI pins fail within the wake deadline", FtdiUartTest::badPins);
        run("FTDI receive keeps pending UART bytes across small reads", FtdiUartTest::pendingRx);
        run("status-only BREAK is discarded but UART framing errors stay strict", FtdiUartTest::statusRx);
        run("RX purge preserves the UART transmit queue", FtdiUartTest::rxOnly);
        run("reported FC/framing wake reaches entry only after complete TX", FtdiUartTest::traceHandshake);
        run("UART framing error on diagnostic entry cannot establish connection", FtdiUartTest::strictEntry);
        run("required wake echo still rejects the reported framing error", FtdiUartTest::strictWake);
        run("truncated USB status on wake cannot be ignored as optional UART RX", FtdiUartTest::brokenUsbWake);
        return passed;
    }
    private static void run(String name, Checked test) throws Exception {
        test.run(); passed++; System.out.println("OK " + name);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void bytes(byte[] expected, byte[] actual) { check(Arrays.equals(expected, actual), "UART bytes differ"); }
    private static void failure(Checked test) throws Exception {
        try { test.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected bounded I/O failure");
    }
    private static byte[] frame() { return KLineTransport.command(0xfe, (byte) 0xff); }

    private static final class UsbFixture implements FtdiUart.UsbIo, FtdiUart.Timing {
        final List<int[]> controls = new ArrayList<>();
        final List<byte[]> uartFrames = new ArrayList<>();
        final ByteArrayOutputStream uartBytes = new ByteArrayOutputStream();
        final Deque<byte[]> rx = new ArrayDeque<>();
        long time, pinReadyAt, lowAt = -1, highAt = -1, txUntil, staleUntil;
        int pin = 1, pendingPin = 1, pinDelay = 7, txStartDelay = 6, chunk = 512;
        int closeCalls, modemPolls, readCalls, writeCalls;
        boolean bitBang, highPending, prematureSwitch, txStuck, shortModem, shortPins, highStuck;
        Integer forcedWriteResult;
        Runnable onSleep;
        Consumer<byte[]> onFrame = data -> {};
        @Override public long now() { return time; }
        @Override public void sleep(long millis) {
            time += millis * 1000000L;
            if (onSleep != null) onSleep.run();
        }
        @Override public int controlOut(int request, int value, int index, int timeout) {
            check(timeout > 0 && timeout <= 500, "Unbounded control timeout");
            controls.add(new int[]{request, value, index});
            if (request == 0 && value == 0) { rx.clear(); txUntil = time; }
            if (request == 0 && value == 1) rx.clear();
            if (request == 0 && value == 2) { txUntil = time; throw new AssertionError("RX discard sent a TX purge"); }
            if (request == 11) {
                if (value == 0x0101) bitBang = true;
                else if (value == 0) {
                    if (bitBang && highPending && pin != 1) prematureSwitch = true;
                    bitBang = false; pin = 1;
                }
            }
            return 0;
        }
        @Override public int controlIn(int request, int value, int index, byte[] buffer, int timeout) {
            check(value == 0 && index == 1 && timeout > 0 && timeout <= 50, "Wrong FT232R input request");
            if (request == 12) {
                if (shortPins) return 0;
                if (time >= pinReadyAt && !(highStuck && pendingPin == 1)) {
                    if (pin != pendingPin) {
                        pin = pendingPin;
                        if (pin == 0) lowAt = time;
                        else { highAt = time; highPending = false; }
                    }
                }
                buffer[0] = (byte) (0xfe | pin); return 1;
            }
            check(request == 5 && buffer.length == 2, "Unexpected status poll");
            modemPolls++;
            if (shortModem) return 1;
            buffer[0] = 1;
            buffer[1] = (byte) (time < staleUntil ? 0x60 : txStuck || time < txUntil ? 0 : 0x60);
            return 2;
        }
        @Override public int write(byte[] data, int offset, int count, int timeout) {
            check(timeout > 0 && timeout <= 50, "Unbounded bulk timeout"); writeCalls++;
            if (forcedWriteResult != null) return forcedWriteResult;
            int accepted = Math.min(chunk, count);
            if (bitBang) {
                check(count == 1, "Unexpected bit-bang stream");
                pendingPin = data[offset] & 1; pinReadyAt = time + pinDelay * 1000000L;
                highPending = pendingPin == 1;
            } else {
                uartBytes.write(data, offset, accepted);
                if (offset == 0) { txUntil = Math.max(txUntil, time) + txStartDelay * 1000000L; staleUntil = time + 2000000L; }
                txUntil += (accepted * 10000000000L + 10399) / 10400;
                if (offset + accepted == data.length) { uartFrames.add(data.clone()); onFrame.accept(data.clone()); }
            }
            return accepted;
        }
        void queue(byte[] uart, int flags) {
            for (int offset = 0; offset < uart.length; offset += 62) {
                int count = Math.min(62, uart.length - offset);
                byte[] packet = new byte[count + 2]; packet[0] = 1; packet[1] = (byte) flags;
                System.arraycopy(uart, offset, packet, 2, count); rx.add(packet);
            }
        }
        @Override public int read(byte[] buffer, int timeout) {
            check(timeout > 0, "Unbounded receive timeout"); readCalls++;
            time += (rx.isEmpty() ? timeout : 1) * 1000000L;
            if (rx.isEmpty()) return -1;
            byte[] packet = rx.remove(); check(packet.length <= buffer.length, "Fixture packet exceeds USB buffer");
            System.arraycopy(packet, 0, buffer, 0, packet.length); return packet.length;
        }
        @Override public void close() { closeCalls++; }
        FtdiUart driver(DiagnosticLog log) { return new FtdiUart(this, 1, 64, log, this); }
        boolean hasControl(int request, int value, int index) {
            return controls.stream().anyMatch(item -> item[0] == request && item[1] == value && item[2] == index);
        }
    }
    private static FtdiUart initialized(UsbFixture usb) throws IOException {
        FtdiUart driver = usb.driver(null); driver.initialize(CancelSignal.NEVER); return driver;
    }
    private static void wakePins() throws Exception {
        UsbFixture usb = new UsbFixture(); usb.pinDelay = 9; FtdiUart driver = initialized(usb);
        check(usb.lowAt >= 0 && usb.highAt - usb.lowAt >= 70000000L, "LOW duration started before the pin changed");
        check(usb.time - usb.highAt >= 130000000L, "HIGH did not settle");
        check(!usb.prematureSwitch && !usb.bitBang, "UART mode cancelled queued HIGH"); driver.close();
    }
    private static void configuration() throws Exception {
        UsbFixture usb = new UsbFixture(); FtdiUart driver = initialized(usb);
        check(usb.hasControl(0, 0, 1) && usb.hasControl(11, 0x0101, 1), "Reset/wake controls missing");
        check(usb.hasControl(2, 0, 1) && usb.hasControl(4, 8, 1) && usb.hasControl(3, 0x4120, 0), "Wrong flow/8N1/divisor");
        check(usb.hasControl(9, 12, 1) && usb.hasControl(9, 8, 1), "Latency setup missing"); driver.close();
    }
    private static void txDrain() throws Exception {
        UsbFixture usb = new UsbFixture(); FtdiUart driver = initialized(usb); long began = usb.time;
        driver.write(frame(), 100, CancelSignal.NEVER);
        check(usb.time >= usb.txUntil && usb.time > began + 4000000L, "USB acceptance or stale TEMT became TX completion");
        check(usb.modemPolls > 1, "Busy UART was not polled"); bytes(frame(), usb.uartBytes.toByteArray()); driver.close();
    }
    private static void partialWrites() throws Exception {
        UsbFixture usb = new UsbFixture(); usb.chunk = 2; FtdiUart driver = initialized(usb);
        byte[] request = KLineTransport.command(0x72, (byte) 0, (byte) 0xf0);
        driver.write(request, 100, CancelSignal.NEVER); bytes(request, usb.uartBytes.toByteArray());
        check(usb.uartFrames.size() == 1 && usb.time >= usb.txUntil, "Partial frame was retried/truncated"); driver.close();
    }
    private static void txTimeout() throws Exception {
        UsbFixture usb = new UsbFixture(); FtdiUart driver = initialized(usb); usb.txStuck = true; long began = usb.time;
        failure(() -> driver.write(frame(), 25, CancelSignal.NEVER));
        check(usb.time - began <= 25000000L, "TX exceeded its deadline"); int writes = usb.writeCalls;
        failure(() -> driver.write(frame(), 25, CancelSignal.NEVER)); check(usb.writeCalls == writes, "Retry appended to incomplete TX");
        usb.txStuck = false; driver.initialize(CancelSignal.NEVER); driver.write(frame(), 100, CancelSignal.NEVER); driver.close();
    }
    private static void shortStatus() throws Exception {
        UsbFixture usb = new UsbFixture(); FtdiUart driver = initialized(usb); usb.shortModem = true;
        failure(() -> driver.write(frame(), 100, CancelSignal.NEVER)); int writes = usb.writeCalls;
        failure(() -> driver.write(frame(), 100, CancelSignal.NEVER)); check(writes == usb.writeCalls, "Short status allowed another TX"); driver.close();
    }
    private static void invalidWrites() throws Exception {
        for (int result : new int[]{-1, 0, 5}) {
            UsbFixture usb = new UsbFixture(); FtdiUart driver = initialized(usb); usb.forcedWriteResult = result;
            failure(() -> driver.write(frame(), 100, CancelSignal.NEVER)); int writes = usb.writeCalls;
            failure(() -> driver.write(frame(), 100, CancelSignal.NEVER)); check(writes == usb.writeCalls, "Invalid bulk count allowed a retry"); driver.close();
        }
    }
    private static void cancelTx() throws Exception {
        UsbFixture usb = new UsbFixture(); FtdiUart driver = initialized(usb); AtomicBoolean stop = new AtomicBoolean();
        usb.onSleep = () -> stop.set(true);
        try { driver.write(frame(), 100, stop::get); throw new AssertionError("TX cancellation ignored"); }
        catch (InterruptedIOException expected) { check(usb.uartFrames.size() == 1, "Cancellation sent another frame"); }
        usb.onSleep = null; int writes = usb.writeCalls;
        failure(() -> driver.write(frame(), 100, CancelSignal.NEVER)); check(writes == usb.writeCalls, "Cancelled TX was reused"); driver.close();
    }
    private static void cancelLow() throws Exception {
        UsbFixture usb = new UsbFixture(); FtdiUart driver = usb.driver(null); AtomicBoolean stop = new AtomicBoolean();
        usb.onSleep = () -> { if (usb.pin == 0) stop.set(true); };
        try { driver.initialize(stop::get); throw new AssertionError("LOW cancellation ignored"); }
        catch (InterruptedIOException expected) { check(!usb.bitBang && usb.uartFrames.isEmpty(), "Cancelled LOW left bit-bang active/sent a command"); }
        driver.close(); driver.close(); check(usb.closeCalls == 1, "USB close repeated");
        int controls = usb.controls.size(); failure(() -> driver.initialize(CancelSignal.NEVER));
        check(controls == usb.controls.size(), "Closed USB was configured again");
    }
    private static void badPins() throws Exception {
        for (boolean shortRead : new boolean[]{true, false}) {
            UsbFixture usb = new UsbFixture(); usb.shortPins = shortRead; usb.highStuck = !shortRead;
            FtdiUart driver = usb.driver(null); failure(() -> driver.initialize(CancelSignal.NEVER));
            check(usb.time <= 190000000L && !usb.bitBang && usb.uartFrames.isEmpty(), "Pin fault hung or sent a UART command"); driver.close();
        }
    }
    private static void pendingRx() throws Exception {
        UsbFixture usb = new UsbFixture(); FtdiUart driver = initialized(usb);
        usb.queue(new byte[]{1, 2, 3, 4, 5}, 0x60);
        bytes(new byte[]{1, 2}, driver.read(2, 50)); bytes(new byte[]{3, 4}, driver.read(2, 50)); bytes(new byte[]{5}, driver.read(2, 50));
        check(usb.readCalls == 1, "Pending bytes required another USB read");
        usb.queue(new byte[]{6, 7, 8}, 0x60); driver.read(1, 50); driver.purge(); bytes(new byte[0], driver.read(10, 1)); driver.close();
    }
    private static void statusRx() throws Exception {
        UsbFixture usb = new UsbFixture(); FtdiUart driver = initialized(usb);
        usb.rx.add(new byte[]{1, 0x70}); bytes(new byte[0], driver.read(256, 50));
        usb.queue(new byte[]{2, 3, (byte) 0xfb}, 0x60); bytes(new byte[]{2, 3, (byte) 0xfb}, driver.read(256, 50));
        usb.rx.add(new byte[]{1, 0x68, (byte) 0xfc, 0}); failure(() -> driver.read(256, 50)); driver.close();
    }
    private static void rxOnly() throws Exception {
        UsbFixture usb = new UsbFixture(); FtdiUart driver = initialized(usb); usb.txUntil = usb.time + 100000000L;
        usb.queue(new byte[]{1, 2}, 0x60); long txUntil = usb.txUntil; driver.purge();
        check(usb.rx.isEmpty() && usb.txUntil == txUntil, "RX purge changed the transmit queue"); driver.close();
    }
    private static void diagnosticResponder(UsbFixture usb, boolean corruptEntry) {
        usb.onFrame = request -> {
            if ((request[0] & 255) == 0xfe) usb.rx.add(new byte[]{1, 0x68, (byte) 0xfc, 0});
            else if (corruptEntry && request[2] == 0) usb.rx.add(new byte[]{1, 0x68, 0x60, 0});
            else if (request[2] == 0x72) usb.queue(KLineTransport.command(2, (byte) 0x72, (byte) 0, (byte) 5, (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5), 0x60);
            else usb.queue(KLineTransport.command(2, (byte) 0, (byte) 0xf0), 0x60);
        };
    }
    private static void traceHandshake() throws Exception {
        UsbFixture usb = new UsbFixture(); diagnosticResponder(usb, false); DiagnosticLog log = new DiagnosticLog();
        FtdiUart driver = usb.driver(log);
        HondaClient client = new HondaClient(new KLineTransport(driver, usb, 1000, KLineTransport.EchoMode.AUTO, log), log);
        check(client.connect(CancelSignal.NEVER).equals("0102030405"), "Valid diagnostic reply was lost after wake fault");
        check(usb.uartFrames.size() == 3 && !usb.prematureSwitch, "Wake repeated or HIGH was cut short");
        check(log.text(0).contains("0x68") && log.text(0).contains("byte drained"), "TX/RX evidence missing"); client.close();
    }
    private static void strictEntry() throws Exception {
        UsbFixture usb = new UsbFixture(); diagnosticResponder(usb, true); FtdiUart driver = usb.driver(null);
        HondaClient client = new HondaClient(new KLineTransport(driver, usb, 1000, KLineTransport.EchoMode.AUTO, null));
        failure(() -> client.connect(CancelSignal.NEVER)); failure(() -> client.readTable(0x10, CancelSignal.NEVER));
        check(usb.uartFrames.size() == 3, "Corrupt entry allowed ID or sensors"); client.close();
    }
    private static void strictWake() throws Exception {
        UsbFixture usb = new UsbFixture(); diagnosticResponder(usb, false); FtdiUart driver = usb.driver(null);
        HondaClient client = new HondaClient(new KLineTransport(driver, usb, 1000, KLineTransport.EchoMode.REQUIRED, null));
        failure(() -> client.connect(CancelSignal.NEVER)); check(usb.uartFrames.size() == 1, "Required echo fault allowed entry"); client.close();
    }
    private static void brokenUsbWake() throws Exception {
        UsbFixture usb = new UsbFixture(); usb.onFrame = request -> usb.rx.add(new byte[]{1});
        FtdiUart driver = usb.driver(null);
        HondaClient client = new HondaClient(new KLineTransport(driver, usb, 1000, KLineTransport.EchoMode.AUTO, null));
        failure(() -> client.connect(CancelSignal.NEVER)); check(usb.uartFrames.size() == 1, "Broken USB transport allowed entry"); client.close();
    }
}
