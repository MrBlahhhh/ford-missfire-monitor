package com.geekopolis.fordmonitor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.geekopolis.fordmonitor.core.FordData;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int TEAL = Color.rgb(109,227,203), WHITE = Color.rgb(237,242,247), MUTED = Color.rgb(165,181,195);
    private static final int BG = Color.rgb(16,24,32), CARD = Color.rgb(27,39,51);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, TextView> liveViews = new HashMap<>();
    private final Map<Integer, Reading> live = new HashMap<>();
    private final Map<Integer, Cylinder> cylinders = new HashMap<>();
    private final Map<Integer, TextView> cylinderViews = new HashMap<>();
    private Spinner profileSpinner, deviceSpinner;
    private TextView status, ready, codes, headline;
    private Button connect;
    private final List<BluetoothDevice> devices = new ArrayList<>();
    private ObdSession session, lastSession;
    private long epoch;
    private boolean connected, active;
    private File csv, raw, exportFile;
    private LinearLayout root, cylinderBox;
    private final java.util.concurrent.ExecutorService exportWorker = Executors.newSingleThreadExecutor();
    private record Reading(Double value, long elapsed) {}
    private record Cylinder(List<FordData.Misfire> values, String note, long elapsed) {}
    private final Runnable refresh = new Runnable() {
        public void run() { render(); if (active) handler.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(12), dp(20), dp(20));
        scroll.addView(root); setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 30) scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        // API 26–29 also receives usable legacy system bar insets.
        if (Build.VERSION.SDK_INT < 30) scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets;
        });
        add(text("FORD / MISFIRE MONITOR", 13, TEAL));
        headline = text("Your engine, cylinder by cylinder", 27, WHITE); headline.setTypeface(null, Typeface.BOLD); add(headline);
        add(text("Personal edition · build 1", 12, MUTED));
        profileSpinner = new Spinner(this);
        profileSpinner.setAdapter(adapter(new String[]{FordData.F150.name(), FordData.V10.name()})); add(profileSpinner);
        int savedProfile = getPreferences(MODE_PRIVATE).getInt("profile", 0); profileSpinner.setSelection(savedProfile == 1 ? 1 : 0);
        profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                getPreferences(MODE_PRIVATE).edit().putInt("profile", pos).apply();
                disconnect(); live.clear(); cylinders.clear(); createCylinders(); render();
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        deviceSpinner = new Spinner(this); add(deviceSpinner);
        LinearLayout deviceButtons = row();
        button(deviceButtons, "Refresh devices", v -> loadDevices());
        button(deviceButtons, "Pair adapter", v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))); add(deviceButtons);
        connect = button(root, "Connect", v -> { if (connected) disconnect(); else connect(); });
        status = text("Pair your OBDLink LX or ELM327 in Android Bluetooth settings.", 14, MUTED); add(status);
        ready = text("Misfire readiness —", 13, MUTED); add(ready);
        add(text("MISFIRE RESULTS", 13, TEAL));
        add(text("Current/last driving-cycle counts and 10-cycle weighted averages, when reported. ECU results may update slowly. Zero does not prove the monitor is active.", 13, MUTED));
        cylinderBox = new LinearLayout(this); cylinderBox.setOrientation(LinearLayout.VERTICAL); add(cylinderBox); createCylinders();
        button(root, "Refresh all cylinders", v -> { if (session != null && connected) session.requestScan(); else toast("Connect first"); });
        add(text("LIVE ENGINE DATA", 13, TEAL));
        for (int[] pair : new int[][]{{0x0C,0x05},{0x06,0x07},{0x08,0x09},{0x04,0x0D},{0x0F,0x11}}) {
            LinearLayout r = row();
            for (int pid : pair) { TextView view = text("—", 17, WHITE); panel(view); liveViews.put(pid,view); r.addView(view, new LinearLayout.LayoutParams(0,-2,1)); }
            add(r);
        }
        add(text("ENGINE FAULT CODES", 13, TEAL));
        codes = text("Read stored, pending and permanent engine codes.", 16, WHITE); panel(codes); add(codes);
        button(root, "Read codes", v -> { if (session != null && connected) { codes.setText("Reading engine codes…"); session.requestCodes(); } else toast("Connect first"); });
        LinearLayout exports = row();
        button(exports,"Export CSV",v -> export(false)); button(exports,"Export debug log",v -> export(true)); add(exports);
        add(text("Logs stay on this device. Last 20 sessions retained. Each file is capped at 8 MiB; monitoring continues if logging fills. Monitoring pauses when you leave the app. ECU writes and code clearing are disabled.",12,MUTED));
        loadDevices(); render();
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration); createCylinders(); render();
    }
    @Override protected void onStart() { super.onStart(); active = true; handler.post(refresh); }
    @Override protected void onStop() { active = false; handler.removeCallbacks(refresh); disconnect(); super.onStop(); }
    @Override protected void onDestroy() { epoch++; exportWorker.shutdown(); super.onDestroy(); }
    private FordData.Profile profile() { return profileSpinner.getSelectedItemPosition() == 1 ? FordData.V10 : FordData.F150; }
    private boolean permission() { return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED; }
    @SuppressLint("MissingPermission")
    private void loadDevices() {
        if (!permission()) { requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 10); return; }
        devices.clear(); List<String> names = new ArrayList<>();
        try {
            BluetoothAdapter a = getSystemService(BluetoothManager.class).getAdapter();
            if (a != null && a.isEnabled()) {
                List<BluetoothDevice> paired = new ArrayList<>(a.getBondedDevices());
                paired.sort(Comparator.comparing(d -> d.getName() == null ? d.getAddress() : d.getName()));
                devices.addAll(paired);
                for (BluetoothDevice d : devices) names.add((d.getName() == null ? "Bluetooth adapter" : d.getName()) + " · " + d.getAddress());
            }
        } catch (SecurityException e) { status.setText("Bluetooth permission was revoked. Grant Nearby devices permission."); }
        if (names.isEmpty()) names.add("No paired devices — pair adapter first");
        deviceSpinner.setAdapter(adapter(names.toArray(new String[0])));
        String saved = getPreferences(MODE_PRIVATE).getString("device", "");
        for (int i = 0; i < devices.size(); i++) if (devices.get(i).getAddress().equals(saved)) deviceSpinner.setSelection(i);
    }
    @Override public void onRequestPermissionsResult(int req, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(req,permissions,results);
        if (req == 10 && permission()) loadDevices(); else status.setText("Nearby devices permission is needed to connect to your paired adapter.");
    }
    @SuppressLint("MissingPermission")
    private void connect() {
        if (!permission()) { loadDevices(); return; }
        int index = deviceSpinner.getSelectedItemPosition();
        if (index < 0 || index >= devices.size()) { toast("Pair an adapter first, then refresh devices"); return; }
        disconnect(); long token = ++epoch;
        live.clear(); cylinders.clear(); ready.setText("Reading misfire readiness…"); codes.setText("Codes not read in this session");
        BluetoothDevice device = devices.get(index);
        getPreferences(MODE_PRIVATE).edit().putString("device",device.getAddress()).apply();
        connected = true; controls(); render();
        session = new ObdSession(device,profile(),new File(getFilesDir(),"sessions"),new ObdSession.Listener() {
            private void post(Runnable r) { handler.post(() -> { if (token == epoch && !isDestroyed()) r.run(); }); }
            public void status(String text, boolean running) { post(() -> {
                status.setText(text); connected = running; controls();
                if (session != null) { csv = session.csvFile; raw = session.rawFile; }
                if (!running) { live.clear(); cylinders.clear(); ready.setText("Disconnected · readiness is not current"); render(); }
            }); }
            public void live(int pid, Double value) { post(() -> { live.put(pid,new Reading(value,SystemClock.elapsedRealtime())); render(); }); }
            public void cylinder(int c, List<FordData.Misfire> values, String note) { post(() -> {
                cylinders.put(c,new Cylinder(Collections.unmodifiableList(new ArrayList<>(values)),note,SystemClock.elapsedRealtime())); render();
            }); }
            public void readiness(String text) { post(() -> ready.setText(text)); }
            public void codes(String text) { post(() -> codes.setText(text)); }
        });
        session.start();
    }
    private void disconnect() {
        epoch++;
        if (session != null) { lastSession = session; session.close(); if (session.csvFile != null) csv = session.csvFile; if (session.rawFile != null) raw = session.rawFile; session = null; }
        connected = false;
        if (status != null) status.setText("Disconnected · connect to read current data");
        live.clear(); cylinders.clear();
        if (ready != null) ready.setText("Disconnected · readiness is not current");
        if (connect != null) controls();
        render();
    }
    private void controls() {
        connect.setText(connected ? "Disconnect" : "Connect"); profileSpinner.setEnabled(!connected); deviceSpinner.setEnabled(!connected);
        if (connected) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void createCylinders() {
        if (cylinderBox == null) return;
        cylinderBox.removeAllViews(); cylinderViews.clear();
        int columns = getResources().getConfiguration().screenWidthDp >= 600 ? 4 : 2;
        for (int c = 1; c <= profile().cylinders(); c += columns) {
            LinearLayout r = row();
            for (int n = c; n <= Math.min(c + columns - 1,profile().cylinders()); n++) {
                TextView tv = text("Cylinder " + n + "\n—",16,WHITE); panel(tv); cylinderViews.put(n,tv);
                r.addView(tv,new LinearLayout.LayoutParams(0,-2,1));
            }
            cylinderBox.addView(r);
        }
    }
    private void render() {
        long now = SystemClock.elapsedRealtime();
        for (Map.Entry<Integer,TextView> entry : cylinderViews.entrySet()) {
            int c = entry.getKey(); Cylinder data = cylinders.get(c); StringBuilder text = new StringBuilder("CYLINDER " + c + "\n");
            boolean warning = false, fault = false;
            if (!connected || data == null) text.append("—\nNot read");
            else if (data.values.isEmpty()) text.append("Unavailable\n").append(data.note);
            else {
                for (FordData.Misfire m : data.values) {
                    if (m.unit().equals("counts")) {
                        text.append(m.kind().startsWith("Last") ? "Cycle: " : "Avg: ").append((int)m.value()).append(" counts\n");
                        warning |= m.evaluated() && m.value() > 0; fault |= m.outsideLimit();
                    } else if (m.unit().equals("%")) {
                        text.append(String.format(Locale.US,"Stored: %.6f%%\n",m.value())); fault |= m.outsideLimit();
                    } else text.append("Raw: ").append(m.raw()).append(String.format(Locale.ROOT," (unit %02X)\n",m.uasid()));
                    if (!m.evaluated()) text.append(m.note()).append('\n');
                    if (m.outsideLimit()) text.append("Outside ECU limit\n");
                }
            }
            if (connected && data != null) { long age = (now-data.elapsed)/1000; text.append(age).append("s since read"); if (age > 30) text.append(" · stale"); }
            entry.getValue().setText(text.toString().trim());
            entry.getValue().setTextColor(fault ? Color.rgb(255,123,131) : warning ? Color.rgb(255,206,120) : WHITE);
        }
        for (Map.Entry<Integer,TextView> e : liveViews.entrySet()) {
            int pid = e.getKey(); Reading r = live.get(pid); String name = name(pid);
            boolean fresh = connected && r != null && r.value != null && now-r.elapsed <= 15000;
            String value = fresh ? formatted(pid,r.value) : "—";
            e.getValue().setText(name + "\n" + value + (r != null && !fresh ? "\nUnavailable / stale" : ""));
        }
    }
    private static String name(int pid) { return switch(pid) {
        case 0x0C -> "Engine RPM"; case 0x05 -> "Coolant"; case 6 -> "Short trim B1"; case 7 -> "Long trim B1";
        case 8 -> "Short trim B2"; case 9 -> "Long trim B2"; case 4 -> "Engine load"; case 0x0D -> "Speed";
        case 0x0F -> "Intake air"; default -> "Throttle";
    }; }
    private static String formatted(int pid,double v) { return switch(pid) {
        case 0x0C -> String.format(Locale.US,"%.0f rpm",v);
        case 0x05,0x0F -> String.format(Locale.US,"%.0f °F",v*1.8+32);
        case 0x0D -> String.format(Locale.US,"%.0f mph",v*0.621371);
        default -> String.format(Locale.US,"%.1f%%",v);
    }; }
    private void export(boolean debug) {
        if (session != null) { if (session.csvFile != null) csv = session.csvFile; if (session.rawFile != null) raw = session.rawFile; }
        if (connected) disconnect(); // Flush a stable snapshot before the SAF file chooser pauses the Activity.
        exportFile = debug ? raw : csv;
        if (exportFile == null || !exportFile.isFile()) {
            File dir = new File(getFilesDir(),"sessions"); File[] saved = dir.listFiles((d,n) -> n.startsWith("session-") && n.endsWith(debug ? ".txt" : ".csv"));
            if (saved != null && saved.length > 0) { Arrays.sort(saved,Comparator.comparing(File::getName).reversed()); exportFile = saved[0]; }
        }
        if (exportFile == null || !exportFile.isFile()) { toast("Connect once to create a session log"); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(debug ? "text/plain" : "text/csv")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,exportFile.getName());
        getPreferences(MODE_PRIVATE).edit().putString("export",exportFile.getName()).apply();
        ObdSession stopped = lastSession;
        exportWorker.execute(() -> {
            try {
                if (stopped != null && !stopped.awaitStopped()) throw new IOException("Log is still closing; try export again");
                handler.post(() -> { if (!isDestroyed()) startActivityForResult(intent,20); });
            } catch (Exception e) { handler.post(() -> { if (!isDestroyed()) toast(e.getMessage()); }); }
        });
    }
    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request != 20 || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String saved = getPreferences(MODE_PRIVATE).getString("export", "");
        if (!saved.matches("session-[0-9]+-[0-9a-f-]+\\.(csv|txt)")) { toast("No session selected"); return; }
        File source = new File(new File(getFilesDir(),"sessions"),saved);
        exportWorker.execute(() -> {
            try (InputStream in = new FileInputStream(source); OutputStream out = getContentResolver().openOutputStream(uri,"wt")) {
                if (out == null) throw new IOException("Cannot open selected destination");
                byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer,0,n);
                handler.post(() -> { if (!isDestroyed()) toast("Session exported"); });
            } catch (Exception e) { handler.post(() -> { if (!isDestroyed()) toast("Export failed: " + e.getMessage()); }); }
        });
    }
    private ArrayAdapter<String> adapter(String[] items) { return new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,items); }
    private TextView text(String value,int size,int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setPadding(0,dp(8),0,dp(8)); return t; }
    private void add(View v) { root.addView(v,new LinearLayout.LayoutParams(-1,-2)); }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(0,dp(3),0,dp(3)); return r; }
    private void panel(View v) { GradientDrawable bg = new GradientDrawable(); bg.setColor(CARD); bg.setCornerRadius(dp(12)); bg.setStroke(dp(2),BG); v.setBackground(bg); v.setPadding(dp(12),dp(12),dp(12),dp(12)); }
    private Button button(LinearLayout parent,String label,View.OnClickListener click) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(WHITE); b.setOnClickListener(click);
        if (parent == root) parent.addView(b,new LinearLayout.LayoutParams(-1,-2));
        else parent.addView(b,new LinearLayout.LayoutParams(0,-2,1)); return b;
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
}
