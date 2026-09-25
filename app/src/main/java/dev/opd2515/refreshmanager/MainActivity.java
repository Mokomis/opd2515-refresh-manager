package dev.opd2515.refreshmanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String[] MODES = {"Default", "120 Hz", "144 Hz"};
    private static final int[] RATE_IDS = {0, 3, 4};
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> shownApps = new ArrayList<>();
    private SharedPreferences preferences;
    private AppAdapter adapter;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("rates", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), 0);
        root.setBackgroundColor(Color.rgb(248, 249, 252));

        TextView title = new TextView(this);
        title.setText("Refresh Manager");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(24, 28, 36));
        title.setPadding(0, 0, 0, dp(4));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Choose the maximum display mode for each app. Changes apply immediately.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(88, 94, 108));
        subtitle.setPadding(0, 0, 0, dp(12));
        root.addView(subtitle);

        EditText search = new EditText(this);
        search.setHint("Search apps");
        search.setSingleLine(true);
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(16), 0, dp(16));
        root.addView(progress, progressParams);

        ListView list = new ListView(this);
        list.setDividerHeight(1);
        adapter = new AppAdapter(this);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            public void afterTextChanged(Editable s) {}
        });

        executor.execute(this::loadApps);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0));
        List<AppEntry> found = new ArrayList<>();
        for (ApplicationInfo info : installed) {
            if (info.packageName.equals(getPackageName())) continue;
            Intent launch = pm.getLaunchIntentForPackage(info.packageName);
            if (launch == null) continue;
            CharSequence label = pm.getApplicationLabel(info);
            found.add(new AppEntry(label == null ? info.packageName : label.toString(),
                    info.packageName, pm.getApplicationIcon(info)));
        }
        Collections.sort(found, Comparator.comparing(
                app -> app.label.toLowerCase(Locale.ROOT)));
        runOnUiThread(() -> {
            allApps.clear();
            allApps.addAll(found);
            filter("");
            progress.setVisibility(View.GONE);
        });
    }

    private void filter(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        shownApps.clear();
        for (AppEntry app : allApps) {
            if (needle.isEmpty()
                    || app.label.toLowerCase(Locale.ROOT).contains(needle)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(needle)) {
                shownApps.add(app);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void setMode(AppEntry app, int position) {
        int rateId = RATE_IDS[position];
        preferences.edit().putInt(app.packageName, rateId).apply();
        executor.execute(() -> {
            String property = RefreshConfig.propertyFor(app.packageName);
            String command = "setprop " + property + " " + rateId
                    + "; service call oplusscreenmode 12 s16 " + app.packageName
                    + " i32 " + rateId;
            boolean ok = runRoot(command);
            runOnUiThread(() -> Toast.makeText(this,
                    ok ? app.label + ": " + MODES[position]
                            : "Root command failed. Check KernelSU permission.",
                    Toast.LENGTH_SHORT).show());
        });
    }

    private boolean runRoot(String command) {
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) { /* drain output */ }
            }
            return process.waitFor() == 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class AppAdapter extends BaseAdapter {
        private final Context context;

        AppAdapter(Context context) { this.context = context; }
        public int getCount() { return shownApps.size(); }
        public AppEntry getItem(int position) { return shownApps.get(position); }
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row;
            if (convertView == null) {
                row = new Row(context);
                convertView = row.root;
                convertView.setTag(row);
            } else {
                row = (Row) convertView.getTag();
            }
            AppEntry app = getItem(position);
            row.icon.setImageDrawable(app.icon);
            row.name.setText(app.label);
            row.packageName.setText(app.packageName);
            row.spinner.setOnItemSelectedListener(null);
            int rate = preferences.getInt(app.packageName, 0);
            row.spinner.setSelection(rate == 4 ? 2 : rate == 3 ? 1 : 0, false);
            row.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent, View view, int selected, long id) {
                    int current = preferences.getInt(app.packageName, 0);
                    if (current != RATE_IDS[selected]) setMode(app, selected);
                }
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            return convertView;
        }
    }

    private final class Row {
        final LinearLayout root;
        final ImageView icon;
        final TextView name;
        final TextView packageName;
        final Spinner spinner;

        Row(Context context) {
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(4), dp(10), dp(4), dp(10));

            icon = new ImageView(context);
            root.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

            LinearLayout text = new LinearLayout(context);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(12), 0, dp(8), 0);
            name = new TextView(context);
            name.setTextSize(16);
            name.setTextColor(Color.rgb(24, 28, 36));
            packageName = new TextView(context);
            packageName.setTextSize(11);
            packageName.setTextColor(Color.rgb(105, 110, 122));
            text.addView(name);
            text.addView(packageName);
            root.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            spinner = new Spinner(context);
            spinner.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_spinner_dropdown_item, MODES));
            root.addView(spinner, new LinearLayout.LayoutParams(dp(120),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private static final class AppEntry {
        final String label;
        final String packageName;
        final Drawable icon;
        AppEntry(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }
}

