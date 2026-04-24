package me.siowu.OplusKeyHook;

import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import me.siowu.OplusKeyHook.utils.SPUtils;

public class MainActivity extends AppCompatActivity {

    private static final String TYPE_NONE = "none";
    private static final String TYPE_COMMON = "common";
    private static final String TYPE_XIAOBU_SHORTCUT = "xiaobu_shortcut";
    private static final String TYPE_CUSTOM_ACTIVITY = "custom_activity";
    private static final String TYPE_CUSTOM_URL_SCHEME = "custom_url_scheme";
    private static final String TYPE_CUSTOM_SHELL = "custom_shell";

    private Spinner spinnerGesture;
    private Spinner spinnerType;
    private Spinner spinnerCommon;
    private Spinner spinnerApp;
    private Spinner spinnerActivity;
    private EditText editUrlScheme;
    private EditText editxiaobuShortcuts;
    private EditText editShell;
    private TextView textSelectedPackage;
    private TextView textSelectedActivity;
    private LinearLayout layoutCommon;
    private LinearLayout layoutCustomActivity;
    private LinearLayout layoutUrlScheme;
    private LinearLayout layoutxiaobuShortcuts;
    private LinearLayout layoutShell;
    private Button btnSave;
    private CheckBox checkboxVibrate;
    private CheckBox checkboxExecuteWhenScreenOff;

    private final List<AppOption> appOptions = new ArrayList<>();
    private final List<ActivityOption> activityOptions = new ArrayList<>();
    private ArrayAdapter<String> appAdapter;
    private ArrayAdapter<String> activityAdapter;

