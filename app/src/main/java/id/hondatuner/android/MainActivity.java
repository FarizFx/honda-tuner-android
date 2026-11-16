package id.hondatuner.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import id.hondatuner.android.usb.FtdiChannel;
import id.hondatuner.core.DiagnosticController;
import id.hondatuner.core.DiagnosticLog;
import id.hondatuner.core.HondaClient;
import id.hondatuner.core.KLineTransport;
import id.hondatuner.core.LiveProfile;
import id.hondatuner.core.HudLayout;
import id.hondatuner.core.HudGeometry;
import id.hondatuner.core.LcdDashboard;
import id.hondatuner.core.RefreshTiming;
import id.hondatuner.core.SensorInfo;
import id.hondatuner.core.SensorSelection;
import id.hondatuner.core.TelemetryHistory;

/** Foreground USB diagnostics with visible connection stages and real-sample history. */
public final class MainActivity extends Activity {
    static final int BG = Color.rgb(13, 23, 38), SURFACE = Color.rgb(24, 39, 57);
    static final int INK = Color.rgb(237, 244, 250), MUTED = Color.rgb(164, 183, 203);
    static final int GREEN = Color.rgb(68, 215, 160), RED = Color.rgb(255, 137, 137);
    private static final String USB_PERMISSION = "id.hondatuner.android.USB_PERMISSION";
    private static final String FAILURE_FILE = "last-diagnostic-failure.txt";
    private static final int EXPORT_REQUEST = 41;
    private static final long[] INTERVALS = {0, 50, 100, 250, 500, 1000, 2000};
    private static final int[] TIMEOUTS = {500, 1000, 2000};
    private static final String[] GROUPS = {"Semua sensor", "Mesin & udara", "Temperatur", "Tegangan", "O2 & bahan bakar", "Saklar & status"};
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private final DiagnosticLog log = new DiagnosticLog();
    private final TelemetryHistory history = new TelemetryHistory(600);
    private final RefreshTiming timing = new RefreshTiming();
    private final AtomicBoolean logDirty = new AtomicBoolean(true);
    private final AtomicReference<Failure> lastFailure = new AtomicReference<>();
    private final List<UsbDevice> devices = new ArrayList<>();
    private final Map<String, List<String>> catalog = new LinkedHashMap<>();
    private int catalogRows;
    private SensorSelection sensorSelection;
    private Button chooseSensors;
    private CheckBox focusSensors;
    private String catalogStatus = "Katalog belum dimuat.";
    private final Map<String, SensorCard> cards = new LinkedHashMap<>();
    private final List<String> graphFields = new ArrayList<>();
    private SharedPreferences preferences;
    private UsbManager usb;
    private DiagnosticController controller;
    private UsbDevice activeDevice, pendingDevice;
    private LiveProfile displayedProfile;
    private LiveProfile.Sample lastSample;
    private Failure shownFailure;
    private long permissionSerial, displayedSession = -1;
    private boolean foreground, registered, permissionResultReady, archivedTrace, exportInFlight, awaitingExport, connectionExpanded;
    private volatile boolean destroyed;
    private int selectedTab;
    private String pendingExportName = "", pendingExportFile = "", usbInventory = "", graphField = "";
    private String appContext = "", tableContext = "Sensor belum dideteksi.";
    private volatile String traceContext = "", stateContext = "";
    private Spinner adapter, interval, category, graphChoice, echoChoice, timeoutChoice;
    private EditText search;
    private Button connect, refresh, toggleLive, readOnce, detect, readDtc, saveCsv, resetStats, saveLog, copyLog, traceNow, traceLast, errorShortcut;
    private final Button[] tabs = new Button[4];
    private final ScrollView[] pages = new ScrollView[4];
    private TextView status, connectionNote, identity, liveNote, filterNote, tableNote, graphNote, dtcCurrent, dtcStored, dtcNote, debugInfo, debugSummary, debugText, failureNote;
    private ProgressBar connectionProgress;
    private Button connectionToggle;
    private LinearLayout connectionPanel;
    private GridLayout grid;
    private SensorGraph graph;
    private FrameLayout rootFrame, hudScreen;
    private LinearLayout normalScreen, hudControls, hudEditControls;
    private TextView hudEditHint;
    private SpeedometerView speedometer;
    private HudLayout hudLayout;
    private HudLayout hudBeforeEdit;
    private Button showHud, rotate, hudPause;
    private boolean hudMode;
    private int orientationBeforeHud = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private int systemUiBeforeHud;
    private android.window.OnBackInvokedCallback hudBackCallback;
    private final Runnable hideHudControls = () -> { if (hudControls != null) hudControls.setVisibility(View.GONE); };

