package id.hondatuner.android;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import id.hondatuner.android.usb.FtdiChannel;
import id.hondatuner.core.DiagnosticController;
import id.hondatuner.core.HondaClient;
import id.hondatuner.core.LiveProfile;

/** Foreground USB diagnostics. Permission, sessions and displayed samples stay bound. */
public final class MainActivity extends Activity {
    static final int BG = Color.rgb(13, 23, 38), SURFACE = Color.rgb(24, 39, 57);
    static final int INK = Color.rgb(237, 244, 250), MUTED = Color.rgb(164, 183, 203), GREEN = Color.rgb(68, 215, 160);
    private static final String USB_PERMISSION = "id.hondatuner.android.USB_PERMISSION";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<UsbDevice> devices = new ArrayList<>();
    private final Map<String, List<String>> catalog = new LinkedHashMap<>();
    private final Map<String, SensorCard> cards = new LinkedHashMap<>();
    private UsbManager usb;
    private DiagnosticController controller;
    private UsbDevice activeDevice, pendingDevice;
    private long permissionSerial, displayedSession = -1, sampleCount;
    private boolean foreground, destroyed, registered;
    private Spinner adapter;
    private Button connect, refresh, liveTab, dtcTab, toggleLive, detect, readDtc;
    private TextView status, connectionNote, identity, liveNote, dtcCurrent, dtcStored, dtcNote;
    private ScrollView liveView, dtcView;
    private GridLayout grid;
    private RpmGraph graph;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (USB_PERMISSION.equals(intent.getAction())) {
                if (pendingDevice == null || intent.getLongExtra("request_token", -1) != permissionSerial || !foreground) return;
                UsbDevice selected = pendingDevice;
                pendingDevice = null;
                if (usb != null && usb.hasPermission(selected)) connectDevice(selected);
                else { connectionNote.setText("Izin USB belum diberikan. Tekan Hubungkan untuk mencoba lagi."); showControls(); }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                UsbDevice removed = usbDevice(intent);
                if (removed != null && ((activeDevice != null && activeDevice.getDeviceId() == removed.getDeviceId()) ||
                        (pendingDevice != null && pendingDevice.getDeviceId() == removed.getDeviceId()))) {
                    permissionSerial++; pendingDevice = null; activeDevice = null;
                    controller.disconnect("Adaptor USB dilepas.");
                }
                refreshAdapters();
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) { refreshAdapters(); }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usb = (UsbManager) getSystemService(Context.USB_SERVICE);
        buildViews();
        loadCatalog();
        controller = new DiagnosticController(new DiagnosticController.Listener() {
            @Override public void onState(DiagnosticController.Snapshot snapshot) {
                main.post(() -> { if (!destroyed && controller.isCurrent(snapshot.session)) showState(controller.snapshot()); });
            }
            @Override public void onProfile(long session, LiveProfile profile) {
                main.post(() -> {
                    if (destroyed || !controller.isCurrent(session)) return;
                    buildCards(profile.fields);
                    renderSample(profile.initial, false);
                    showControls();
                });
            }
            @Override public void onSample(long session, long generation, LiveProfile.Sample sample) {
                main.post(() -> {
                    if (!destroyed && controller.isCurrentLive(session, generation)) renderSample(sample, true);
                });
            }
            @Override public void onDtc(long session, long operation, HondaClient.DtcReport report) {
                main.post(() -> {
                    if (destroyed || !controller.isCurrentOperation(session, operation)) return;
                    dtcCurrent.setText(dtcText(report.current)); dtcStored.setText(dtcText(report.stored));
                    dtcNote.setText("Dibaca " + timeLabel(report.completedMillis) + " · " + identityLabel(controller.snapshot().ecuId));
                });
            }
            @Override public void onDtcError(long session, long operation, String reason) {
                main.post(() -> {
                    if (destroyed || !controller.isCurrentOperation(session, operation)) return;
                    dtcCurrent.setText("Belum terbaca."); dtcStored.setText("Belum terbaca.");
                    dtcNote.setText("Pembacaan gagal. " + reason);
                });
            }
        });
        IntentFilter filter = new IntentFilter(USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
        registered = true;
        showState(controller.snapshot());
    }
    @Override protected void onStart() { super.onStart(); foreground = true; refreshAdapters(); }
    @Override protected void onStop() {
        foreground = false;
        permissionSerial++; pendingDevice = null; activeDevice = null;
        controller.disconnect("Koneksi dihentikan saat aplikasi meninggalkan layar. Hubungkan kembali untuk membaca ECU.");
        super.onStop();
    }
    @Override protected void onDestroy() {
        destroyed = true;
        if (controller != null) controller.close();
        if (registered) unregisterReceiver(receiver);
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); refreshAdapters(); }
    private static UsbDevice usbDevice(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    private void buildViews() {
        LinearLayout root = column(); root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(12), dp(16), dp(8));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(16) + insets.getSystemWindowInsetLeft(), dp(12) + insets.getSystemWindowInsetTop(),
                    dp(16) + insets.getSystemWindowInsetRight(), dp(8) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.addView(text(getString(R.string.app_name), 26, INK, true));
        TextView subtitle = text(getString(R.string.usb_subtitle), 12, GREEN, false); subtitle.setPadding(0, dp(2), 0, dp(10)); root.addView(subtitle);
        LinearLayout connection = panel();
        status = text("TERPUTUS", 12, MUTED, true); connection.addView(status);
        LinearLayout selection = row();
        adapter = new Spinner(this); selection.addView(adapter, weight());
        refresh = button(getString(R.string.refresh), false); selection.addView(refresh, new LinearLayout.LayoutParams(dp(92), dp(48)));
        connection.addView(selection);
        connect = button(getString(R.string.connect), true); connection.addView(connect, new LinearLayout.LayoutParams(-1, dp(48)));
        identity = text("", 12, GREEN, false); identity.setPadding(0, dp(8), 0, 0); connection.addView(identity);
        connectionNote = text(getString(R.string.usb_help), 12, MUTED, false); connectionNote.setPadding(0, dp(5), 0, 0); connection.addView(connectionNote);
        root.addView(connection);
        LinearLayout tabs = row(); tabs.setPadding(0, dp(8), 0, dp(4));
        liveTab = button(getString(R.string.live_tab), true); dtcTab = button(getString(R.string.dtc_tab), false);
        tabs.addView(liveTab, weight()); tabs.addView(dtcTab, weight()); root.addView(tabs);
        FrameLayout pages = new FrameLayout(this); root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        liveView = new ScrollView(this); liveView.setFillViewport(true); pages.addView(liveView, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout live = column(); live.setPadding(0, dp(8), 0, 0); liveView.addView(live);
        LinearLayout controls = row(); toggleLive = button(getString(R.string.start_live), false); detect = button(getString(R.string.detect_again), false);
        controls.addView(toggleLive, weight()); controls.addView(detect, weight()); live.addView(controls);
        liveNote = text(getString(R.string.sensor_empty), 12, MUTED, false); liveNote.setPadding(0, dp(8), 0, dp(8)); live.addView(liveNote);
        LinearLayout chart = panel(); chart.addView(text(getString(R.string.rpm_history), 14, INK, true));
        graph = new RpmGraph(this); chart.addView(graph, new LinearLayout.LayoutParams(-1, dp(120))); live.addView(chart);
        grid = new GridLayout(this); grid.setColumnCount(getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2); grid.setPadding(0, dp(8), 0, 0); live.addView(grid);
        dtcView = new ScrollView(this); dtcView.setFillViewport(true); pages.addView(dtcView, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout dtcs = column(); dtcs.setPadding(0, dp(8), 0, 0); dtcView.addView(dtcs);
        readDtc = button(getString(R.string.read_dtc), true); dtcs.addView(readDtc, new LinearLayout.LayoutParams(-1, dp(48)));
        dtcNote = text(getString(R.string.dtc_unread), 12, MUTED, false); dtcNote.setPadding(0, dp(8), 0, dp(12)); dtcs.addView(dtcNote);
        LinearLayout current = panel(); current.addView(text(getString(R.string.dtc_current), 17, INK, true)); dtcCurrent = text(getString(R.string.dtc_unread), 14, MUTED, false); dtcCurrent.setPadding(0, dp(10), 0, 0); current.addView(dtcCurrent); dtcs.addView(current);
        LinearLayout stored = panel(); stored.addView(text(getString(R.string.dtc_stored), 17, INK, true)); dtcStored = text(getString(R.string.dtc_unread), 14, MUTED, false); dtcStored.setPadding(0, dp(10), 0, 0); stored.addView(dtcStored);
        LinearLayout.LayoutParams storedParams = new LinearLayout.LayoutParams(-1, -2); storedParams.topMargin = dp(12); dtcs.addView(stored, storedParams);
        setContentView(root);
        showTab(false);
        refresh.setOnClickListener(view -> refreshAdapters());
        connect.setOnClickListener(view -> onConnect());
        liveTab.setOnClickListener(view -> showTab(false)); dtcTab.setOnClickListener(view -> showTab(true));
        toggleLive.setOnClickListener(view -> controller.toggleLive());
        detect.setOnClickListener(view -> controller.detectAgain());
        readDtc.setOnClickListener(view -> {
            if (controller.readDtcs()) { dtcCurrent.setText("Membaca..."); dtcStored.setText("Membaca..."); dtcNote.setText("Live dijeda selama pembacaan DTC."); }
        });
    }

    private void onConnect() {
        if (pendingDevice != null) { permissionSerial++; pendingDevice = null; connectionNote.setText("Permintaan USB dibatalkan."); showControls(); return; }
        if (controller.snapshot().state != DiagnosticController.State.DISCONNECTED) {
            activeDevice = null; controller.disconnect("Koneksi diputus."); refreshAdapters(); return;
        }
        if (usb == null || devices.isEmpty()) { connectionNote.setText(getString(R.string.usb_help)); return; }
        int selected = adapter.getSelectedItemPosition(); if (selected < 0 || selected >= devices.size()) return;
        UsbDevice device = devices.get(selected);
        if (usb.hasPermission(device)) { connectDevice(device); return; }
        pendingDevice = device;
        long request = ++permissionSerial;
        Intent intent = new Intent(USB_PERMISSION).setPackage(getPackageName()).putExtra("request_token", request);
        PendingIntent pending = PendingIntent.getBroadcast(this, (int) (request & 0x7fffffff), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        connectionNote.setText("Izinkan akses adaptor USB pada dialog Android."); showControls();
        try { usb.requestPermission(device, pending); }
        catch (RuntimeException error) { pendingDevice = null; connectionNote.setText("Permintaan USB gagal. " + error.getMessage()); showControls(); }
    }
    private void connectDevice(UsbDevice device) {
        if (!foreground || destroyed) return;
        activeDevice = device;
        controller.connect(cancel -> FtdiChannel.open(usb, device, cancel));
    }
    private void refreshAdapters() {
        if (adapter == null) return;
        int selectedId = adapter.getSelectedItemPosition() >= 0 && adapter.getSelectedItemPosition() < devices.size() ? devices.get(adapter.getSelectedItemPosition()).getDeviceId() : -1;
        devices.clear();
        if (usb != null) for (UsbDevice device : usb.getDeviceList().values()) if (FtdiChannel.matches(device)) devices.add(device);
        Collections.sort(devices, Comparator.comparingInt(UsbDevice::getDeviceId));
        List<String> labels = new ArrayList<>();
        for (UsbDevice device : devices) labels.add("FT232R · USB #" + device.getDeviceId());
        if (labels.isEmpty()) labels.add(getString(R.string.adapter_missing));
        ArrayAdapter<String> choices = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); adapter.setAdapter(choices);
        for (int i = 0; i < devices.size(); i++) if (devices.get(i).getDeviceId() == selectedId) adapter.setSelection(i);
        if (controller != null) showControls();
    }
    private void showState(DiagnosticController.Snapshot snapshot) {
        if (displayedSession != snapshot.session) {
            displayedSession = snapshot.session; sampleCount = 0; cards.clear(); grid.removeAllViews(); graph.clear();
            liveNote.setText(getString(R.string.sensor_empty)); dtcCurrent.setText(getString(R.string.dtc_unread)); dtcStored.setText(getString(R.string.dtc_unread)); dtcNote.setText(getString(R.string.dtc_unread));
        }
        status.setText(snapshot.connected() ? "TERHUBUNG" : snapshot.state == DiagnosticController.State.CONNECTING ? "MENGHUBUNGKAN" : "TERPUTUS");
        status.setTextColor(snapshot.connected() ? GREEN : MUTED);
        identity.setText(snapshot.connected() ? identityLabel(snapshot.ecuId) : "");
        identity.setVisibility(snapshot.connected() ? View.VISIBLE : View.GONE);
        connectionNote.setText(snapshot.message);
        for (SensorCard card : cards.values()) card.setLive(snapshot.state == DiagnosticController.State.LIVE);
        showControls();
    }
    private void showControls() {
        DiagnosticController.Snapshot state = controller.snapshot();
        boolean disconnected = state.state == DiagnosticController.State.DISCONNECTED;
        connect.setText(pendingDevice != null ? R.string.cancel : disconnected ? R.string.connect : R.string.disconnect);
        connect.setEnabled(pendingDevice != null || !disconnected || !devices.isEmpty());
        adapter.setEnabled(disconnected && pendingDevice == null && !devices.isEmpty()); refresh.setEnabled(disconnected && pendingDevice == null);
        toggleLive.setText(state.state == DiagnosticController.State.LIVE ? R.string.pause_live : R.string.start_live);
        toggleLive.setEnabled(state.connected() && !state.busy() && state.hasSensors);
        detect.setEnabled(state.connected() && !state.busy()); readDtc.setEnabled(state.connected() && !state.busy());
        for (Button button : new Button[]{connect, refresh, toggleLive, detect, readDtc}) button.setAlpha(button.isEnabled() ? 1f : 0.45f);
    }
    private void showTab(boolean dtc) {
        liveView.setVisibility(dtc ? View.GONE : View.VISIBLE); dtcView.setVisibility(dtc ? View.VISIBLE : View.GONE);
        tint(liveTab, !dtc); tint(dtcTab, dtc);
    }
    private void buildCards(List<String> fields) {
        cards.clear(); grid.removeAllViews(); graph.clear(); sampleCount = 0;
        List<String> ordered = new ArrayList<>(fields);
        String[] priority = {"engine_speed", "tp_sensor_angle", "tp_sensor_1_angle", "battery_voltage", "ect_sensor_temperature", "eot_sensor_temperature", "iat_sensor_temperature", "map_sensor_pressure"};
        Collections.sort(ordered, Comparator.comparingInt(name -> {
            for (int i = 0; i < priority.length; i++) if (priority[i].equals(name)) return i;
            return priority.length;
        }));
        for (String name : ordered) {
            SensorCard card = new SensorCard(name); cards.put(name, card);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = 0; params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); params.setMargins(dp(3), dp(3), dp(3), dp(3)); grid.addView(card, params);
        }
    }
    private void renderSample(LiveProfile.Sample sample, boolean acquired) {
        boolean live = controller.snapshot().state == DiagnosticController.State.LIVE;
        for (Map.Entry<String, SensorCard> card : cards.entrySet()) card.getValue().setReading(sample.values.get(card.getKey()), live);
        Object rpm = sample.values.get("engine_speed"); graph.add(rpm instanceof Number ? ((Number) rpm).doubleValue() : null);
        if (acquired) sampleCount++;
        String reading = acquired ? "Pembaruan #" + sampleCount : "Sampel awal";
        liveNote.setText(reading + " · " + timeLabel(sample.completedMillis) + " · " + sample.validGroups + " grup terbaca" + (sample.failures.isEmpty() ? "" : " · ada sensor belum terbaca"));
    }
    private String dtcText(List<HondaClient.DtcCode> codes) {
        if (codes.isEmpty()) return getString(R.string.dtc_empty);
        StringBuilder result = new StringBuilder();
        for (HondaClient.DtcCode code : codes) { if (result.length() != 0) result.append("\n\n"); result.append(code.code()).append("\n").append(code.description()); }
        return result.toString();
    }
    private void loadCatalog() {
        try (BufferedReader source = new BufferedReader(new InputStreamReader(getAssets().open("ecu_catalog.tsv"), StandardCharsets.UTF_8))) {
            for (String line; (line = source.readLine()) != null;) {
                String[] parts = line.split("\t", -1); if (parts.length != 3) continue;
                List<String> rows = catalog.get(parts[0]); if (rows == null) { rows = new ArrayList<>(); catalog.put(parts[0], rows); }
                rows.add(parts[1] + (parts[2].isEmpty() ? "" : " · " + parts[2]));
            }
        } catch (IOException ignored) { catalog.clear(); }
    }
    private String identityLabel(String id) {
        if (id.isEmpty()) return "Identitas ECU belum terbaca";
        List<String> rows = catalog.get(id);
        if (rows == null) return "ECU " + id + " · model belum ada di katalog";
        if (rows.size() == 1) return rows.get(0) + "\nECU " + id;
        return "ECU " + id + " · " + rows.size() + " definisi cocok dalam katalog";
    }
    private static String timeLabel(long millis) { return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(millis)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private LinearLayout panel() { LinearLayout view = column(); view.setPadding(dp(12), dp(12), dp(12), dp(12)); view.setBackground(background(SURFACE)); return view; }
    private GradientDrawable background(int color) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(14)); return shape; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, dp(48), 1); }
    private TextView text(String label, float size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(label); view.setTextSize(size); view.setTextColor(color); if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }
    private Button button(String label, boolean primary) { Button view = new Button(this); view.setText(label); view.setAllCaps(false); view.setTextSize(13); view.setMinWidth(0); view.setMinHeight(0); tint(view, primary); return view; }
    private void tint(Button view, boolean primary) { view.setBackgroundTintList(ColorStateList.valueOf(primary ? GREEN : SURFACE)); view.setTextColor(primary ? BG : INK); }
    private String sensorLabel(String name) {
        switch (name) {
            case "engine_speed": return "Putaran mesin";
            case "tp_sensor_angle": case "tp_sensor_1_angle": return "Posisi gas";
            case "battery_voltage": return "Tegangan baterai";
            case "ect_sensor_temperature": return "Suhu pendingin";
            case "eot_sensor_temperature": return "Suhu oli";
            case "iat_sensor_temperature": return "Suhu udara masuk";
            case "map_sensor_pressure": return "Tekanan manifold";
            case "spark_advance": return "Sudut pengapian";
            case "injector": return "Sinyal injektor";
            default: return name.replace('_', ' ').replace('.', '/');
        }
    }
    private static String unit(String name) {
        if (name.equals("engine_speed")) return "RPM";
        if (name.contains("voltage")) return "V";
        if (name.endsWith("_temperature")) return "°C";
        if (name.endsWith("_angle") || name.equals("spark_advance")) return "°";
        return "";
    }
    private final class SensorCard extends LinearLayout {
        private final TextView reading, state;
        private final String sensor;
        private boolean valid;
        SensorCard(String sensor) {
            super(MainActivity.this); this.sensor = sensor; setOrientation(VERTICAL); setPadding(dp(12), dp(12), dp(12), dp(12)); setMinimumHeight(dp(118)); setBackground(background(SURFACE));
            TextView label = text(sensorLabel(sensor), 12, MUTED, false); label.setMinLines(2); addView(label);
            reading = text("—", sensor.equals("engine_speed") ? 29 : 25, INK, true); addView(reading);
            state = text("Belum terbaca", 11, MUTED, false); state.setPadding(0, dp(4), 0, 0); addView(state);
        }
        void setReading(Object value, boolean live) {
            valid = value != null && !(value instanceof Double && !Double.isFinite((Double) value));
            String label = "—";
            if (valid) {
                if (value instanceof Boolean) label = (Boolean) value ? "ON" : "OFF";
                else if (value instanceof Double) label = String.format(Locale.getDefault(), "%.2f", (Double) value);
                else label = value.toString();
                if (!unit(sensor).isEmpty()) label += " " + unit(sensor);
            }
            reading.setText(label); reading.setTextColor(valid ? INK : MUTED); setLive(live);
            setContentDescription(sensorLabel(sensor) + ", " + label + ", " + state.getText());
        }
        void setLive(boolean live) { state.setText(!valid ? "Belum terbaca" : live ? "Live" : "Sampel terakhir"); state.setTextColor(valid && live ? GREEN : MUTED); }
    }
}
