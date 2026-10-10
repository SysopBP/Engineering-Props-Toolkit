package com.sysopbp.engineeringprops;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String MODULE = "/data/adb/modules/engineering_props_toolkit";
    private static final String[] TABS = {"Overview", "Properties", "Modules", "Diagnostics", "Settings"};
    private static final int[] TAB_ICONS = {R.drawable.ic_home, R.drawable.ic_properties,
            R.drawable.ic_module, R.drawable.ic_diagnostics, R.drawable.ic_settings};
    private static final String[] ENGINEERING_KEYS = {
            "ro.product.model", "ro.product.device", "ro.build.version.release", "ro.build.version.sdk",
            "ro.build.version.security_patch", "ro.build.display.id", "ro.build.type", "ro.build.tags",
            "ro.debuggable", "ro.secure", "ro.adb.secure", "ro.boot.verifiedbootstate",
            "ro.boot.vbmeta.device_state", "ro.boot.flash.locked", "ro.boot.warranty_bit",
            "ro.boot.bootloader", "ro.csc.sales_code", "ril.sales_code", "init.svc.adbd",
            "service.adb.root", "persist.sys.usb.config", "sys.usb.config", "sys.usb.state",
            "init.svc.bootanim", "sys.boot_completed", "ro.system.build.type", "ro.vendor.build.type",
            "ro.product.build.type", "ro.bootimage.build.type"
    };
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayDeque<Report> history = new ArrayDeque<>();
    private final Map<String, String> snapshot = new LinkedHashMap<>();
    private final Map<String, String> properties = new TreeMap<>();
    private SharedPreferences preferences;
    private LinearLayout root, page, navigation;
    private TextView status, reportView;
    private ProgressBar progress;
    private String currentTab = "Overview", query = "", moduleConfig = "", lastReport = "", lastTitle = "Report";
    private boolean busy, rootKnown, rootAvailable, configLoaded;
    private int background, cardColor, raisedColor, accent;
    private final int foreground = 0xFFF4EFF5, secondary = 0xFFBFB3C1;

    private static final class Report {
        final String title, body;
        Report(String title, String body) { this.title = title; this.body = body; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("appearance", MODE_PRIVATE);
        if (state != null) {
            currentTab = state.getString("tab", "Overview");
            query = state.getString("query", "");
        }
        buildLayout();
        refreshOverview();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("tab", currentTab);
        state.putString("query", query);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void colors() {
        String theme = preferences.getString("theme", "glass");
        accent = preferences.getInt("accent", 0xFFB85B80);
        int brightness = preferences.getInt("brightness", 15);
        int shade = 5 + brightness / 5;
        background = Color.rgb(shade, shade, shade + 4);
        cardColor = "material".equals(theme) ? 0xFF28212B : 0xD92A222E;
        raisedColor = "material".equals(theme) ? 0xFF382C3D : 0xFF352B3A;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), 0xFF51404F);
        return drawable;
    }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }
    private Drawable icon(int resource, int color, int size) {
        Drawable drawable = getDrawable(resource).mutate();
        drawable.setTint(color);
        drawable.setBounds(0, 0, dp(size), dp(size));
        return drawable;
    }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private LinearLayout panel() {
        LinearLayout layout = column();
        layout.setPadding(dp(16), dp(12), dp(16), dp(14));
        layout.setBackground(shape(cardColor, 22));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), 0, dp(8));
        page.addView(layout, params);
        return layout;
    }
    private void label(LinearLayout parent, String title, int resource) {
        TextView view = text(title, 17, foreground);
        view.setTypeface(null, Typeface.BOLD);
        view.setCompoundDrawables(icon(resource, accent, 22), null, null, null);
        view.setCompoundDrawablePadding(dp(10));
        parent.addView(view);
    }
    private Button action(LinearLayout parent, String title, int resource, Runnable callback) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(foreground);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(14), dp(10), dp(12), dp(10));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setBackground(shape(raisedColor, 14));
        button.setCompoundDrawables(icon(resource, accent, 20), null, null, null);
        button.setCompoundDrawablePadding(dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(3));
        parent.addView(button, params);
        button.setOnClickListener(v -> callback.run());
        return button;
    }
    private void command(LinearLayout parent, String title, int resource, String script) {
        action(parent, title, resource, () -> execute(title, script, null));
    }
    private void note(LinearLayout parent, String value) { parent.addView(text(value, 13, secondary)); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }

    private void buildLayout() {
        colors();
        root = column();
        root.setBackgroundColor(background);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        LinearLayout brand = column();
        brand.setPadding(dp(20), dp(12), dp(20), dp(8));
        TextView title = text("Engineering Props", 24, foreground);
        title.setTypeface(null, Typeface.BOLD);
        title.setCompoundDrawables(icon(R.drawable.ic_chip, accent, 26), null, null, null);
        title.setCompoundDrawablePadding(dp(10));
        brand.addView(title);
        status = text("Checking root access…", 12, secondary);
        brand.addView(status);
        root.addView(brand);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = column();
        page.setPadding(dp(16), dp(8), dp(16), dp(20));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(4), dp(6), dp(4), dp(6));
        navigation.setBackground(shape(cardColor, 24));
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, -2);
        navParams.setMargins(dp(12), 0, dp(12), dp(10));
        root.addView(navigation, navParams);
        setContentView(root);
        root.requestApplyInsets();
        render();
    }

    private void updateStatus() {
        status.setText(busy ? "Operation in progress…" : rootKnown
                ? (rootAvailable ? "Root UID 0 connected • v0.6 preview" : "Root unavailable • grant access in KernelSU")
                : "Root has not been checked");
        status.setTextColor(rootAvailable ? 0xFF9AD9B0 : secondary);
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
    }

    private void render() {
        page.removeAllViews();
        TextView title = text(currentTab, 26, foreground);
        title.setTypeface(null, Typeface.BOLD);
        page.addView(title);
        navigation.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final String tabName = TABS[i];
            boolean selected = tabName.equals(currentTab);
            TextView tab = text(tabName, 10, selected ? accent : secondary);
            tab.setGravity(Gravity.CENTER);
            tab.setMaxLines(1);
            tab.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tab.setPadding(dp(2), dp(8), dp(2), dp(8));
            tab.setCompoundDrawables(null, icon(TAB_ICONS[i], selected ? accent : secondary, 23), null, null);
            tab.setCompoundDrawablePadding(dp(5));
            if (selected) tab.setBackground(shape(raisedColor, 16));
            tab.setContentDescription(tabName + " tab");
            navigation.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
            tab.setOnClickListener(v -> { currentTab = tabName; render(); });
        }
        switch (currentTab) {
            case "Properties": propertiesPage(); break;
            case "Modules": modulesPage(); break;
            case "Diagnostics": diagnosticsPage(); break;
            case "Settings": settingsPage(); break;
            default: overviewPage();
        }
        reportPanel();
        updateStatus();
    }

    private String get(String key) {
        String value = snapshot.get(key);
        return value == null || value.isEmpty() ? "Not reported" : value;
    }
    private void overviewPage() {
        LinearLayout device = panel();
        label(device, snapshot.isEmpty() ? Build.MODEL : get("ro.product.model"), R.drawable.ic_chip);
        note(device, "Android " + Build.VERSION.RELEASE + " • API " + Build.VERSION.SDK_INT);
        note(device, "Security patch " + Build.VERSION.SECURITY_PATCH);
        TextView build = text("Build type: " + get("ro.build.type"), 20, foreground);
        build.setTypeface(null, Typeface.BOLD);
        device.addView(build);
        note(device, "Debuggable " + get("ro.debuggable") + "  •  Secure " + get("ro.secure")
                + "  •  ADB secure " + get("ro.adb.secure"));
        note(device, "Verified boot " + get("ro.boot.verifiedbootstate") + "  •  adbd " + get("init.svc.adbd"));
        note(device, "Properties describe reported state. They do not prove adbd root support or change SELinux.");
        action(device, "Refresh device & root status", R.drawable.ic_refresh, this::refreshOverview);
        LinearLayout quick = panel();
        label(quick, "Quick checks", R.drawable.ic_diagnostics);
        command(quick, "ADB process & USB state", R.drawable.ic_usb, adb());
        command(quick, "Partition build identities", R.drawable.ic_properties, partition());
        action(quick, "Save comparison baseline", R.drawable.ic_history, this::saveBaseline);
        action(quick, "Compare with saved baseline", R.drawable.ic_compare, this::compareBaseline);
        String saved = preferences.getString("baseline_time", "");
        note(quick, saved.isEmpty() ? "No baseline saved yet. Saving captures the selected engineering properties."
                : "Baseline saved " + saved + ". Compare reads fresh values.");
        if (!history.isEmpty()) {
            LinearLayout recent = panel();
            label(recent, "Recent reports", R.drawable.ic_history);
            note(recent, "Last 8 reports in this session. Export a report to keep it.");
            for (Report report : history) {
                action(recent, report.title, R.drawable.ic_file, () -> {
                    lastTitle = report.title;
                    lastReport = report.body;
                    render();
                });
            }
        }
    }

    private String overview() {
        StringBuilder script = new StringBuilder();
        for (String key : ENGINEERING_KEYS) script.append("echo '").append(key).append("='\"$(getprop '")
                .append(key).append("')\"; ");
        script.append("echo 'SELINUX='\"$(getenforce)\"; echo '[Root identity]'; id; ")
                .append("echo '[Module]'; if [ -d '").append(MODULE).append("' ]; then cat '")
                .append(MODULE).append("/module.prop'; [ ! -e '").append(MODULE)
                .append("/disable' ] && echo 'Module enabled' || echo 'Module disabled'; else echo 'Module not installed'; fi");
        return script.toString();
    }
    private Map<String, String> parseSnapshot(String output) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : output.split("\n")) {
            int split = line.indexOf('=');
            if (split > 0) {
                String key = line.substring(0, split);
                if (Arrays.asList(ENGINEERING_KEYS).contains(key)) result.put(key, line.substring(split + 1).trim());
            }
        }
        return result;
    }
    private void refreshOverview() {
        execute("Engineering status", overview(), result -> {
            if (result.succeeded()) { snapshot.clear(); snapshot.putAll(parseSnapshot(result.output)); }
        });
    }
    private void saveBaseline() {
        if (busy) { toast("Wait for the current operation"); return; }
        new AlertDialog.Builder(this).setTitle("Save engineering baseline?")
                .setMessage("Reads current engineering properties and replaces the previous comparison baseline. This is a diagnostic snapshot, not a restore backup.")
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (dialog, which) ->
                    execute("Saved baseline", overview(), result -> {
                        if (!result.succeeded()) return;
                        Map<String, String> values = parseSnapshot(result.output);
                        snapshot.clear(); snapshot.putAll(values);
                        preferences.edit().putString("baseline", new JSONObject(values).toString())
                                .putString("baseline_time", timestamp()).apply();
                        toast("Baseline saved");
                    })).show();
    }
    private void compareBaseline() {
        String saved = preferences.getString("baseline", "");
        if (saved.isEmpty()) { toast("Save a baseline first"); return; }
        execute("Baseline comparison", overview(), result -> {
            if (!result.succeeded()) return;
            try {
                JSONObject before = new JSONObject(saved);
                Map<String, String> now = parseSnapshot(result.output);
                snapshot.clear(); snapshot.putAll(now);
                StringBuilder diff = new StringBuilder("Baseline: ").append(preferences.getString("baseline_time", ""))
                        .append("\nCurrent: ").append(timestamp()).append("\n\n");
                int changed = 0;
                for (String key : ENGINEERING_KEYS) {
                    String oldValue = before.optString(key, "<missing>"), newValue = now.getOrDefault(key, "<missing>");
                    if (!oldValue.equals(newValue)) {
                        changed++;
                        diff.append(key).append("\n  Before: ").append(oldValue)
                                .append("\n  Now:    ").append(newValue).append("\n\n");
                    }
                }
                diff.append(changed).append(" changed / ").append(ENGINEERING_KEYS.length).append(" checked");
                lastReport = diff.toString();
            } catch (Exception error) { lastReport = "Saved baseline cannot be read. Save a new baseline."; }
        });
    }

    private void propertiesPage() {
        LinearLayout browser = panel();
        label(browser, "Property browser", R.drawable.ic_search);
        note(browser, "Read-only. Search by property name or value; tap a result to copy it.");
        action(browser, "Read current properties", R.drawable.ic_refresh, () -> execute("Property inventory", "getprop", result -> {
            if (!result.succeeded()) return;
            properties.clear();
            Pattern pattern = Pattern.compile("^\\[([^]]+)\\]: \\[(.*)\\]$");
            for (String line : result.output.split("\n")) {
                Matcher match = pattern.matcher(line);
                if (match.matches()) properties.put(match.group(1), match.group(2));
            }
        }));
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(foreground);
        search.setHintTextColor(secondary);
        search.setHint("Search properties…");
        search.setContentDescription("Search property names and values");
        search.setCompoundDrawables(icon(R.drawable.ic_search, secondary, 20), null, null, null);
        search.setCompoundDrawablePadding(dp(8));
        search.setText(query);
        browser.addView(search);
        TextView count = text("", 12, secondary);
        browser.addView(count);
        LinearLayout results = column();
        browser.addView(results);
        Runnable filter = () -> {
            results.removeAllViews();
            String needle = query.toLowerCase(Locale.ROOT);
            int matches = 0, shown = 0;
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                String value = entry.getKey() + "=" + entry.getValue();
                if (!value.toLowerCase(Locale.ROOT).contains(needle)) continue;
                matches++;
                if (shown++ >= 80) continue;
                TextView row = text(entry.getKey() + "\n" + entry.getValue(), 13, foreground);
                row.setTypeface(Typeface.MONOSPACE);
                row.setPadding(dp(8), dp(10), dp(8), dp(10));
                row.setOnClickListener(v -> copy(value));
                results.addView(row);
            }
            count.setText(properties.isEmpty() ? "Load properties to begin" : matches + " matching • "
                    + Math.min(matches, 80) + " shown. Refine search for more.");
        };
        filter.run();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); filter.run(); }
            public void afterTextChanged(Editable editable) { }
        });
    }

    private void modulesPage() {
        LinearLayout modules = panel();
        label(modules, "KernelSU modules", R.drawable.ic_module);
        command(modules, "Read module inventory", R.drawable.ic_refresh, modules());
        command(modules, "Find engineering property writers", R.drawable.ic_search,
                "for m in /data/adb/modules/*; do [ -d \"$m\" ] || continue; grep -nE 'resetprop|ro.build.type|ro.debuggable|ro.secure|ro.adb.secure' \"$m\"/*.sh \"$m\"/system.prop 2>/dev/null; done | head -120");
        LinearLayout config = panel();
        label(config, "Rendering configuration", R.drawable.ic_graphics);
        note(config, "These settings update the installed Toolkit module configuration. They take effect when its service next runs or after reboot.");
        action(config, "Read current module configuration", R.drawable.ic_refresh, () -> execute("Toolkit configuration",
                "test -f '" + MODULE + "/config.conf' || { echo 'Toolkit config missing'; exit 1; }; cat '" + MODULE + "/config.conf'",
                result -> { if (result.succeeded()) { moduleConfig = result.output; configLoaded = true; } }));
        configControl(config, "Rendering profile", "GRAPHICS_PROFILE");
        configControl(config, "GPU overdraw", "GRAPHICS_OVERDRAW");
        command(config, "Read saved configuration backup", R.drawable.ic_history,
                "test -f '" + MODULE + "/config.conf.app-backup' || { echo 'No app configuration backup'; exit 1; }; cat '" + MODULE + "/config.conf.app-backup'");
        action(config, "Restore previous configuration", R.drawable.ic_history, () -> confirm("Restore module configuration?",
                "Replaces config.conf with the backup saved before the most recent app change. The restored settings apply when the module service next runs or after reboot.",
                () -> execute("Configuration restored", ToolkitConfig.restore(MODULE),
                        result -> { if (result.succeeded()) configLoaded = false; })));
    }
    private void configControl(LinearLayout parent, String title, String key) {
        String value = null;
        for (String line : moduleConfig.split("\n")) if (line.startsWith(key + "=")) value = line.substring(key.length() + 1).trim();
        note(parent, title + ": " + (!configLoaded ? "Refresh to read" : "1".equals(value) ? "Enabled in config"
                : "0".equals(value) ? "Disabled in config" : "Not configured"));
        final String requested = "1".equals(value) ? "0" : "1";
        Button button = action(parent, ("1".equals(value) ? "Disable " : "Enable ") + title.toLowerCase(Locale.ROOT), R.drawable.ic_graphics,
                () -> confirm("Update " + title.toLowerCase(Locale.ROOT) + "?", "Saves a backup, updates " + key + "=" + requested
                        + " and verifies the configuration value. Changes are pending the next module service run or reboot.",
                        () -> execute("Rendering configuration updated", toggle(key, requested), result -> {
                            if (result.succeeded()) { moduleConfig = result.output; configLoaded = true; }
                        })));
        button.setEnabled(configLoaded);
        button.setAlpha(configLoaded ? 1f : 0.5f);
    }
    private String toggle(String key, String value) {
        return ToolkitConfig.update(MODULE, key, value);
    }

    private void diagnosticsPage() {
        LinearLayout adb = panel();
        label(adb, "ADB & partitions", R.drawable.ic_usb);
        command(adb, "ADB daemon identity", R.drawable.ic_usb, adb());
        command(adb, "Partition properties & disk baseline", R.drawable.ic_properties, partition());
        LinearLayout samsung = panel();
        label(samsung, "Samsung firmware", R.drawable.ic_chip);
        command(samsung, "Firmware, CSC & Knox state", R.drawable.ic_chip,
                "for k in ro.product.model ro.boot.bootloader ro.csc.sales_code ril.sales_code ro.boot.warranty_bit ro.boot.verifiedbootstate ro.boot.flash.locked; do echo \"$k=$(getprop $k)\"; done");
        command(samsung, "SELinux state & recent denials", R.drawable.ic_shield,
                "getenforce; logcat -d -b all -t 1000 2>/dev/null | grep -i 'avc: denied' | tail -50; echo 'Audit sample complete; no policies changed'");
        command(samsung, "IMS & telephony packages", R.drawable.ic_module,
                "pm list packages | grep -iE 'ims|rcs|telephony' | head -80");
        LinearLayout boot = panel();
        label(boot, "Boot & reliability", R.drawable.ic_history);
        command(boot, "Boot uptime & completion", R.drawable.ic_history,
                "date; uptime; cat /proc/uptime; for k in sys.boot_completed init.svc.bootanim ro.boot.bootreason sys.boot.reason; do echo \"$k=$(getprop $k)\"; done");
        command(boot, "Toolkit boot timeline", R.drawable.ic_file,
                "test -f '" + MODULE + "/logs/boot-timeline.txt' || { echo 'Module timeline not found'; exit 1; }; tail -120 '" + MODULE + "/logs/boot-timeline.txt'");
        command(boot, "Toolkit boot events", R.drawable.ic_file,
                "test -f '" + MODULE + "/logs/boot.log' || { echo 'Module boot log not found'; exit 1; }; tail -150 '" + MODULE + "/logs/boot.log'");
        command(boot, "Recent crash buffer", R.drawable.ic_diagnostics, "logcat -d -b crash -t 220");
        command(boot, "Full diagnostic snapshot", R.drawable.ic_file,
                "date; " + overview() + "; " + adb() + "; " + partition() + "; " + modules());
        note(boot, "Reports may contain device identifiers, package names or log details. Review the report before sharing.");
    }
    private String adb() {
        return "echo '[ADB daemon and USB]'; for k in init.svc.adbd service.adb.root ro.debuggable ro.secure ro.adb.secure persist.sys.usb.config sys.usb.config sys.usb.state; do echo \"$k=$(getprop $k)\"; done; "
                + "echo '[Process identity]'; pids=$(pidof adbd); if [ -z \"$pids\" ]; then echo 'adbd not running'; fi; "
                + "for p in $pids; do grep -E 'Name:|Uid:|Gid:' /proc/$p/status; readlink /proc/$p/exe; done; "
                + "echo 'UID 2000 = shell; UID 0 = root. Reported engineering properties do not establish adb root support.'";
    }
    private String partition() {
        return "for k in ro.build ro.bootimage.build ro.system.build ro.system_ext.build ro.system_dlkm.build ro.vendor.build ro.vendor_dlkm.build ro.product.build ro.odm.build; do echo \"$k.type=$(getprop $k.type)  tags=$(getprop $k.tags)\"; done; "
                + "echo '[On-disk baseline]'; grep -hE '^ro\\.(build\\.type|debuggable|secure|adb\\.secure)=' /system/build.prop /vendor/build.prop /product/build.prop 2>/dev/null; echo 'Partition report complete'";
    }
    private String modules() {
        return "found=0; for m in /data/adb/modules/*; do [ -d \"$m\" ] || continue; found=1; echo \"=== ${m##*/} ===\"; "
                + "[ -e \"$m/disable\" ] && echo DISABLED || echo ENABLED; [ -e \"$m/remove\" ] && echo 'REMOVAL PENDING'; "
                + "grep -E '^(id|name|version|versionCode|author)=' \"$m/module.prop\" 2>/dev/null; done; "
                + "[ \"$found\" = 1 ] || echo 'No modules found'; echo 'Module inventory complete'";
    }

    private void settingsPage() {
        LinearLayout appearance = panel();
        label(appearance, "Appearance", R.drawable.ic_palette);
        action(appearance, "Theme: " + preferences.getString("theme", "glass"), R.drawable.ic_palette, () ->
                new AlertDialog.Builder(this).setTitle("Choose theme").setItems(new String[]{"One UI inspired glass", "Material dark"},
                        (dialog, which) -> { preferences.edit().putString("theme", which == 0 ? "glass" : "material").apply(); buildLayout(); }).show());
        action(appearance, "Accent color", R.drawable.ic_palette, () ->
                new AlertDialog.Builder(this).setTitle("Accent color").setItems(new String[]{"Wine red", "Lavender", "Ice blue", "Mint"},
                        (dialog, which) -> { int[] colors = {0xFFB85B80, 0xFFB4A2DE, 0xFF8FC3EE, 0xFF8FD0AD};
                            preferences.edit().putInt("accent", colors[which]).apply(); buildLayout(); }).show());
        note(appearance, "Background brightness");
        SeekBar brightness = new SeekBar(this);
        brightness.setMax(100);
        brightness.setProgress(preferences.getInt("brightness", 15));
        brightness.setContentDescription("Background brightness");
        appearance.addView(brightness);
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user) { int shade = 5 + value / 5; root.setBackgroundColor(Color.rgb(shade, shade, shade + 4)); }
            }
            public void onStopTrackingTouch(SeekBar bar) { preferences.edit().putInt("brightness", bar.getProgress()).apply(); colors(); }
        });
        LinearLayout data = panel();
        label(data, "Reports & baseline", R.drawable.ic_file);
        note(data, "Reports export to Downloads as timestamped text files. Baselines stay private to this app; session history clears when the app closes.");
        action(data, "Delete saved comparison baseline", R.drawable.ic_history, () -> confirm("Delete baseline?", "Removes only this app's saved engineering-property comparison.",
                () -> { preferences.edit().remove("baseline").remove("baseline_time").apply(); toast("Baseline deleted"); }));
        action(data, "Clear session reports", R.drawable.ic_history, () -> { history.clear(); lastReport = ""; render(); });
        LinearLayout about = panel();
        label(about, "Engineering Props • 0.6 preview", R.drawable.ic_info);
        note(about, "Root diagnostics and verified Toolkit configuration controls. KernelSU root permission is required for protected information. Module rendering settings require an installed engineering_props_toolkit module.");
        action(about, "Open project on GitHub", R.drawable.ic_info, () -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SysopBP/Engineering-Props-Toolkit"))); }
            catch (Exception error) { toast("No browser available"); }
        });
    }

    private void reportPanel() {
        LinearLayout report = panel();
        label(report, lastReport.isEmpty() ? "Report viewer" : lastTitle, R.drawable.ic_file);
        note(report, busy ? "Running… root commands have a 25-second timeout." : lastReport.isEmpty()
                ? "Run a diagnostic to view its results here." : "Select text to copy part of this report.");
        reportView = text(lastReport.length() > 60000 ? lastReport.substring(0, 60000)
                + "\n[Display shortened; export or copy to view the full report]" : lastReport, 12, foreground);
        reportView.setTypeface(Typeface.MONOSPACE);
        reportView.setTextIsSelectable(true);
        report.addView(reportView);
        if (!lastReport.isEmpty()) {
            action(report, "Copy report", R.drawable.ic_copy, () -> copy(lastReport));
            action(report, "Save to Downloads", R.drawable.ic_export, () -> export(false));
            action(report, "Share report", R.drawable.ic_share, () -> export(true));
        }
    }
    private void copy(String value) {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Engineering Props", value));
        toast("Copied");
    }
    private String timestamp() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()); }
    private void confirm(String title, String message, Runnable accepted) {
        if (busy) { toast("Wait for the current operation"); return; }
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> accepted.run()).show();
    }

    private void execute(String title, String script, Consumer<CommandRunner.Result> completed) {
        if (busy) { toast("Wait for the current operation"); return; }
        busy = true;
        updateStatus();
        worker.execute(() -> {
            CommandRunner.Result result;
            try {
                String guarded = "uid=$(id -u); echo __ROOT_UID__=$uid; [ \"$uid\" = 0 ] || { echo 'Root UID 0 required'; exit 77; }; " + script;
                result = CommandRunner.run(Arrays.asList("su", "-c", guarded), 25);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception error) {
                result = new CommandRunner.Result("Root command unavailable: " + error.getMessage(), -1, false, false);
            }
            final CommandRunner.Result value = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                rootKnown = true;
                rootAvailable = value.output.contains("__ROOT_UID__=0\n");
                String cleaned = value.output.replaceAll("(?m)^__ROOT_UID__=.*\\n?", "");
                lastTitle = title;
                lastReport = "Engineering Props 0.6 preview\n" + timestamp() + "\n" + title + "\n\n" + cleaned
                        + (value.timedOut ? "\nCommand timed out after 25 seconds. Check root access and retry." : "")
                        + (!value.timedOut && value.exitCode != 0 ? "\nExit status: " + value.exitCode : "")
                        + (value.truncated ? "\nReport truncated at 256 KiB." : "");
                if (completed != null) {
                    try { completed.accept(new CommandRunner.Result(cleaned, value.exitCode, value.timedOut, value.truncated)); }
                    catch (Exception error) { lastReport += "\nUnable to process report: " + error.getMessage(); }
                }
                history.addFirst(new Report(title + " • " + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()), lastReport));
                while (history.size() > 8) history.removeLast();
                render();
                toast(value.succeeded() ? "Report ready" : "Operation failed; see report");
            });
        });
    }

    private void export(boolean share) {
        if (busy) { toast("Wait for the current operation"); return; }
        if (lastReport.isEmpty()) return;
        final String body = lastReport;
        busy = true;
        updateStatus();
        worker.execute(() -> {
            Uri uri = null;
            String name = "EngineeringProps_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date()) + ".txt";
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("Downloads entry could not be created");
                try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                    if (stream == null) throw new IllegalStateException("Downloads stream unavailable");
                    stream.write(body.getBytes(StandardCharsets.UTF_8));
                }
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(uri, ready, null, null);
                final Uri saved = uri;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    busy = false;
                    updateStatus();
                    toast("Saved to Downloads: " + name);
                    if (share) {
                        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_STREAM, saved).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        send.setClipData(ClipData.newRawUri("Engineering Props report", saved));
                        try { startActivity(Intent.createChooser(send, "Share diagnostic report")); }
                        catch (Exception error) { toast("No sharing app available; report saved in Downloads"); }
                    }
                });
            } catch (Exception error) {
                if (uri != null) { try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) { } }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    busy = false;
                    updateStatus();
                    toast("Export failed: " + error.getMessage());
                });
            }
        });
    }
}