    private String selectedPackageName = "";
    private String selectedActivityName = "";
    private boolean suppressNextAppSelectionEvent = false;
    private boolean suppressNextActivitySelectionEvent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            SPUtils.init(this);
        } catch (SecurityException e) {
            runOnUiThread(() -> Toast.makeText(
                    MainActivity.this,
                    R.string.message_activate_module_first,
                    Toast.LENGTH_LONG
            ).show());
        }

        spinnerGesture = findViewById(R.id.spinnerGesture);
        spinnerType = findViewById(R.id.spinnerType);
        spinnerCommon = findViewById(R.id.spinnerCommon);
        spinnerApp = findViewById(R.id.spinnerApp);
        spinnerActivity = findViewById(R.id.spinnerActivity);
        editUrlScheme = findViewById(R.id.editUrlScheme);
        editxiaobuShortcuts = findViewById(R.id.editxiaobuShortcuts);
        editShell = findViewById(R.id.editShell);
        textSelectedPackage = findViewById(R.id.textSelectedPackage);
        textSelectedActivity = findViewById(R.id.textSelectedActivity);
        layoutCommon = findViewById(R.id.layoutCommon);
        layoutCustomActivity = findViewById(R.id.layoutCustomActivity);
        layoutUrlScheme = findViewById(R.id.layoutUrlScheme);
        layoutxiaobuShortcuts = findViewById(R.id.layoutxiaobuShortcuts);
        layoutShell = findViewById(R.id.layoutShell);
        checkboxVibrate = findViewById(R.id.checkboxVibrate);
        checkboxExecuteWhenScreenOff = findViewById(R.id.checkboxExecuteWhenScreenOff);
        btnSave = findViewById(R.id.btnSave);

        setupStaticSpinners();
        setupCustomActivitySpinners();

        spinnerGesture.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                loadGestureConfig(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spinnerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                updateLayout(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        btnSave.setOnClickListener(v -> saveConfig());

        loadInstalledApps();
        loadGestureConfig(0);
    }

    private void setupStaticSpinners() {
        ArrayAdapter<String> adapterGesture = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.gesture_options)))
        );
        adapterGesture.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGesture.setAdapter(adapterGesture);

        ArrayAdapter<String> adapterType = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.type_options)))
        );
        adapterType.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(adapterType);

        ArrayAdapter<String> adapterCommon = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.common_action_options)))
        );
        adapterCommon.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCommon.setAdapter(adapterCommon);
    }

    private void setupCustomActivitySpinners() {
        appAdapter = createSpinnerAdapter(Collections.singletonList(getString(R.string.loading_apps)));
        activityAdapter = createSpinnerAdapter(Collections.singletonList(getString(R.string.prompt_select_activity)));
        spinnerApp.setAdapter(appAdapter);
        spinnerActivity.setAdapter(activityAdapter);

        spinnerApp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressNextAppSelectionEvent) {
                    suppressNextAppSelectionEvent = false;
                    return;
                }

                if (position <= 0 || position - 1 >= appOptions.size()) {
                    if (position == 0) {
                        selectedPackageName = "";
                        selectedActivityName = "";
                        updateCustomActivitySummary();
                        updateActivitySpinner(Collections.singletonList(getString(R.string.prompt_select_activity)));
                    }
                    return;
                }

                AppOption selectedApp = appOptions.get(position - 1);
                if (!TextUtils.equals(selectedPackageName, selectedApp.packageName)) {
                    selectedPackageName = selectedApp.packageName;
                    selectedActivityName = "";
                    updateCustomActivitySummary();
                    loadActivitiesForPackage(selectedApp.packageName);
                } else if (activityOptions.isEmpty()) {
                    loadActivitiesForPackage(selectedApp.packageName);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spinnerActivity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressNextActivitySelectionEvent) {
                    suppressNextActivitySelectionEvent = false;
                    return;
                }

                if (position <= 0 || position - 1 >= activityOptions.size()) {
                    if (position == 0) {
                        selectedActivityName = "";
                        updateCustomActivitySummary();
                    }
                    return;
                }

                selectedActivityName = activityOptions.get(position - 1).className;
                updateCustomActivitySummary();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        updateCustomActivitySummary();
    }

    private ArrayAdapter<String> createSpinnerAdapter(List<String> values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>(values)
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void loadGestureConfig(int gesture) {
        String prefix = getPrefix(gesture);

        spinnerType.setSelection(getTypeIndex(SPUtils.getString(prefix + "type", TYPE_NONE)));
        spinnerCommon.setSelection(SPUtils.getInt(prefix + "common_index", 0));

        selectedPackageName = SPUtils.getString(prefix + "package", "");
        selectedActivityName = SPUtils.getString(prefix + "activity", "");
        editUrlScheme.setText(SPUtils.getString(prefix + "url", ""));
        editxiaobuShortcuts.setText(SPUtils.getString(prefix + "xiaobu_shortcuts", ""));
        editShell.setText(SPUtils.getString(prefix + "shell", ""));

        checkboxVibrate.setChecked(SPUtils.getBoolean(prefix + "vibrate", true));
        checkboxExecuteWhenScreenOff.setChecked(SPUtils.getBoolean(prefix + "screen_off", true));

        updateCustomActivitySummary();
        restoreAppSelection();
        updateLayout(spinnerType.getSelectedItemPosition());
    }

    private void saveConfig() {
        int gesture = spinnerGesture.getSelectedItemPosition();
        String prefix = getPrefix(gesture);
        String type = getSelectedTypeValue();

        SPUtils.putString(prefix + "type", type);
        SPUtils.putInt(prefix + "common_index", spinnerCommon.getSelectedItemPosition());
        SPUtils.putString(prefix + "package", selectedPackageName.trim());
        SPUtils.putString(prefix + "activity", selectedActivityName.trim());
        SPUtils.putString(prefix + "url", editUrlScheme.getText().toString().trim());
        SPUtils.putString(prefix + "xiaobu_shortcuts", editxiaobuShortcuts.getText().toString().trim());
        SPUtils.putString(prefix + "shell", editShell.getText().toString().trim());

        SPUtils.putBoolean(prefix + "vibrate", checkboxVibrate.isChecked());
        SPUtils.putBoolean(prefix + "screen_off", checkboxExecuteWhenScreenOff.isChecked());

        Toast.makeText(this, R.string.message_saved, Toast.LENGTH_SHORT).show();

        if (TYPE_CUSTOM_SHELL.equals(type)) {
            if (applyRootPermission()) {
                Toast.makeText(this, R.string.message_root_granted, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.message_root_denied, Toast.LENGTH_SHORT).show();
            }
            showShellPermissionDialog();
        }
    }

    private void loadInstalledApps() {
        updateAppSpinner(Collections.singletonList(getString(R.string.loading_apps)));
        new Thread(() -> {
            List<AppOption> options = new ArrayList<>();
            PackageManager packageManager = getPackageManager();
            Collator collator = Collator.getInstance(Locale.getDefault());

            try {
                List<ApplicationInfo> applications = getInstalledApplicationsCompat(packageManager);
                for (ApplicationInfo applicationInfo : applications) {
                    String packageName = applicationInfo.packageName;
                    String label = packageManager.getApplicationLabel(applicationInfo).toString().trim();
                    if (label.isEmpty()) {
                        label = packageName;
                    }
                    options.add(new AppOption(label, packageName));
                }
            } catch (Exception e) {
                Log.e("MainActivity", "loadInstalledApps", e);
            }

            options.sort((left, right) -> {
                int labelResult = collator.compare(left.label, right.label);
                if (labelResult != 0) {
                    return labelResult;
                }
                return collator.compare(left.packageName, right.packageName);
            });

            runOnUiThread(() -> {
                appOptions.clear();
                appOptions.addAll(options);
                updateAppSpinner(buildAppEntries(options));
                restoreAppSelection();
            });
        }).start();
    }

    private void loadActivitiesForPackage(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            activityOptions.clear();
            updateActivitySpinner(Collections.singletonList(getString(R.string.prompt_select_activity)));
            return;
        }

        updateActivitySpinner(Collections.singletonList(getString(R.string.loading_activities)));
        new Thread(() -> {
            List<ActivityOption> options = new ArrayList<>();
            PackageManager packageManager = getPackageManager();
            Collator collator = Collator.getInstance(Locale.getDefault());

            try {
                PackageInfo packageInfo = getPackageInfoCompat(packageManager, packageName);
                if (packageInfo.activities != null) {
                    for (ActivityInfo activityInfo : packageInfo.activities) {
                        String className = normalizeClassName(activityInfo.packageName, activityInfo.name);
                        CharSequence label = activityInfo.loadLabel(packageManager);
                        String displayName = buildActivityLabel(label, className);
                        options.add(new ActivityOption(displayName, className));
                    }
                }
            } catch (Exception e) {
                Log.e("MainActivity", "loadActivitiesForPackage", e);
            }

            options.sort((left, right) -> {
                int labelResult = collator.compare(left.label, right.label);
                if (labelResult != 0) {
                    return labelResult;
                }
                return collator.compare(left.className, right.className);
            });

            runOnUiThread(() -> {
                if (!TextUtils.equals(selectedPackageName, packageName)) {
                    return;
                }

                activityOptions.clear();
                activityOptions.addAll(options);
                updateActivitySpinner(buildActivityEntries(options));
                restoreActivitySelection();
            });
        }).start();
    }

    private List<ApplicationInfo> getInstalledApplicationsCompat(PackageManager packageManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0));
        }
        return packageManager.getInstalledApplications(0);
    }

    private PackageInfo getPackageInfoCompat(PackageManager packageManager, String packageName) throws PackageManager.NameNotFoundException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES));
        }
        return packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
    }

    private void restoreAppSelection() {
        if (appAdapter == null) {
            return;
        }

        int selection = 0;
        if (!TextUtils.isEmpty(selectedPackageName)) {
            for (int i = 0; i < appOptions.size(); i++) {
                if (TextUtils.equals(appOptions.get(i).packageName, selectedPackageName)) {
                    selection = i + 1;
                    break;
                }
            }
        }

        if (spinnerApp.getSelectedItemPosition() != selection) {
            suppressNextAppSelectionEvent = true;
            spinnerApp.setSelection(selection);
            if (selection > 0 || !TextUtils.isEmpty(selectedPackageName)) {
                loadActivitiesForPackage(selectedPackageName);
            } else {
                activityOptions.clear();
                updateActivitySpinner(Collections.singletonList(getString(R.string.prompt_select_activity)));
            }
        } else if (selection > 0) {
            loadActivitiesForPackage(selectedPackageName);
        } else if (!TextUtils.isEmpty(selectedPackageName)) {
            loadActivitiesForPackage(selectedPackageName);
        } else {
            activityOptions.clear();
            updateActivitySpinner(Collections.singletonList(getString(R.string.prompt_select_activity)));
        }
    }

    private void restoreActivitySelection() {
        int selection = 0;
        if (!TextUtils.isEmpty(selectedActivityName)) {
            for (int i = 0; i < activityOptions.size(); i++) {
                if (TextUtils.equals(activityOptions.get(i).className, selectedActivityName)) {
                    selection = i + 1;
                    break;
                }
            }
        }

        if (spinnerActivity.getSelectedItemPosition() != selection) {
            suppressNextActivitySelectionEvent = true;
            spinnerActivity.setSelection(selection);
        } else {
            updateCustomActivitySummary();
        }
    }

    private void updateAppSpinner(List<String> items) {
        replaceAdapterItems(appAdapter, items);
    }

    private void updateActivitySpinner(List<String> items) {
        replaceAdapterItems(activityAdapter, items);
    }

    private void replaceAdapterItems(ArrayAdapter<String> adapter, List<String> items) {
        adapter.clear();
        adapter.addAll(items);
        adapter.notifyDataSetChanged();
    }

    private List<String> buildAppEntries(List<AppOption> options) {
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.prompt_select_app));
        if (options.isEmpty()) {
            items.set(0, getString(R.string.no_apps_found));
            return items;
        }
        for (AppOption option : options) {
            items.add(option.label + " (" + option.packageName + ")");
        }
        return items;
    }

    private List<String> buildActivityEntries(List<ActivityOption> options) {
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.prompt_select_activity));
        if (options.isEmpty()) {
            items.set(0, getString(R.string.no_activities_found));
            return items;
        }
        for (ActivityOption option : options) {
            items.add(option.label);
        }
        return items;
    }

    private String buildActivityLabel(CharSequence label, String className) {
        String cleanLabel = label == null ? "" : label.toString().trim();
        if (cleanLabel.isEmpty() || TextUtils.equals(cleanLabel, className)) {
            return className;
        }
        return cleanLabel + " (" + className + ")";
    }

    private String normalizeClassName(String packageName, String className) {
        if (TextUtils.isEmpty(className)) {
            return "";
        }
        if (className.startsWith(".")) {
            return packageName + className;
        }
        return className;
    }

    private void updateCustomActivitySummary() {
        textSelectedPackage.setText(TextUtils.isEmpty(selectedPackageName)
                ? getString(R.string.not_selected)
                : selectedPackageName);
        textSelectedActivity.setText(TextUtils.isEmpty(selectedActivityName)
                ? getString(R.string.not_selected)
                : selectedActivityName);
    }

    private String getPrefix(int gesture) {
        switch (gesture) {
            case 0:
                return "single_";
            case 1:
                return "double_";
            case 2:
                return "long_";
            default:
                return "single_";
        }
    }

    private int getTypeIndex(String type) {
        switch (type) {
            case TYPE_NONE:
            case "无":
                return 0;
            case TYPE_COMMON:
            case "常用功能":
                return 1;
            case TYPE_XIAOBU_SHORTCUT:
            case "执行小布快捷指令":
                return 2;
            case TYPE_CUSTOM_ACTIVITY:
            case "自定义Activity":
                return 3;
            case TYPE_CUSTOM_URL_SCHEME:
            case "自定义UrlScheme":
                return 4;
            case TYPE_CUSTOM_SHELL:
            case "自定义Shell命令":
                return 5;
            default:
                return 0;
        }
    }

    private String getSelectedTypeValue() {
        switch (spinnerType.getSelectedItemPosition()) {
            case 1:
                return TYPE_COMMON;
            case 2:
                return TYPE_XIAOBU_SHORTCUT;
            case 3:
                return TYPE_CUSTOM_ACTIVITY;
            case 4:
                return TYPE_CUSTOM_URL_SCHEME;
            case 5:
                return TYPE_CUSTOM_SHELL;
            default:
                return TYPE_NONE;
        }
    }

    private void updateLayout(int pos) {
        layoutCommon.setVisibility(View.GONE);
        layoutCustomActivity.setVisibility(View.GONE);
        layoutUrlScheme.setVisibility(View.GONE);
        layoutxiaobuShortcuts.setVisibility(View.GONE);
        layoutShell.setVisibility(View.GONE);

        switch (pos) {
            case 1:
                layoutCommon.setVisibility(View.VISIBLE);
                break;
            case 2:
                layoutxiaobuShortcuts.setVisibility(View.VISIBLE);
                break;
            case 3:
                layoutCustomActivity.setVisibility(View.VISIBLE);
                break;
            case 4:
                layoutUrlScheme.setVisibility(View.VISIBLE);
                break;
            case 5:
                layoutShell.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }

    public boolean applyRootPermission() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su -c echo root_ok");
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = br.readLine();
            return "root_ok".equals(result);
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private void showShellPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_notice)
                .setMessage(R.string.dialog_shell_permission_message)
                .setCancelable(false)
                .setNegativeButton(R.string.action_authorize, (dialog, which) -> gotoColorOSAutoStart())
                .setPositiveButton(R.string.action_confirm, null)
                .show();
    }

    private void gotoColorOSAutoStart() {
        try {
            Runtime.getRuntime().exec(new String[]{
                    "su", "-c",
                    "am start -n com.oplus.battery/com.oplus.startupapp.view.StartupAppListActivity"
            });
        } catch (Exception e) {
            Log.e("gotoColorOSAutoStart", e.getMessage());
        }
    }

    private static final class AppOption {
        private final String label;
        private final String packageName;

        private AppOption(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

    private static final class ActivityOption {
        private final String label;
        private final String className;

        private ActivityOption(String label, String className) {
            this.label = label;
            this.className = className;
        }
    }
}