    private static final class Failure {
        final String reason, trace, source;
        Failure(String reason, String trace, String source) { this.reason = reason; this.trace = trace; this.source = source; }
    }
    private final Runnable refreshScreen = new Runnable() {
        @Override public void run() {
            if (!foreground || destroyed) return;
            Failure failure = lastFailure.get();
            if (failure != shownFailure) {
                shownFailure = failure;
                errorShortcut.setVisibility(failure == null ? View.GONE : View.VISIBLE);
                if (failure != null) {
                    failureNote.setText(failure.reason);
                    if ("CONNECT".equals(failure.source) || "USB OPEN".equals(failure.source) || "USB PERMISSION".equals(failure.source)) {
                        archivedTrace = false; showTab(3);
                    }
                }
                logDirty.set(true);
            }
            refreshLiveNote();
            if (hudMode) refreshHud();
            if (selectedTab == 3 && logDirty.getAndSet(false)) renderDebug();
            main.postDelayed(this, 500);
        }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (USB_PERMISSION.equals(intent.getAction())) {
                if (pendingDevice == null || intent.getLongExtra("request_token", -1) != permissionSerial) return;
                permissionResultReady = true;
                log.info("USB PERMISSION", "Dialog selesai; foreground=" + foreground + ", granted=" + usb.hasPermission(pendingDevice));
                finishPermission();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                UsbDevice removed = usbDevice(intent);
                if (removed != null) log.warn("USB", "Dilepas: " + deviceLabel(removed));
                if (sameDevice(removed, activeDevice) || sameDevice(removed, pendingDevice)) {
                    permissionSerial++; pendingDevice = null; permissionResultReady = false; activeDevice = null;
                    controller.disconnect("Adaptor USB dilepas. Pasang kembali lalu tekan Hubungkan.");
                }
                refreshAdapters();
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                log.info("USB", "Adaptor dipasang"); refreshAdapters();
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("diagnostics", MODE_PRIVATE);
        hudLayout = readHudLayout(Collections.emptyList());
        sensorSelection = new SensorSelection(Arrays.asList(preferences.getString("sensor_order", "").split("\t")),
                preferences.getStringSet("hidden_sensors", Collections.emptySet()));
        usb = (UsbManager) getSystemService(Context.USB_SERVICE);
        appContext = "Honda Tuner " + versionLabel() + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" + Build.MANUFACTURER + " " + Build.MODEL;
        traceContext = appContext;
        log.setDetailed(preferences.getBoolean("detailed_log", true));
        log.setSink(entry -> {
            int priority = "ERROR".equals(entry.level) ? Log.ERROR : "WARN".equals(entry.level) ? Log.WARN : Log.DEBUG;
            Log.println(priority, "HondaTuner", entry.line());
            logDirty.set(true);
            if ("ERROR".equals(entry.level)) {
                Failure failure = new Failure(entry.source + " · " + firstLine(entry.message), captureTrace(0), entry.source);
                lastFailure.set(failure);
                files.execute(() -> {
                    try { Files.write(new File(getFilesDir(), FAILURE_FILE).toPath(), (failure.reason + "\n\n" + failure.trace).getBytes(StandardCharsets.UTF_8)); }
                    catch (IOException error) { log.warn("LOG SAVE", "Log error belum tersimpan: " + error.getMessage()); }
                });
            }
        });
        log.info("APP", appContext);
        if (savedInstanceState != null) {
            permissionSerial = savedInstanceState.getLong("permission_serial");
            if (Build.VERSION.SDK_INT >= 33) pendingDevice = savedInstanceState.getParcelable("pending_device", UsbDevice.class);
            else pendingDevice = savedInstanceState.getParcelable("pending_device");
            permissionResultReady = savedInstanceState.getBoolean("permission_ready");
            selectedTab = savedInstanceState.getInt("tab"); archivedTrace = savedInstanceState.getBoolean("archived_trace");
            exportInFlight = savedInstanceState.getBoolean("export_in_flight");
            awaitingExport = exportInFlight;
            pendingExportName = savedInstanceState.getString("export_name", "");
            pendingExportFile = savedInstanceState.getString("export_file", "");
        }
        buildViews();
        loadCatalog();
        controller = new DiagnosticController(new DiagnosticController.Listener() {
            @Override public void onState(DiagnosticController.Snapshot snapshot) {
                stateContext = sessionContext(snapshot);
                logDirty.set(true);
                main.post(() -> { if (!destroyed && controller.isCurrent(snapshot.session)) showState(controller.snapshot()); });
            }
            @Override public void onProfile(long session, LiveProfile profile) {
                main.post(() -> {
                    if (destroyed || !controller.isCurrent(session)) return;
                    displayedProfile = profile; buildCards(profile.fields); renderSample(profile.initial);
                    StringBuilder tables = new StringBuilder();
                    for (LiveProfile.Table table : profile.tables) {
                        if (tables.length() != 0) tables.append(" · ");
                        tables.append(String.format(Locale.ROOT, "0x%02X → 0x%02X (%d byte)", table.request, table.response, table.bodyLength));
                    }
                    tableContext = tables.toString(); tableNote.setText("Tabel ECU: " + tableContext);
                    logDirty.set(true); showControls();
                });
            }
            @Override public void onSample(long session, long generation, LiveProfile.Sample sample) {
                main.post(() -> {
                    if (destroyed || !controller.isCurrentLive(session, generation)) return;
                    timing.record(generation, sample, controller.snapshot().state == DiagnosticController.State.LIVE);
                    renderSample(sample);
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
                    dtcNote.setText("Pembacaan gagal. " + reason + "\nDetail tersedia di Debug.");
                });
            }
        }, log);
        controller.setLiveIntervalMillis(INTERVALS[boundedIndex(interval.getSelectedItemPosition(), INTERVALS.length)]);
        IntentFilter filter = new IntentFilter(USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        // The flags overload is available at minSdk 26; exported flags start at API 33.
        int receiverFlags = Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0;
        registerReceiver(receiver, filter, receiverFlags);
        registered = true;
        files.execute(() -> {
            File previous = new File(getFilesDir(), FAILURE_FILE);
            if (!previous.isFile()) return;
            try {
                String trace = new String(Files.readAllBytes(previous.toPath()), StandardCharsets.UTF_8);
                lastFailure.compareAndSet(null, new Failure(firstLine(trace), trace, "PREVIOUS")); logDirty.set(true);
            } catch (IOException error) { log.warn("LOG LOAD", error.getMessage()); }
        });
        showState(controller.snapshot());
        if (savedInstanceState != null && savedInstanceState.getBoolean("hud_mode")) setHudMode(true);
        if (savedInstanceState != null && hudMode && savedInstanceState.getBoolean("hud_editing")) {
            beginHudLayoutEdit();
            ArrayList<String> auxiliary = savedInstanceState.getStringArrayList("hud_edit_auxiliary");
            String primary = savedInstanceState.getString("hud_edit_primary", hudLayout.primary);
            if (auxiliary != null) hudLayout = new HudLayout(primary, auxiliary, hudLayout.theme, hudLayout.digitScale,
                    hudLayout.mirrored, hudLayout.landscape, hudLayout.focused, hudLayout.rpmBar, hudLayout.rpmLimit, hudLayout.geometry);
            refreshHud();
            speedometer.beginEdit(HudGeometry.decode(savedInstanceState.getString("hud_edit_portrait"), savedInstanceState.getString("hud_edit_landscape")));
            String selected = savedInstanceState.getString("hud_edit_selected");
            if (selected != null && LcdDashboard.blocks(hudLayout).contains(selected)) speedometer.selectBlock(selected);
        }
    }
    @Override protected void onStart() {
        super.onStart(); foreground = true; log.info("LIFECYCLE", "Foreground"); refreshAdapters();
        speedometer.setKeepScreenOn(hudMode);
        if (hudMode) applyHudSystemBars();
        finishPermission(); main.removeCallbacks(refreshScreen); main.post(refreshScreen);
    }
    @Override protected void onStop() {
        foreground = false; main.removeCallbacks(refreshScreen);
        main.removeCallbacks(hideHudControls); hideHudControls.run();
        speedometer.setKeepScreenOn(false);
        // A permission dialog owns no UART. Consume its pending result on return.
        // Active ECU communication always ends when the screen leaves the foreground.
        activeDevice = null;
        if (controller != null && controller.snapshot().state != DiagnosticController.State.DISCONNECTED) {
            controller.disconnect("Koneksi dihentikan saat aplikasi meninggalkan layar. Hubungkan kembali untuk membaca ECU.");
        }
        log.info("LIFECYCLE", "Background; UART ditutup, pending permission=" + (pendingDevice != null));
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("permission_serial", permissionSerial); state.putParcelable("pending_device", pendingDevice);
        state.putBoolean("permission_ready", permissionResultReady); state.putInt("tab", selectedTab);
        state.putBoolean("archived_trace", archivedTrace); state.putBoolean("export_in_flight", awaitingExport);
        state.putString("export_name", pendingExportName); state.putString("export_file", pendingExportFile);
        state.putBoolean("hud_mode", hudMode);
        state.putBoolean("hud_editing", speedometer.isEditing());
        if (speedometer.isEditing()) {
            state.putString("hud_edit_portrait", speedometer.editedGeometry().encode(false));
            state.putString("hud_edit_landscape", speedometer.editedGeometry().encode(true));
            state.putString("hud_edit_selected", speedometer.selectedBlock());
            state.putString("hud_edit_primary", hudLayout.primary);
            state.putStringArrayList("hud_edit_auxiliary", new ArrayList<>(hudLayout.auxiliary));
        }
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        destroyed = true;
        if (Build.VERSION.SDK_INT >= 33 && hudBackCallback != null) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(hudBackCallback);
        if (controller != null) controller.close();
        if (registered) unregisterReceiver(receiver);
        if (!awaitingExport) deleteExport(pendingExportFile);
        log.setSink(null); files.shutdown(); main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); refreshAdapters(); }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        grid.setColumnCount(configuration.screenWidthDp >= 600 ? 3 : 2); applyFilter();
        rotate.setText(configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ? "Mode tegak" : "Mode miring");
        if (configuration.screenHeightDp < 500) { connectionExpanded = false; connectionPanel.setVisibility(View.GONE); }
        speedometer.requestLayout(); refreshHud();
        log.info("LIFECYCLE", "Rotasi layar; sesi USB dan statistik dipertahankan.");
    }
    @Override public void onBackPressed() {
        if (hudMode) hudBack(); else super.onBackPressed();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && hudMode) applyHudSystemBars();
    }
    private static UsbDevice usbDevice(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }
    private static boolean sameDevice(UsbDevice a, UsbDevice b) { return a != null && b != null && a.getDeviceId() == b.getDeviceId(); }

    private void buildViews() {
        LinearLayout root = column(); root.setBackgroundColor(BG); root.setPadding(dp(14), dp(8), dp(14), dp(6));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(14) + insets.getSystemWindowInsetLeft(), dp(8) + insets.getSystemWindowInsetTop(),
                    dp(14) + insets.getSystemWindowInsetRight(), dp(6) + insets.getSystemWindowInsetBottom()); return insets;
        });
        LinearLayout heading = row(); heading.addView(text(getString(R.string.app_name), 24, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(text("USB OTG · " + versionLabel(), 11, GREEN, false)); root.addView(heading);
        connectionExpanded = getResources().getConfiguration().screenHeightDp >= 500;
        connectionToggle = button("USB / ECU · Detail koneksi", false);
        root.addView(connectionToggle, new LinearLayout.LayoutParams(-1, dp(36)));
        LinearLayout connection = panel(); connectionPanel = connection;
        connection.setVisibility(connectionExpanded ? View.VISIBLE : View.GONE);
        connectionToggle.setOnClickListener(view -> { connectionExpanded = !connectionExpanded; connectionPanel.setVisibility(connectionExpanded ? View.VISIBLE : View.GONE); });
        status = text("USB / ECU", 12, MUTED, true); connection.addView(status);
        LinearLayout selection = row(); adapter = new Spinner(this); selection.addView(adapter, weight());
        refresh = button(getString(R.string.refresh), false); selection.addView(refresh, new LinearLayout.LayoutParams(dp(88), dp(44))); connection.addView(selection);
        connect = button(getString(R.string.connect), true); connection.addView(connect, new LinearLayout.LayoutParams(-1, dp(44)));
        connectionProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); connectionProgress.setIndeterminate(true); connectionProgress.setIndeterminateTintList(ColorStateList.valueOf(GREEN));
        connection.addView(connectionProgress, new LinearLayout.LayoutParams(-1, dp(4))); connectionProgress.setVisibility(View.GONE);
        identity = text("", 12, GREEN, false); connection.addView(identity);
        connectionNote = text(getString(R.string.usb_help), 12, MUTED, false); connectionNote.setTextIsSelectable(true); connection.addView(connectionNote);
        errorShortcut = button("Log error terakhir · Buka Debug", false); errorShortcut.setTextColor(RED); errorShortcut.setVisibility(View.GONE);
        connection.addView(errorShortcut, new LinearLayout.LayoutParams(-1, dp(36))); root.addView(connection);
        LinearLayout tabRow = row(); tabRow.setPadding(0, dp(5), 0, dp(3));
        String[] labels = {"Live", "Grafik", "DTC", "Debug"};
        for (int i = 0; i < tabs.length; i++) { final int tab = i; tabs[i] = button(labels[i], i == selectedTab); tabRow.addView(tabs[i], weight()); tabs[i].setOnClickListener(view -> showTab(tab)); }
        root.addView(tabRow);
        FrameLayout pageFrame = new FrameLayout(this); root.addView(pageFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout[] content = new LinearLayout[4];
        for (int i = 0; i < pages.length; i++) {
            pages[i] = new ScrollView(this); pages[i].setFillViewport(true); pageFrame.addView(pages[i], new FrameLayout.LayoutParams(-1, -1));
            content[i] = column(); content[i].setPadding(0, dp(6), 0, dp(12)); pages[i].addView(content[i]);
        }
        buildLive(content[0]); buildGraph(content[1]); buildDtcs(content[2]); buildDebug(content[3]);
        normalScreen = root; rootFrame = new FrameLayout(this); rootFrame.setBackgroundColor(BG);
        rootFrame.addView(root, new FrameLayout.LayoutParams(-1, -1)); buildHud();
        setContentView(rootFrame); showTab(selectedTab);
        refresh.setOnClickListener(view -> refreshAdapters()); connect.setOnClickListener(view -> onConnect());
        errorShortcut.setOnClickListener(view -> { archivedTrace = true; showTab(3); });
    }
    private void buildLive(LinearLayout live) {
        LinearLayout controls = row(); toggleLive = button("Mulai live", true); readOnce = button("Baca sekali", false);
        controls.addView(toggleLive, weight()); controls.addView(readOnce, weight()); live.addView(controls);
        LinearLayout modes = row(); showHud = button("Speedometer LCD", true);
        rotate = button(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? "Mode tegak" : "Mode miring", false);
        modes.addView(showHud, weight()); modes.addView(rotate, weight()); live.addView(modes);
        showHud.setOnClickListener(view -> setHudMode(true));
        rotate.setOnClickListener(view -> setRequestedOrientation(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE));
        LinearLayout target = row(); target.addView(text("Target refresh", 12, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        interval = new Spinner(this); choices(interval, Arrays.asList("0 ms", "50 ms", "100 ms", "250 ms", "500 ms", "1.000 ms", "2.000 ms")); interval.setSelection(savedIntervalIndex());
        target.addView(interval, new LinearLayout.LayoutParams(dp(155), dp(44))); live.addView(target);
        liveNote = text(getString(R.string.sensor_empty), 12, MUTED, false); padded(liveNote); live.addView(liveNote);
        LinearLayout options = row(); detect = button("Deteksi ulang", false); saveCsv = button("Simpan CSV", false);
        options.addView(detect, weight()); options.addView(saveCsv, weight()); live.addView(options);
        LinearLayout filters = panel(); search = new EditText(this); search.setSingleLine(true); search.setTextSize(14); search.setTextColor(INK); search.setHintTextColor(MUTED); search.setHint("Cari RPM, gas, baterai, sensor…"); filters.addView(search, new LinearLayout.LayoutParams(-1, dp(44)));
        category = new Spinner(this); choices(category, Arrays.asList(GROUPS)); filters.addView(category, new LinearLayout.LayoutParams(-1, dp(44)));
        chooseSensors = button("Pilih & urutkan sensor", false); filters.addView(chooseSensors, new LinearLayout.LayoutParams(-1, dp(44)));
        chooseSensors.setOnClickListener(view -> editSensors());
        focusSensors = new CheckBox(this); focusSensors.setText("Baca hanya grup sensor pilihan"); focusSensors.setTextColor(INK); focusSensors.setTextSize(13);
        focusSensors.setChecked(preferences.getBoolean("focus_sensors", false)); filters.addView(focusSensors);
        filters.addView(text("Aktifkan untuk mempercepat sensor pilihan. Sensor dalam satu grup tetap dibaca bersama; grup lain tidak direkam di grafik/CSV. Pencarian dan kategori hanya menyaring tampilan.", 11, MUTED, false));
        focusSensors.setOnCheckedChangeListener((button, checked) -> syncSensorPolling());
        filterNote = text("Belum ada sensor.", 11, MUTED, false); filters.addView(filterNote); spaced(live, filters);
        grid = new GridLayout(this); grid.setColumnCount(getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2); grid.setPadding(0, dp(6), 0, 0); live.addView(grid);
        tableNote = text("Ukuran dan jenis tabel mengikuti balasan ECU.", 11, MUTED, false); padded(tableNote); live.addView(tableNote);
        resetStats = button("Reset statistik & riwayat", false); live.addView(resetStats, new LinearLayout.LayoutParams(-1, dp(44)));
        toggleLive.setOnClickListener(view -> controller.toggleLive()); readOnce.setOnClickListener(view -> controller.readOnce());
        detect.setOnClickListener(view -> controller.detectAgain());
        saveCsv.setOnClickListener(view -> exportText(history.csv(), "text/csv", "HondaTuner-live-" + filenameTime() + ".csv"));
        resetStats.setOnClickListener(view -> {
            history.reset(new ArrayList<>(cards.keySet())); timing.reset();
            for (SensorCard card : cards.values()) card.refreshStatistics(); refreshGraph(); refreshLiveNote(); showControls();
            log.info("UI", "Statistik dan riwayat sampel direset.");
        });
        selected(interval, position -> {
            timing.reset();
            preferences.edit().putLong("interval_ms", INTERVALS[position]).apply();
            if (controller != null) controller.setLiveIntervalMillis(INTERVALS[position]);
        });
        selected(category, position -> applyFilter());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable text) { }
        });
    }
    private int savedIntervalIndex() {
        long value = 0;
        if (preferences.contains("interval_ms")) value = preferences.getLong("interval_ms", 0);
        else if (preferences.contains("interval_index")) {
            long[] previous = {250, 500, 1000, 2000};
            value = previous[boundedIndex(preferences.getInt("interval_index", 1), previous.length)];
        }
        for (int i = 0; i < INTERVALS.length; i++) if (INTERVALS[i] == value) return i;
        return 0;
    }
    private void editSensors() {
        if (cards.isEmpty()) return;
        SensorSelection draft = new SensorSelection(sensorSelection.savedOrder(), sensorSelection.hidden());
        List<String> available = new ArrayList<>(cards.keySet());
        ScrollView scroll = new ScrollView(this); LinearLayout list = column(); scroll.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Sensor & urutan tampilan").setView(scroll)
                .setNegativeButton("Batal", null).setNeutralButton("Semua & urutan awal", null)
                .setPositiveButton("Simpan", (ignored, which) -> {
                    sensorSelection = draft;
                    preferences.edit().putString("sensor_order", String.join("\t", draft.savedOrder())).putStringSet("hidden_sensors", draft.hidden()).apply();
                    applyFilter(); syncSensorPolling();
                }).create();
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            int offset = scroll.getScrollY(); list.removeAllViews();
            List<String> ordered = draft.order(available);
            for (int i = 0; i < ordered.size(); i++) {
                String name = ordered.get(i); LinearLayout item = row();
                CheckBox shown = new CheckBox(this); shown.setText(SensorInfo.label(name)); shown.setTextColor(INK); shown.setTextSize(13);
                shown.setChecked(draft.isVisible(name)); shown.setOnCheckedChangeListener((button, checked) -> draft.setVisible(name, checked));
                item.addView(shown, new LinearLayout.LayoutParams(0, -2, 1));
                Button up = button("↑", false), down = button("↓", false);
                up.setContentDescription("Naikkan " + SensorInfo.label(name)); down.setContentDescription("Turunkan " + SensorInfo.label(name));
                up.setEnabled(i > 0); down.setEnabled(i < ordered.size() - 1);
                up.setOnClickListener(view -> { draft.move(name, -1, available); draw[0].run(); });
                down.setOnClickListener(view -> { draft.move(name, 1, available); draw[0].run(); });
                item.addView(up, new LinearLayout.LayoutParams(dp(44), dp(44))); item.addView(down, new LinearLayout.LayoutParams(dp(44), dp(44)));
                list.addView(item);
            }
            scroll.post(() -> scroll.scrollTo(0, offset));
        };
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
            draft.reset(available);
            draw[0].run();
        }));
        draw[0].run(); dialog.show();
    }
    private void syncSensorPolling() {
        if (controller == null || focusSensors == null || displayedProfile == null) return;
        if (hudMode && hudLayout.focused) {
            List<String> needed = hudLayout.pollingFields(displayedProfile.fields);
            controller.setLiveFields(needed.isEmpty() ? null : needed); return;
        }
        List<String> visible = sensorSelection.visible(displayedProfile.fields);
        if (focusSensors.isChecked() && visible.isEmpty()) {
            focusSensors.setChecked(false); toast("Pilih minimal satu sensor untuk membatasi pembacaan.");
        }
        preferences.edit().putBoolean("focus_sensors", focusSensors.isChecked()).apply();
        controller.setLiveFields(focusSensors.isChecked() ? visible : null);
    }
    private HudLayout readHudLayout(List<String> fields) {
        HudLayout defaults = HudLayout.defaults(fields);
        List<String> auxiliary = new ArrayList<>();
        for (int i = 0; i < 3; i++) auxiliary.add(preferences.getString("hud_aux_" + i, defaults.auxiliary.get(i)));
        return new HudLayout(preferences.getString("hud_primary", defaults.primary), auxiliary,
                preferences.getInt("hud_theme", defaults.theme), preferences.getFloat("hud_scale", 1f),
                preferences.getBoolean("hud_mirror", false), preferences.getBoolean("hud_landscape", true),
                preferences.getBoolean("hud_focus", true), preferences.getBoolean("hud_rpm_bar", true),
                preferences.getInt("hud_rpm_limit", defaults.rpmLimit),
                HudGeometry.decode(preferences.getString("hud_boxes_portrait", null), preferences.getString("hud_boxes_landscape", null)));
    }
    private void buildHud() {
        hudScreen = new FrameLayout(this); hudScreen.setBackgroundColor(Color.rgb(5, 14, 16)); hudScreen.setVisibility(View.GONE);
        hudScreen.setOnApplyWindowInsetsListener((view, insets) -> {
            int left = insets.getSystemWindowInsetLeft(), top = insets.getSystemWindowInsetTop(),
                    right = insets.getSystemWindowInsetRight(), bottom = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                left = Math.max(left, insets.getDisplayCutout().getSafeInsetLeft()); top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
                right = Math.max(right, insets.getDisplayCutout().getSafeInsetRight()); bottom = Math.max(bottom, insets.getDisplayCutout().getSafeInsetBottom());
            }
            view.setPadding(dp(6) + left, top, dp(6) + right, dp(4) + bottom); return insets;
        });
        speedometer = new SpeedometerView(this); hudScreen.addView(speedometer, new FrameLayout.LayoutParams(-1, -1));
        hudControls = panel(); hudControls.setVisibility(View.GONE);
        LinearLayout controls = row(); Button back = button("‹ Live", false), settings = button("Atur HUD", false);
        hudPause = button("Mulai live", true);
        controls.addView(back, weight()); controls.addView(hudPause, weight()); hudControls.addView(controls);
        LinearLayout editRow = row(); Button editLayout = button("Edit tata letak", true);
        editRow.addView(settings, weight()); editRow.addView(editLayout, weight()); hudControls.addView(editRow);
        FrameLayout.LayoutParams menuPosition = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        menuPosition.setMargins(dp(8), dp(8), dp(8), dp(8)); hudScreen.addView(hudControls, menuPosition);
        hudEditControls = column(); hudEditControls.setPadding(dp(6), dp(4), dp(6), dp(3)); hudEditControls.setBackground(background(SURFACE));
        LinearLayout tools = row(); Button save = button("Simpan", true), cancel = button("Batal", false), options = button("⋯ Kotak", false);
        tools.addView(save, weight()); tools.addView(cancel, weight()); tools.addView(options, weight()); hudEditControls.addView(tools);
        hudEditHint = text("Pilih kotak, geser, atau tarik titik sudut untuk mengubah ukuran.", 11, INK, false); hudEditHint.setPadding(dp(6), 0, dp(6), 0);
        hudEditHint.setLines(2);
        hudEditControls.addView(hudEditHint); hudEditControls.setVisibility(View.GONE);
        hudScreen.addView(hudEditControls, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        hudEditControls.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> updateHudEditorMargin());
        rootFrame.addView(hudScreen, new FrameLayout.LayoutParams(-1, -1));
        back.setOnClickListener(view -> setHudMode(false)); settings.setOnClickListener(view -> { hideHudControls.run(); editHud(); });
        editLayout.setOnClickListener(view -> beginHudLayoutEdit());
        hudPause.setOnClickListener(view -> { if (controller != null) controller.toggleLive(); hideHudControls.run(); });
        save.setOnClickListener(view -> finishHudLayoutEdit(true)); cancel.setOnClickListener(view -> finishHudLayoutEdit(false));
        options.setOnClickListener(this::hudEditorOptions);
        speedometer.setOnClickListener(view -> { if (!speedometer.isEditing()) toggleHudControls(); });
        speedometer.setEditorListener(selected -> hudEditHint.setText(selected == null ? "Pilih kotak untuk menggeser atau mengubah ukurannya."
                : LcdDashboard.blockLabel(selected, hudLayout) + " · Geser kotak / tarik titik sudut"));
        refreshHud();
    }
    private void toggleHudControls() {
        main.removeCallbacks(hideHudControls);
        boolean showing = hudControls.getVisibility() != View.VISIBLE;
        hudControls.setVisibility(showing ? View.VISIBLE : View.GONE);
        if (showing) main.postDelayed(hideHudControls, 4000);
    }
    private void beginHudLayoutEdit() {
        if (!hudMode) setHudMode(true);
        if (speedometer.isEditing()) return;
        main.removeCallbacks(hideHudControls); hideHudControls.run();
        hudBeforeEdit = hudLayout; speedometer.beginEdit(hudLayout.geometry); hudEditControls.setVisibility(View.VISIBLE);
        hudEditControls.post(this::updateHudEditorMargin);
    }
    private void updateHudEditorMargin() {
        FrameLayout.LayoutParams position = (FrameLayout.LayoutParams) speedometer.getLayoutParams();
        int margin = speedometer.isEditing() ? hudEditControls.getHeight() : 0;
        if (position.topMargin != margin) { position.topMargin = margin; speedometer.setLayoutParams(position); }
    }
    private void finishHudLayoutEdit(boolean save) {
        if (!speedometer.isEditing()) return;
        HudGeometry geometry = speedometer.finishEdit(save);
        if (save) {
            hudLayout = hudLayout.withGeometry(geometry);
            saveHudLayout();
        } else if (hudBeforeEdit != null) hudLayout = hudBeforeEdit;
        hudBeforeEdit = null;
        hudEditControls.setVisibility(View.GONE); updateHudEditorMargin(); timing.reset(); syncSensorPolling(); refreshHud();
        toast(save ? "Posisi dan ukuran HUD disimpan." : "Perubahan tata letak dibatalkan.");
    }
    private void hudEditorOptions(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor); String selected = speedometer.selectedBlock();
        menu.getMenu().add(0, 1, 0, "Ganti sensor kotak ini").setEnabled(selected != null && (HudGeometry.PRIMARY.equals(selected) || selected.startsWith("aux")));
        menu.getMenu().add(0, 2, 1, "Pilih kotak");
        menu.getMenu().add(0, 3, 2, "Reset kotak ini").setEnabled(selected != null);
        menu.getMenu().add(0, 4, 3, "Reset tata letak orientasi ini");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) editHudBlockSensor();
            else if (item.getItemId() == 2) {
                List<String> ids = LcdDashboard.blocks(hudLayout), labels = new ArrayList<>();
                for (String id : ids) labels.add(LcdDashboard.blockLabel(id, hudLayout));
                new AlertDialog.Builder(this).setTitle("Pilih kotak").setItems(labels.toArray(new String[0]), (dialog, which) -> speedometer.selectBlock(ids.get(which))).show();
            } else if (item.getItemId() == 3) speedometer.resetSelected();
            else speedometer.resetPlacement();
            return true;
        }); menu.show();
    }
    private void editHudBlockSensor() {
        String id = speedometer.selectedBlock(); if (id == null) return;
        boolean primary = HudGeometry.PRIMARY.equals(id); int slot = primary ? -1 : Integer.parseInt(id.substring(3));
        List<String> fields = hudFields(), labels = new ArrayList<>(); if (!primary) fields.add(0, "");
        for (String field : fields) labels.add(hudFieldLabel(field));
        String current = primary ? hudLayout.primary : hudLayout.auxiliary.get(slot);
        new AlertDialog.Builder(this).setTitle(LcdDashboard.blockLabel(id, hudLayout))
                .setSingleChoiceItems(labels.toArray(new String[0]), fields.indexOf(current), (dialog, which) -> {
                    List<String> auxiliary = new ArrayList<>(hudLayout.auxiliary); String mainField = hudLayout.primary;
                    if (primary) mainField = fields.get(which); else auxiliary.set(slot, fields.get(which));
                    hudLayout = new HudLayout(mainField, auxiliary, hudLayout.theme, hudLayout.digitScale, hudLayout.mirrored,
                            hudLayout.landscape, hudLayout.focused, hudLayout.rpmBar, hudLayout.rpmLimit, hudLayout.geometry);
                    timing.reset(); syncSensorPolling(); refreshHud();
                    speedometer.selectBlock(primary || !fields.get(which).isEmpty() ? id : HudGeometry.PRIMARY); dialog.dismiss();
                }).setNegativeButton("Tutup", null).show();
    }
    private void hudBack() { if (speedometer.isEditing()) finishHudLayoutEdit(false); else setHudMode(false); }
    private void applyHudSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController bars = getWindow().getInsetsController();
            if (bars != null) {
                if (hudMode) { bars.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); bars.hide(android.view.WindowInsets.Type.systemBars()); }
                else bars.show(android.view.WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(hudMode ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION : systemUiBeforeHud);
        }
    }
    private void setHudMode(boolean enabled) {
        if (hudMode == enabled) return;
        if (enabled) { orientationBeforeHud = getRequestedOrientation(); systemUiBeforeHud = getWindow().getDecorView().getSystemUiVisibility(); }
        else if (speedometer.isEditing()) finishHudLayoutEdit(false);
        hudMode = enabled; normalScreen.setVisibility(enabled ? View.GONE : View.VISIBLE);
        hudScreen.setVisibility(enabled ? View.VISIBLE : View.GONE); speedometer.setKeepScreenOn(enabled && foreground);
        main.removeCallbacks(hideHudControls); hideHudControls.run(); applyHudSystemBars();
        (enabled ? hudScreen : normalScreen).requestApplyInsets();
        if (Build.VERSION.SDK_INT >= 33) {
            if (enabled) {
                if (hudBackCallback == null) hudBackCallback = this::hudBack;
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, hudBackCallback);
            } else if (hudBackCallback != null) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(hudBackCallback);
        }
        if (enabled) setRequestedOrientation(hudLayout.landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        else setRequestedOrientation(orientationBeforeHud);
        timing.reset(); syncSensorPolling();
        log.info("HUD", enabled ? "LCD dibuka; fokus=" + hudLayout.focused + ", utama=" + hudLayout.primary : "Kembali ke Live; pilihan pembacaan Live dipulihkan.");
        if (!enabled) refreshVisibleReadings();
        refreshHud(); refreshLiveNote(); showControls();
        if (enabled && !preferences.getBoolean("hud_tap_tip_seen", false)) {
            toast("Ketuk layar untuk kontrol dan Edit tata letak."); preferences.edit().putBoolean("hud_tap_tip_seen", true).apply();
        }
    }
    private List<String> hudFields() {
        List<String> fields = new ArrayList<>(graphFields);
        for (String field : Arrays.asList("vss", "engine_speed", "tp_sensor_angle", "battery_voltage", "ect_sensor_temperature", hudLayout.primary))
            if (!fields.contains(field)) fields.add(field);
        for (String field : hudLayout.auxiliary) if (!field.isEmpty() && !fields.contains(field)) fields.add(field);
        return fields;
    }
    private String hudFieldLabel(String field) {
        if (field.isEmpty()) return "Sembunyikan";
        String unit = SensorInfo.unit(field);
        boolean available = displayedProfile != null && displayedProfile.fields.contains(field);
        return SensorInfo.label(field) + " · " + (unit.isEmpty() ? "raw" : unit) + (available ? "" : " · belum tersedia");
    }
    private Spinner hudChoice(LinearLayout form, String label, List<String> fields, String value) {
        form.addView(text(label, 12, MUTED, false)); Spinner spinner = new Spinner(this);
        List<String> labels = new ArrayList<>(); for (String field : fields) labels.add(hudFieldLabel(field));
        choices(spinner, labels); spinner.setSelection(Math.max(0, fields.indexOf(value)));
        form.addView(spinner, new LinearLayout.LayoutParams(-1, dp(44))); return spinner;
    }
    private CheckBox hudCheck(LinearLayout form, String label, boolean checked) {
        CheckBox box = new CheckBox(this); box.setText(label); box.setTextColor(INK); box.setTextSize(13);
        box.setChecked(checked); form.addView(box); return box;
    }
    private void saveHudLayout() {
        SharedPreferences.Editor edit = preferences.edit().putString("hud_primary", hudLayout.primary).putInt("hud_theme", hudLayout.theme)
                .putFloat("hud_scale", hudLayout.digitScale).putBoolean("hud_mirror", hudLayout.mirrored)
                .putBoolean("hud_landscape", hudLayout.landscape).putBoolean("hud_focus", hudLayout.focused)
                .putBoolean("hud_rpm_bar", hudLayout.rpmBar).putInt("hud_rpm_limit", hudLayout.rpmLimit)
                .putString("hud_boxes_portrait", hudLayout.geometry.encode(false)).putString("hud_boxes_landscape", hudLayout.geometry.encode(true));
        for (int i = 0; i < 3; i++) edit.putString("hud_aux_" + i, hudLayout.auxiliary.get(i)); edit.apply();
    }
    private void editHud() {
        ScrollView scroll = new ScrollView(this); LinearLayout form = column(); form.setPadding(dp(14), dp(8), dp(14), dp(8)); scroll.addView(form);
        form.addView(text("Pilih sensor dan tampilan LCD. Edit tata letak membuka kanvas: geser setiap kotak dan tarik titik sudut untuk mengubah ukuran. Pilihan di form ini diterapkan saat membuka editor. Speed/VSS mengikuti dukungan ECU dan tetap RAW sampai skala km/h terverifikasi.", 12, MUTED, false));
        List<String> fields = hudFields(), optional = new ArrayList<>(fields); optional.add(0, "");
        Spinner primary = hudChoice(form, "Angka utama", fields, hudLayout.primary);
        Spinner[] auxiliary = new Spinner[3];
        for (int i = 0; i < 3; i++) auxiliary[i] = hudChoice(form, "Sensor pendamping " + (i + 1), optional, hudLayout.auxiliary.get(i));
        form.addView(text("Warna LCD", 12, MUTED, false)); Spinner theme = new Spinner(this);
        choices(theme, Arrays.asList("Hijau", "Cyan", "Amber", "Putih")); theme.setSelection(hudLayout.theme); form.addView(theme);
        form.addView(text("Ukuran angka utama", 12, MUTED, false)); Spinner size = new Spinner(this);
        choices(size, Arrays.asList("Kecil", "Normal", "Besar")); size.setSelection(hudLayout.digitScale < .95f ? 0 : hudLayout.digitScale > 1.05f ? 2 : 1); form.addView(size);
        form.addView(text("Skala maksimum bar RPM", 12, MUTED, false)); Spinner rpmLimit = new Spinner(this);
        int[] limits = {10000, 12000, 15000, 18000, 20000}; choices(rpmLimit, Arrays.asList("10.000 RPM", "12.000 RPM", "15.000 RPM", "18.000 RPM", "20.000 RPM"));
        int selectedLimit = 2; for (int i = 0; i < limits.length; i++) if (hudLayout.rpmLimit == limits[i]) selectedLimit = i;
        rpmLimit.setSelection(selectedLimit); form.addView(rpmLimit);
        CheckBox landscape = hudCheck(form, "Landscape saat membuka LCD", hudLayout.landscape);
        CheckBox mirror = hudCheck(form, "Cerminkan display untuk HUD", hudLayout.mirrored);
        CheckBox rpm = hudCheck(form, "Tampilkan bar RPM", hudLayout.rpmBar);
        CheckBox focused = hudCheck(form, "Baca hanya grup sensor HUD", hudLayout.focused);
        form.addView(text("Fokus HUD mengurangi transaksi. Saat kembali ke Live, pilihan pembacaan Live dipulihkan. Min / Avg / Max menghitung sampel valid sepanjang sesi, termasuk nilai 0; putus koneksi mereset statistik.", 11, MUTED, false));
        Runnable applyChoices = () -> {
                    List<String> slots = new ArrayList<>(); for (Spinner spinner : auxiliary) slots.add(optional.get(spinner.getSelectedItemPosition()));
                    hudLayout = new HudLayout(fields.get(primary.getSelectedItemPosition()), slots, theme.getSelectedItemPosition(),
                            new float[]{.85f, 1f, 1.15f}[size.getSelectedItemPosition()], mirror.isChecked(), landscape.isChecked(),
                            focused.isChecked(), rpm.isChecked(), limits[rpmLimit.getSelectedItemPosition()], hudLayout.geometry);
                    saveHudLayout();
                    if (hudMode) setRequestedOrientation(hudLayout.landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
                    timing.reset(); syncSensorPolling(); refreshHud();
                };
        new AlertDialog.Builder(this).setTitle("Atur speedometer / HUD").setView(scroll).setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (dialog, which) -> applyChoices.run())
                .setNeutralButton("Edit tata letak", (dialog, which) -> { applyChoices.run(); beginHudLayoutEdit(); }).show();
    }
    private void refreshHud() {
        if (speedometer == null || hudLayout == null) return;
        DiagnosticController.Snapshot state = controller == null ? null : controller.snapshot();
        boolean connected = state != null && state.connected(), live = state != null && state.state == DiagnosticController.State.LIVE;
        Map<String, Object> values = connected && lastSample != null ? lastSample.values : Collections.emptyMap();
        LcdDashboard.Reading primary = new LcdDashboard.Reading(hudLayout.primary, values.get(hudLayout.primary), history.statistics(hudLayout.primary));
        List<LcdDashboard.Reading> auxiliary = new ArrayList<>();
        for (String name : hudLayout.auxiliary) if (!name.isEmpty()) auxiliary.add(new LcdDashboard.Reading(name, values.get(name), history.statistics(name)));
        long age = lastSample == null ? 0 : Math.max(0, (System.nanoTime() - lastSample.completedNanos) / 1000000L);
        String speed = displayedProfile != null && displayedProfile.fields.contains("vss") ? "VSS TERSEDIA / RAW" : "VSS BELUM TERSEDIA";
        speedometer.update(hudLayout, new LcdDashboard.Data(primary, auxiliary, TelemetryHistory.numeric(values.get("engine_speed")),
                timing.actualMillis(), timing.readingMillis(), connected, live, age, speed));
    }
    private void buildGraph(LinearLayout graphs) {
        graphs.addView(text("Grafik sensor", 20, INK, true));
        graphChoice = new Spinner(this); choices(graphChoice, Collections.singletonList("Sensor muncul setelah deteksi ECU")); graphs.addView(graphChoice, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout chart = panel(); graph = new SensorGraph(this); chart.addView(graph, new LinearLayout.LayoutParams(-1, dp(230))); graphs.addView(chart);
        graphNote = text("Belum ada sampel.", 13, MUTED, false); padded(graphNote); graphs.addView(graphNote);
        graphs.addView(text("Grafik memakai 120 sampel terakhir dan waktu pembacaan aktual. Celah garis berarti sensor tidak terbaca. Jeda dan lanjutkan lewat tab Live.", 12, MUTED, false));
        selected(graphChoice, position -> { if (position < graphFields.size()) { graphField = graphFields.get(position); refreshGraph(); } });
    }
    private void buildDtcs(LinearLayout dtcs) {
        readDtc = button(getString(R.string.read_dtc), true); dtcs.addView(readDtc, new LinearLayout.LayoutParams(-1, dp(48)));
        dtcNote = text(getString(R.string.dtc_unread), 12, MUTED, false); padded(dtcNote); dtcs.addView(dtcNote);
        LinearLayout current = panel(); current.addView(text(getString(R.string.dtc_current), 17, INK, true));
        dtcCurrent = text(getString(R.string.dtc_unread), 14, MUTED, false); dtcCurrent.setTextIsSelectable(true); padded(dtcCurrent); current.addView(dtcCurrent); dtcs.addView(current);
        LinearLayout stored = panel(); stored.addView(text(getString(R.string.dtc_stored), 17, INK, true));
        dtcStored = text(getString(R.string.dtc_unread), 14, MUTED, false); dtcStored.setTextIsSelectable(true); padded(dtcStored); stored.addView(dtcStored); spaced(dtcs, stored);
        readDtc.setOnClickListener(view -> {
            if (controller.readDtcs()) { dtcCurrent.setText("Membaca…"); dtcStored.setText("Membaca…"); dtcNote.setText("Live dijeda selama pembacaan DTC."); }
        });
    }
    private void buildDebug(LinearLayout debug) {
        debug.addView(text("Debug koneksi", 20, INK, true));
        debug.addView(text("Log direkam otomatis sejak aplikasi dibuka. Salin mengambil bagian akhir beserta header versi dan error; Simpan .txt menyimpan log lengkap.", 12, MUTED, false));
        failureNote = text("Belum ada error yang direkam.", 12, RED, false); failureNote.setTextIsSelectable(true); padded(failureNote); debug.addView(failureNote);
        LinearLayout sources = row(); traceNow = button("Log sekarang", true); traceLast = button("Error terakhir", false);
        sources.addView(traceNow, weight()); sources.addView(traceLast, weight()); debug.addView(sources);
        LinearLayout actions = row(); copyLog = button("Salin akhir log", true); saveLog = button("Simpan .txt", false);
        actions.addView(copyLog, weight()); actions.addView(saveLog, weight()); debug.addView(actions);
        Switch detailed = new Switch(this); detailed.setText("Detail TX/RX USB & K-Line"); detailed.setTextColor(INK); detailed.setTextSize(13); detailed.setChecked(log.isDetailed()); debug.addView(detailed, new LinearLayout.LayoutParams(-1, dp(44)));
        detailed.setOnCheckedChangeListener((view, checked) -> { log.setDetailed(checked); preferences.edit().putBoolean("detailed_log", checked).apply(); log.info("LOG", "Detail TX/RX=" + checked); });
        LinearLayout settings = panel(); settings.addView(text("Pengaturan koneksi · ubah saat terputus", 12, MUTED, false));
        echoChoice = new Spinner(this); choices(echoChoice, Arrays.asList("Echo: otomatis (default)", "Echo: wajib", "Echo: tanpa echo")); echoChoice.setSelection(boundedIndex(preferences.getInt("echo_index", 0), 3)); settings.addView(echoChoice, new LinearLayout.LayoutParams(-1, dp(44)));
        timeoutChoice = new Spinner(this); choices(timeoutChoice, Arrays.asList("Timeout: 500 ms", "Timeout: 1.000 ms (default)", "Timeout: 2.000 ms")); timeoutChoice.setSelection(boundedIndex(preferences.getInt("timeout_index", 1), TIMEOUTS.length)); settings.addView(timeoutChoice, new LinearLayout.LayoutParams(-1, dp(44))); spaced(debug, settings);
        selected(echoChoice, position -> preferences.edit().putInt("echo_index", position).apply());
        selected(timeoutChoice, position -> preferences.edit().putInt("timeout_index", position).apply());
        debugInfo = text("", 11, MUTED, false); debugInfo.setTextIsSelectable(true); padded(debugInfo); debug.addView(debugInfo);
        Button clear = button("Bersihkan log sekarang", false); debug.addView(clear, new LinearLayout.LayoutParams(-1, dp(44)));
        debugSummary = text("", 11, GREEN, false); padded(debugSummary); debug.addView(debugSummary);
        debugText = text("", 11, INK, false); debugText.setTypeface(Typeface.MONOSPACE); debugText.setTextIsSelectable(true); debug.addView(debugText);
        traceNow.setOnClickListener(view -> { archivedTrace = false; renderDebug(); });
        traceLast.setOnClickListener(view -> { archivedTrace = true; renderDebug(); });
        copyLog.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) { clipboard.setPrimaryClip(ClipData.newPlainText("Honda Tuner debug", DiagnosticLog.compactTrace(exportTrace(), 120, 32 * 1024))); toast("Bagian akhir log disalin."); }
        });
        saveLog.setOnClickListener(view -> exportText(exportTrace(), "text/plain", "HondaTuner-debug-" + filenameTime() + ".txt"));
        clear.setOnClickListener(view -> { log.clear(); log.info("APP", appContext); log.info("UI", "Log sesi dibersihkan. Arsip error terakhir tetap tersedia."); archivedTrace = false; renderDebug(); });
    }

    private void onConnect() {
        if (pendingDevice != null) {
            permissionSerial++; pendingDevice = null; permissionResultReady = false;
            log.info("USB PERMISSION", "Dibatalkan pengguna"); connectionNote.setText("Permintaan USB dibatalkan."); showControls(); return;
        }
        if (controller.snapshot().state != DiagnosticController.State.DISCONNECTED) {
            activeDevice = null; controller.disconnect("Koneksi diputus pengguna."); refreshAdapters(); return;
        }
        if (usb == null || devices.isEmpty()) { connectionNote.setText(getString(R.string.usb_help)); log.warn("USB", "Tidak ada adaptor yang didukung."); return; }
        int index = adapter.getSelectedItemPosition(); if (index < 0 || index >= devices.size()) return;
        UsbDevice device = devices.get(index);
        if (usb.hasPermission(device)) { connectDevice(device); return; }
        pendingDevice = device; permissionResultReady = false; long request = ++permissionSerial;
        Intent intent = new Intent(USB_PERMISSION).setPackage(getPackageName()).putExtra("request_token", request);
        PendingIntent pending = PendingIntent.getBroadcast(this, (int) (request & 0x7fffffff), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        log.info("USB PERMISSION", "Meminta akses: " + deviceLabel(device) + ", token=" + request);
        connectionNote.setText("Izinkan akses adaptor USB pada dialog Android."); showControls();
        try { usb.requestPermission(device, pending); }
        catch (RuntimeException error) {
            pendingDevice = null; permissionResultReady = false; log.error("USB PERMISSION", error);
            connectionNote.setText("Permintaan USB gagal. " + firstLine(error.toString())); showControls();
        }
    }
    private void finishPermission() {
        if (!foreground || destroyed || pendingDevice == null || usb == null) return;
        boolean granted = usb.hasPermission(pendingDevice);
        if (!granted && !permissionResultReady) { connectionNote.setText("Menunggu jawaban dialog izin USB. Tekan Batal untuk membatalkan."); showControls(); return; }
        UsbDevice device = pendingDevice; pendingDevice = null; permissionResultReady = false;
        if (granted) connectDevice(device);
        else { log.error("USB PERMISSION", "Izin USB ditolak. Tekan Hubungkan dan izinkan akses adaptor."); connectionNote.setText("Izin USB belum diberikan. Tekan Hubungkan untuk mencoba lagi."); showControls(); }
    }
    private void connectDevice(UsbDevice device) {
        if (!foreground || destroyed) return;
        activeDevice = device;
        // Capture settings now. Neither worker factory ever reads an Android widget.
        final KLineTransport.EchoMode echo = KLineTransport.EchoMode.values()[boundedIndex(echoChoice.getSelectedItemPosition(), 3)];
        final int timeout = TIMEOUTS[boundedIndex(timeoutChoice.getSelectedItemPosition(), TIMEOUTS.length)];
        String deviceContext = deviceLabel(device) + "\n" + device.getDeviceName() + ", interfaces=" + device.getInterfaceCount() + ", permission=" + usb.hasPermission(device);
        traceContext = appContext + "\n" + deviceContext + "\nK-Line 10400 baud, 8N1, flow=none\nEcho=" + echo + ", timeout=" + timeout + " ms";
        log.info("UI", "Hubungkan\n" + traceContext);
        resetSessionViews();
        if (getResources().getConfiguration().screenHeightDp < 500) { connectionExpanded = false; connectionPanel.setVisibility(View.GONE); }
        controller.connect(cancel -> FtdiChannel.open(usb, device, cancel, log), channel -> new KLineTransport(channel, System::nanoTime, timeout, echo, log));
    }
    private void refreshAdapters() {
        if (adapter == null) return;
        int selectedId = adapter.getSelectedItemPosition() >= 0 && adapter.getSelectedItemPosition() < devices.size() ? devices.get(adapter.getSelectedItemPosition()).getDeviceId() : -1;
        devices.clear(); StringBuilder inventory = new StringBuilder();
        if (usb != null) for (UsbDevice device : usb.getDeviceList().values()) {
            if (inventory.length() != 0) inventory.append("\n"); inventory.append(deviceLabel(device)).append(FtdiChannel.matches(device) ? " · kandidat FT232R" : " · driver belum didukung");
            if (FtdiChannel.matches(device)) devices.add(device);
        }
        String current = inventory.length() == 0 ? "Tidak ada perangkat USB terdeteksi." : inventory.toString();
        if (!current.equals(usbInventory)) { usbInventory = current; log.info("USB SCAN", current); }
        Collections.sort(devices, Comparator.comparingInt(UsbDevice::getDeviceId));
        List<String> labels = new ArrayList<>(); for (UsbDevice device : devices) labels.add("FTDI 0403:6001 · USB #" + device.getDeviceId());
        if (labels.isEmpty()) labels.add(getString(R.string.adapter_missing)); choices(adapter, labels);
        for (int i = 0; i < devices.size(); i++) if (devices.get(i).getDeviceId() == selectedId) adapter.setSelection(i);
        logDirty.set(true);
        if (controller != null) {
            if (pendingDevice == null) showState(controller.snapshot()); else showControls();
        }
    }
    private void showState(DiagnosticController.Snapshot snapshot) {
        stateContext = sessionContext(snapshot);
        if (displayedSession != snapshot.session) {
            displayedSession = snapshot.session;
            if (snapshot.state == DiagnosticController.State.CONNECTING) resetSessionViews();
        }
        if (snapshot.state == DiagnosticController.State.DISCONNECTED) {
            history.clearStatistics(); timing.reset();
            for (SensorCard card : cards.values()) card.refreshStatistics();
        }
        String state;
        switch (snapshot.state) {
            case CONNECTING: state = "SCAN ECU · MENGHUBUNGKAN"; break;
            case DISCOVERING: state = "ECU TERHUBUNG · DETEKSI SENSOR"; break;
            case LIVE: state = "ECU TERHUBUNG · LIVE"; break;
            case READING_DTC: state = "ECU TERHUBUNG · BACA DTC"; break;
            case READING_SAMPLE: state = "ECU TERHUBUNG · BACA SEKALI"; break;
            case PAUSED: state = "ECU TERHUBUNG · LIVE DIJEDA"; break;
            default: state = devices.isEmpty() ? "USB BELUM TERDETEKSI" : "USB TERDETEKSI · ECU BELUM TERHUBUNG";
        }
        status.setText(state); status.setTextColor(snapshot.connected() ? GREEN : MUTED);
        connectionToggle.setText(state + " · Detail");
        connectionProgress.setVisibility(snapshot.busy() ? View.VISIBLE : View.GONE);
        identity.setText(snapshot.connected() ? identityLabel(snapshot.ecuId) : ""); identity.setVisibility(snapshot.connected() ? View.VISIBLE : View.GONE);
        connectionNote.setText(snapshot.message);
        for (SensorCard card : cards.values()) card.setLive(snapshot.state == DiagnosticController.State.LIVE);
        refreshLiveNote(); refreshHud(); logDirty.set(true); showControls();
    }
    private void resetSessionViews() {
        displayedProfile = null; lastSample = null; tableContext = "Sensor belum dideteksi.";
        cards.clear(); grid.removeAllViews(); history.reset(Collections.emptyList()); timing.reset(); graphFields.clear(); graphField = "";
        choices(graphChoice, Collections.singletonList("Sensor muncul setelah deteksi ECU")); graph.setSeries("", Collections.emptyList());
        graphNote.setText("Belum ada sampel."); filterNote.setText("Belum ada sensor."); tableNote.setText(tableContext);
        dtcCurrent.setText(getString(R.string.dtc_unread)); dtcStored.setText(getString(R.string.dtc_unread)); dtcNote.setText(getString(R.string.dtc_unread));
        refreshHud();
    }
    private void showControls() {
        if (controller == null) return;
        DiagnosticController.Snapshot state = controller.snapshot(); boolean disconnected = state.state == DiagnosticController.State.DISCONNECTED;
        connect.setText(pendingDevice != null ? R.string.cancel : disconnected ? R.string.connect : R.string.disconnect);
        connect.setEnabled(pendingDevice != null || !disconnected || !devices.isEmpty());
        adapter.setEnabled(disconnected && pendingDevice == null && !devices.isEmpty()); refresh.setEnabled(disconnected && pendingDevice == null);
        echoChoice.setEnabled(disconnected && pendingDevice == null); timeoutChoice.setEnabled(disconnected && pendingDevice == null);
        toggleLive.setText(state.state == DiagnosticController.State.LIVE ? R.string.pause_live : R.string.start_live);
        toggleLive.setEnabled(state.connected() && !state.busy() && state.hasSensors);
        readOnce.setEnabled(state.state == DiagnosticController.State.PAUSED && state.hasSensors);
        detect.setEnabled(state.connected() && !state.busy()); readDtc.setEnabled(state.connected() && !state.busy()); interval.setEnabled(!state.busy());
        saveCsv.setEnabled(history.size() > 0 && !exportInFlight); resetStats.setEnabled(history.size() > 0); saveLog.setEnabled(!exportInFlight);
        graphChoice.setEnabled(!graphFields.isEmpty()); traceLast.setEnabled(lastFailure.get() != null);
        chooseSensors.setEnabled(!cards.isEmpty()); focusSensors.setEnabled(!cards.isEmpty());
        hudPause.setText(state.state == DiagnosticController.State.LIVE ? R.string.pause_live : R.string.start_live);
        hudPause.setEnabled(state.connected() && !state.busy() && state.hasSensors); hudPause.setAlpha(hudPause.isEnabled() ? 1f : .45f);
        for (Button button : new Button[]{connect, refresh, toggleLive, readOnce, detect, readDtc, saveCsv, resetStats, saveLog, traceLast}) button.setAlpha(button.isEnabled() ? 1f : 0.45f);
    }
    private void showTab(int tab) {
        if (hudMode) setHudMode(false);
        selectedTab = boundedIndex(tab, pages.length);
        for (int i = 0; i < pages.length; i++) { pages[i].setVisibility(i == selectedTab ? View.VISIBLE : View.GONE); tint(tabs[i], i == selectedTab); }
        if (selectedTab == 3 && debugText != null) renderDebug();
        if (selectedTab == 0 && lastSample != null) refreshVisibleReadings();
        if (selectedTab == 1 && graph != null) refreshGraph();
    }
    private void buildCards(List<String> fields) {
        cards.clear(); grid.removeAllViews(); history.includeFields(fields); lastSample = null; timing.reset();
        if (!speedometer.isEditing()) hudLayout = readHudLayout(fields);
        List<String> ordered = sensorSelection.order(fields);
        for (String name : ordered) cards.put(name, new SensorCard(name));
        graphFields.clear(); List<String> labels = new ArrayList<>();
        for (String name : ordered) if (TelemetryHistory.numeric(displayedProfile.initial.values.get(name)) != null) { graphFields.add(name); labels.add(SensorInfo.label(name) + (SensorInfo.unit(name).isEmpty() ? " · raw" : " · " + SensorInfo.unit(name))); }
        if (labels.isEmpty()) labels.add("Tidak ada sensor numerik pada tabel ini");
        graphField = graphFields.isEmpty() ? "" : graphFields.get(0); choices(graphChoice, labels); applyFilter();
        syncSensorPolling();
    }
    private void applyFilter() {
        if (grid == null || search == null || category == null) return;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int group = Math.max(0, category.getSelectedItemPosition()); grid.removeAllViews(); int visible = 0;
        for (String name : sensorSelection.visible(cards.keySet())) {
            String searchable = name + " " + SensorInfo.label(name) + " " + SensorInfo.unit(name);
            if (group > 0 && !GROUPS[group].equals(SensorInfo.group(name)) || !searchable.toLowerCase(Locale.ROOT).contains(query)) continue;
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = 0; params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); params.setMargins(dp(3), dp(3), dp(3), dp(3)); grid.addView(cards.get(name), params); visible++;
        }
        filterNote.setText(cards.isEmpty() ? "Belum ada sensor." : visible + " / " + cards.size() + " sensor ditampilkan");
        if (!hudMode && selectedTab == 0) refreshVisibleReadings();
    }
    private void renderSample(LiveProfile.Sample sample) {
        lastSample = sample; history.add(sample);
        if (!hudMode && selectedTab == 0) refreshVisibleReadings();
        if (!hudMode && selectedTab == 1) refreshGraph();
        if (hudMode) refreshHud();
        refreshLiveNote(); showControls();
    }
    private void refreshVisibleReadings() {
        if (lastSample == null || controller == null) return;
        boolean live = controller.snapshot().state == DiagnosticController.State.LIVE;
        for (Map.Entry<String, SensorCard> card : cards.entrySet()) if (card.getValue().getParent() == grid)
            card.getValue().setReading(lastSample.values.get(card.getKey()), live);
    }
    private void refreshLiveNote() {
        if (liveNote == null) return;
        if (lastSample == null) { liveNote.setText(getString(R.string.sensor_empty)); return; }
        int valid = 0; for (Object value : lastSample.values.values()) if (value != null && (!(value instanceof Number) || TelemetryHistory.numeric(value) != null)) valid++;
        double age = Math.max(0, (System.nanoTime() - lastSample.completedNanos) / 1000000000.0);
        String mode = controller != null && controller.snapshot().state == DiagnosticController.State.LIVE ? "Live" : "Sampel terakhir";
        String refresh = timing.actualMillis() == null ? "Refresh aktual — ms" : String.format(Locale.getDefault(),
                "Refresh aktual %.1f ms (%.2f Hz) · terakhir %.1f ms", timing.actualMillis(), timing.updatesPerSecond(), timing.latestMillis());
        String duration = timing.readingMillis() == null ? "—" : String.format(Locale.getDefault(), "%.1f", timing.readingMillis());
        liveNote.setText(String.format(Locale.getDefault(), "%s · %s · usia %.1f dtk\n%s\nPutaran %s ms · target %d ms\n%d/%d sensor · %d grup terbaca\nRiwayat %d/600 sampel · total %d", mode, timeLabel(lastSample.completedMillis), age,
                refresh, duration, controller == null ? 0 : controller.liveIntervalMillis(), valid, cards.size(), lastSample.validGroups, history.size(), history.totalSamples()) +
                (lastSample.failures.isEmpty() ? "" : "\nAda grup gagal dibaca; buka Debug untuk detail."));
    }
    private void refreshGraph() {
        if (graph == null) return;
        graph.setSeries(graphField, history.points(graphField, 120));
        if (graphField.isEmpty()) { graphNote.setText("Belum ada sensor numerik."); return; }
        TelemetryHistory.Statistics stats = history.statistics(graphField);
        Object current = lastSample == null ? null : lastSample.values.get(graphField); String unit = SensorInfo.unit(graphField);
        graphNote.setText(SensorInfo.label(graphField) + ": " + SensorInfo.format(current) + (unit.isEmpty() ? " (raw)" : " " + unit) + "\n" + SensorInfo.statisticsLabel(stats));
    }
    private void renderDebug() {
        Failure failure = lastFailure.get();
        if (archivedTrace && failure == null) archivedTrace = false;
        tint(traceNow, !archivedTrace); tint(traceLast, archivedTrace);
        traceLast.setEnabled(failure != null); traceLast.setAlpha(failure == null ? .45f : 1f);
        failureNote.setText(failure == null ? "Belum ada error yang direkam." : failure.reason);
        debugInfo.setText(appContext + "\n\nUSB terdeteksi:\n" + usbInventory + "\n\n" + stateContext + "\nTabel: " + tableContext);
        debugSummary.setText(archivedTrace ? "Arsip error terakhir · tetap tersedia setelah aplikasi dibuka ulang." : log.size() + " entri · menampilkan 240 entri terbaru · " + log.droppedEntries() + " entri lama diganti. Salin mengambil bagian akhir; simpan .txt untuk log lengkap.");
        debugText.setText(archivedTrace ? failure.trace : captureTrace(240));
    }
    private String captureTrace(int maximumLines) { return traceContext + "\n" + stateContext + "\n\n" + log.text(maximumLines); }
    private String exportTrace() { Failure failure = lastFailure.get(); return archivedTrace && failure != null ? failure.trace : captureTrace(0); }

    private void exportText(String content, String mime, String name) {
        if (exportInFlight) return;
        exportInFlight = true; pendingExportName = name;
        final String snapshotFile = "export-" + UUID.randomUUID() + ".txt"; pendingExportFile = snapshotFile;
        showControls();
        toast("Pilih lokasi simpan. Koneksi ditutup saat meninggalkan aplikasi.");
        // Snapshot before the picker. Lifecycle/disconnect cannot empty the export.
        files.execute(() -> {
            try {
                Files.write(new File(getFilesDir(), snapshotFile).toPath(), content.getBytes(StandardCharsets.UTF_8));
                if (destroyed) { Files.deleteIfExists(new File(getFilesDir(), snapshotFile).toPath()); return; }
                main.post(() -> {
                    if (destroyed) return;
                    if (!foreground) { exportInFlight = false; deleteExport(snapshotFile); showControls(); return; }
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, name);
                    try { awaitingExport = true; startActivityForResult(intent, EXPORT_REQUEST); }
                    catch (RuntimeException error) { awaitingExport = false; exportInFlight = false; deleteExport(snapshotFile); log.error("EXPORT", error); toast("Pemilih lokasi gagal dibuka. Coba Salin log."); showControls(); }
                });
            } catch (IOException error) {
                log.error("EXPORT", error); main.post(() -> { if (!destroyed) { exportInFlight = false; toast("Ekspor gagal: " + error.getMessage()); showControls(); } });
            }
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_REQUEST) return;
        awaitingExport = false;
        if (result != RESULT_OK || data == null || data.getData() == null) { deleteExport(pendingExportFile); exportInFlight = false; showControls(); return; }
        final Uri destination = data.getData(); final String name = pendingExportName;
        final String snapshotFile = pendingExportFile;
        files.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                if (output == null) throw new IOException("Lokasi simpan tidak bisa dibuka.");
                if (snapshotFile.isEmpty()) throw new IOException("Snapshot ekspor tidak tersedia. Ulangi simpan log.");
                Files.copy(new File(getFilesDir(), snapshotFile).toPath(), output);
                log.info("EXPORT", "Disimpan: " + name);
                main.post(() -> { if (!destroyed) toast("Disimpan: " + name); });
            } catch (IOException | RuntimeException error) {
                log.error("EXPORT", error); main.post(() -> { if (!destroyed) toast("Simpan gagal: " + firstLine(error.toString())); });
            } finally {
                if (!snapshotFile.isEmpty()) try { Files.deleteIfExists(new File(getFilesDir(), snapshotFile).toPath()); } catch (IOException ignored) { }
                main.post(() -> { if (!destroyed) { exportInFlight = false; showControls(); } });
            }
        });
    }
    private void deleteExport(String name) {
        if (name.isEmpty()) return;
        files.execute(() -> { try { Files.deleteIfExists(new File(getFilesDir(), name).toPath()); } catch (IOException error) { log.warn("EXPORT", "Snapshot sementara belum dihapus: " + error.getMessage()); } });
    }

    private String dtcText(List<HondaClient.DtcCode> codes) {
        if (codes.isEmpty()) return getString(R.string.dtc_empty);
        StringBuilder result = new StringBuilder();
        for (HondaClient.DtcCode code : codes) { if (result.length() != 0) result.append("\n\n"); result.append(code.code()).append("\n").append(code.description()); } return result.toString();
    }
    private void loadCatalog() {
        catalog.clear(); catalogRows = 0;
        try (BufferedReader source = new BufferedReader(new InputStreamReader(getAssets().open("ecu_catalog.tsv"), StandardCharsets.UTF_8))) {
            for (String line; (line = source.readLine()) != null;) {
                String[] parts = line.split("\t", -1); if (parts.length != 3) continue;
                List<String> rows = catalog.get(parts[0]); if (rows == null) { rows = new ArrayList<>(); catalog.put(parts[0], rows); }
                rows.add(parts[1] + (parts[2].isEmpty() ? "" : " · " + parts[2]));
                catalogRows++;
            }
            catalogStatus = catalogRows + " definisi, " + catalog.size() + " ID ECU unik, dimuat dari aplikasi.";
            log.info("CATALOG", catalogStatus);
        } catch (IOException error) {
            catalog.clear(); catalogRows = 0;
            catalogStatus = "Katalog nama gagal dimuat: " + error.getMessage();
            log.warn("CATALOG", catalogStatus);
        }
    }
    private String sessionContext(DiagnosticController.Snapshot snapshot) {
        return "State=" + snapshot.state + ", session=" + snapshot.session + ", ECU=" + snapshot.ecuId +
                "\nIdentitas: " + snapshot.identityStatus + "\nModel: " + (snapshot.ecuId.isEmpty() ? "Menunggu ID ECU." : identityLabel(snapshot.ecuId)) +
                "\nKatalog: " + catalogStatus + "\n" + snapshot.message;
    }
    private String identityLabel(String id) {
        if (id.isEmpty()) return "ECU merespons · ID belum terbaca. Detail di Debug.";
        if (catalogRows == 0) return "ECU " + id + " · katalog nama belum tersedia";
        List<String> rows = catalog.get(id);
        if (rows == null) return "ECU " + id + " · model belum ada di katalog";
        if (rows.size() == 1) return rows.get(0) + "\nECU " + id;
        return "ECU " + id + " · " + rows.size() + " definisi cocok dalam katalog";
    }
    private String versionLabel() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { return "debug"; }
    }
    private static String deviceLabel(UsbDevice device) { return String.format(Locale.ROOT, "USB #%d · VID:%04X PID:%04X", device.getDeviceId(), device.getVendorId(), device.getProductId()); }
    private static String firstLine(String text) { if (text == null) return "Tidak ada detail."; int end = text.indexOf('\n'); return end < 0 ? text : text.substring(0, end); }
    private static String timeLabel(long millis) { return new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date(millis)); }
    private static String filenameTime() { return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()); }
    private static int boundedIndex(int index, int size) { return Math.min(Math.max(0, index), size - 1); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private LinearLayout panel() { LinearLayout view = column(); view.setPadding(dp(10), dp(10), dp(10), dp(10)); view.setBackground(background(SURFACE)); return view; }
    private void spaced(LinearLayout parent, View child) { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(8); parent.addView(child, params); }
    private void padded(TextView view) { view.setPadding(0, dp(8), 0, dp(8)); }
    private GradientDrawable background(int color) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(12)); return shape; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, dp(44), 1); }
    private TextView text(String label, float size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(label); view.setTextSize(size); view.setTextColor(color); if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }
    private Button button(String label, boolean primary) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false); view.setTextSize(12); view.setMinWidth(0); view.setMinHeight(0); view.setPadding(dp(4), 0, dp(4), 0); tint(view, primary); return view;
    }
    private void tint(Button view, boolean primary) { view.setBackgroundTintList(ColorStateList.valueOf(primary ? GREEN : SURFACE)); view.setTextColor(primary ? BG : INK); }
    private void choices(Spinner spinner, List<String> labels) {
        ArrayAdapter<String> choices = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels); choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(choices);
    }
    private interface Selection { void changed(int position); }
    private void selected(Spinner spinner, Selection callback) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { callback.changed(position); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }
    private final class SensorCard extends LinearLayout {
        private final TextView reading, state, stats;
        private final String sensor;
        private boolean valid;
        SensorCard(String sensor) {
            super(MainActivity.this); this.sensor = sensor; setOrientation(VERTICAL); setPadding(dp(10), dp(10), dp(10), dp(10)); setMinimumHeight(dp(165)); setBackground(background(SURFACE));
            TextView label = text(SensorInfo.label(sensor), 12, MUTED, false); label.setMinLines(2); addView(label);
            reading = text("—", sensor.equals("engine_speed") ? 25 : 22, INK, true); addView(reading);
            state = text("Belum terbaca", 11, MUTED, false); state.setPadding(0, dp(4), 0, dp(6)); addView(state);
            stats = text("", 10, MUTED, false); addView(stats);
            TextView group = text(SensorInfo.group(sensor), 10, GREEN, false); group.setPadding(0, dp(6), 0, 0); addView(group);
        }
        void setReading(Object value, boolean live) {
            valid = value != null && (!(value instanceof Number) || TelemetryHistory.numeric(value) != null);
            String label = SensorInfo.format(value); String unit = SensorInfo.unit(sensor);
            if (valid && !unit.isEmpty()) label += " " + unit;
            reading.setText(label); reading.setTextColor(valid ? INK : MUTED); setLive(live); refreshStatistics();
            setContentDescription(SensorInfo.label(sensor) + ", " + label + ", " + state.getText() + ", " + stats.getText());
        }
        void setLive(boolean live) { state.setText(!valid ? "Belum terbaca" : live ? "Live" : "Sampel terakhir"); state.setTextColor(valid && live ? GREEN : MUTED); }
        void refreshStatistics() {
            TelemetryHistory.Statistics values = history.statistics(sensor);
            stats.setText(SensorInfo.statisticsLabel(values));
        }
    }
}
