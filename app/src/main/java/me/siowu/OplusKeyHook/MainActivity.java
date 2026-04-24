package me.siowu.OplusKeyHook;

import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import me.siowu.OplusKeyHook.utils.SPUtils;

public class MainActivity extends AppCompatActivity {

    private static final String TYPE_NONE = "none";
    private static final String TYPE_COMMON = "common";
    private static final String TYPE_OPEN_APP = "open_app";
    private static final String TYPE_XIAOBU_SHORTCUT = "xiaobu_shortcut";
    private static final String TYPE_CUSTOM_ACTIVITY = "custom_activity";
    private static final String TYPE_CUSTOM_URL_SCHEME = "custom_url_scheme";
    private static final String TYPE_CUSTOM_SHELL = "custom_shell";

    private Spinner spinnerGesture;
    private Spinner spinnerType;
    private Spinner spinnerCommon;
    private Button btnSelectApp;
    private Button btnSelectActivity;
    private EditText editUrlScheme;
    private EditText editxiaobuShortcuts;
    private EditText editShell;
    private TextView textSelectedPackage;
    private TextView textSelectedActivity;
    private LinearLayout layoutCommon;
    private LinearLayout layoutCustomActivity;
    private LinearLayout layoutActivitySelection;
    private LinearLayout layoutUrlScheme;
    private LinearLayout layoutxiaobuShortcuts;
    private LinearLayout layoutShell;
    private Button btnSave;
    private CheckBox checkboxVibrate;
    private CheckBox checkboxExecuteWhenScreenOff;

    private final List<AppOption> appOptions = new ArrayList<>();
    private final List<AppOption> filteredAppOptions = new ArrayList<>();
    private final List<ActivityOption> activityOptions = new ArrayList<>();

    private String selectedPackageName = "";
    private String selectedActivityName = "";
    private String loadedActivityPackageName = "";

    private AlertDialog appPickerDialog;
    private AlertDialog activityPickerDialog;
    private AppListAdapter appListAdapter;
    private ActivityListAdapter activityListAdapter;
    private EditText editAppSearch;
    private RadioGroup radioAppFilter;
    private TextView textAppPickerStatus;
    private TextView textActivityPickerStatus;

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
        btnSelectApp = findViewById(R.id.btnSelectApp);
        btnSelectActivity = findViewById(R.id.btnSelectActivity);
        editUrlScheme = findViewById(R.id.editUrlScheme);
        editxiaobuShortcuts = findViewById(R.id.editxiaobuShortcuts);
        editShell = findViewById(R.id.editShell);
        textSelectedPackage = findViewById(R.id.textSelectedPackage);
        textSelectedActivity = findViewById(R.id.textSelectedActivity);
        layoutCommon = findViewById(R.id.layoutCommon);
        layoutCustomActivity = findViewById(R.id.layoutCustomActivity);
        layoutActivitySelection = findViewById(R.id.layoutActivitySelection);
        layoutUrlScheme = findViewById(R.id.layoutUrlScheme);
        layoutxiaobuShortcuts = findViewById(R.id.layoutxiaobuShortcuts);
        layoutShell = findViewById(R.id.layoutShell);
        checkboxVibrate = findViewById(R.id.checkboxVibrate);
        checkboxExecuteWhenScreenOff = findViewById(R.id.checkboxExecuteWhenScreenOff);
        btnSave = findViewById(R.id.btnSave);

        setupStaticSpinners();
        setupSelectionButtons();

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

        loadInstalledApps(false, null);
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

    private void setupSelectionButtons() {
        btnSelectApp.setOnClickListener(v -> showAppPickerDialog());
        btnSelectActivity.setOnClickListener(v -> {
            if (TextUtils.isEmpty(selectedPackageName)) {
                Toast.makeText(this, R.string.prompt_select_app, Toast.LENGTH_SHORT).show();
                return;
            }
            showActivityPickerDialog();
        });
        updateSelectionViews();
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

        loadedActivityPackageName = "";
        activityOptions.clear();
        updateSelectionViews();
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

    private void loadInstalledApps(boolean forceRefresh, Runnable onComplete) {
        if (!forceRefresh && !appOptions.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        updateAppPickerStatus(getString(R.string.loading_apps), true);
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
                    Drawable icon = applicationInfo.loadIcon(packageManager);
                    boolean systemApp = isSystemApp(applicationInfo);
                    boolean launchable = packageManager.getLaunchIntentForPackage(packageName) != null;
                    options.add(new AppOption(label, packageName, icon, systemApp, launchable));
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
                updateSelectionViews();
                applyAppPickerFilter();
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        }).start();
    }

    private void loadActivitiesForPackage(String packageName, boolean forceRefresh, Runnable onComplete) {
        if (TextUtils.isEmpty(packageName)) {
            loadedActivityPackageName = "";
            activityOptions.clear();
            updateSelectionViews();
            updateActivityPickerItems();
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        if (!forceRefresh && TextUtils.equals(loadedActivityPackageName, packageName)) {
            updateActivityPickerItems();
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        updateActivityPickerStatus(getString(R.string.loading_activities), true);
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
                loadedActivityPackageName = packageName;
                activityOptions.clear();
                activityOptions.addAll(options);
                updateSelectionViews();
                updateActivityPickerItems();
                if (onComplete != null) {
                    onComplete.run();
                }
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

    private void showAppPickerDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_picker, null, false);
        editAppSearch = dialogView.findViewById(R.id.editAppSearch);
        radioAppFilter = dialogView.findViewById(R.id.radioAppFilter);
        Button btnRefreshApps = dialogView.findViewById(R.id.btnRefreshApps);
        ListView listViewApps = dialogView.findViewById(R.id.listViewApps);
        textAppPickerStatus = dialogView.findViewById(R.id.textAppPickerStatus);

        appListAdapter = new AppListAdapter();
        listViewApps.setAdapter(appListAdapter);
        listViewApps.setOnItemClickListener((parent, view, position, id) -> {
            AppOption option = filteredAppOptions.get(position);
            selectedPackageName = option.packageName;
            selectedActivityName = "";
            loadedActivityPackageName = "";
            activityOptions.clear();
            updateSelectionViews();
            if (activityPickerDialog != null) {
                activityPickerDialog.dismiss();
            }
            if (appPickerDialog != null) {
                appPickerDialog.dismiss();
            }
            if (TYPE_CUSTOM_ACTIVITY.equals(getSelectedTypeValue())) {
                loadActivitiesForPackage(selectedPackageName, false, null);
            }
        });

        editAppSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyAppPickerFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        AppOption selectedApp = findAppOption(selectedPackageName);
        if (selectedApp != null && selectedApp.systemApp) {
            radioAppFilter.check(R.id.radioSystemApps);
        } else {
            radioAppFilter.check(R.id.radioUserApps);
        }
        radioAppFilter.setOnCheckedChangeListener((group, checkedId) -> applyAppPickerFilter());
        btnRefreshApps.setOnClickListener(v -> loadInstalledApps(true, null));

        appPickerDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_select_app)
                .setView(dialogView)
                .setNegativeButton(R.string.action_confirm, null)
                .create();
        appPickerDialog.setOnDismissListener(dialog -> {
            appPickerDialog = null;
            appListAdapter = null;
            editAppSearch = null;
            radioAppFilter = null;
            textAppPickerStatus = null;
            filteredAppOptions.clear();
        });
        appPickerDialog.show();

        loadInstalledApps(false, this::applyAppPickerFilter);
    }

    private void showActivityPickerDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_activity_picker, null, false);
        Button btnRefreshActivities = dialogView.findViewById(R.id.btnRefreshActivities);
        ListView listViewActivities = dialogView.findViewById(R.id.listViewActivities);
        textActivityPickerStatus = dialogView.findViewById(R.id.textActivityPickerStatus);

        activityListAdapter = new ActivityListAdapter();
        listViewActivities.setAdapter(activityListAdapter);
        listViewActivities.setOnItemClickListener((parent, view, position, id) -> {
            selectedActivityName = activityOptions.get(position).className;
            updateSelectionViews();
            if (activityPickerDialog != null) {
                activityPickerDialog.dismiss();
            }
        });

        btnRefreshActivities.setOnClickListener(v -> loadActivitiesForPackage(selectedPackageName, true, null));

        activityPickerDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_select_activity)
                .setView(dialogView)
                .setNegativeButton(R.string.action_confirm, null)
                .create();
        activityPickerDialog.setOnDismissListener(dialog -> {
            activityPickerDialog = null;
            activityListAdapter = null;
            textActivityPickerStatus = null;
        });
        activityPickerDialog.show();

        loadActivitiesForPackage(selectedPackageName, false, null);
    }

    private void applyAppPickerFilter() {
        if (appListAdapter == null) {
            return;
        }

        String query = editAppSearch == null ? "" : editAppSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean showSystemApps = radioAppFilter != null && radioAppFilter.getCheckedRadioButtonId() == R.id.radioSystemApps;
        boolean launchableOnly = TYPE_OPEN_APP.equals(getSelectedTypeValue());

        filteredAppOptions.clear();
        for (AppOption option : appOptions) {
            if (option.systemApp != showSystemApps) {
                continue;
            }
            if (launchableOnly && !option.launchable) {
                continue;
            }
            if (!query.isEmpty()) {
                String lowerLabel = option.label.toLowerCase(Locale.ROOT);
                String lowerPackage = option.packageName.toLowerCase(Locale.ROOT);
                if (!lowerLabel.contains(query) && !lowerPackage.contains(query)) {
                    continue;
                }
            }
            filteredAppOptions.add(option);
        }

        appListAdapter.notifyDataSetChanged();
        updateAppPickerStatus(filteredAppOptions.isEmpty() ? getString(R.string.no_apps_found) : "", false);
    }

    private void updateActivityPickerItems() {
        if (activityListAdapter != null) {
            activityListAdapter.notifyDataSetChanged();
        }
        updateActivityPickerStatus(activityOptions.isEmpty() ? getString(R.string.no_activities_found) : "", false);
    }

    private void updateSelectionViews() {
        AppOption selectedApp = findAppOption(selectedPackageName);
        btnSelectApp.setText(selectedApp == null
                ? (TextUtils.isEmpty(selectedPackageName) ? getString(R.string.prompt_select_app) : selectedPackageName)
                : selectedApp.label);

        ActivityOption selectedActivity = findActivityOption(selectedActivityName);
        btnSelectActivity.setText(selectedActivity == null
                ? (TextUtils.isEmpty(selectedActivityName) ? getString(R.string.prompt_select_activity) : selectedActivityName)
                : selectedActivity.label);
        btnSelectActivity.setEnabled(!TextUtils.isEmpty(selectedPackageName));

        textSelectedPackage.setText(TextUtils.isEmpty(selectedPackageName)
                ? getString(R.string.not_selected)
                : selectedPackageName);
        textSelectedActivity.setText(TextUtils.isEmpty(selectedActivityName)
                ? getString(R.string.not_selected)
                : selectedActivityName);
    }

    private AppOption findAppOption(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        for (AppOption option : appOptions) {
            if (TextUtils.equals(option.packageName, packageName)) {
                return option;
            }
        }
        return null;
    }

    private ActivityOption findActivityOption(String className) {
        if (TextUtils.isEmpty(className)) {
            return null;
        }
        for (ActivityOption option : activityOptions) {
            if (TextUtils.equals(option.className, className)) {
                return option;
            }
        }
        return null;
    }

    private boolean isSystemApp(ApplicationInfo applicationInfo) {
        return (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                || (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    private void updateAppPickerStatus(String message, boolean loading) {
        if (textAppPickerStatus == null) {
            return;
        }
        textAppPickerStatus.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
        textAppPickerStatus.setText(message);
        if (appListAdapter != null) {
            appListAdapter.setLoading(loading);
        }
    }

    private void updateActivityPickerStatus(String message, boolean loading) {
        if (textActivityPickerStatus == null) {
            return;
        }
        textActivityPickerStatus.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
        textActivityPickerStatus.setText(message);
        if (activityListAdapter != null) {
            activityListAdapter.setLoading(loading);
        }
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

    private String getPrefix(int gesture) {
        switch (gesture) {
            case 0:
                return "single_";
            case 1:
                return "double_";
            case 2:
                return "long_";
            default:
                Log.w("MainActivity", "Unexpected gesture index: " + gesture);
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
            case TYPE_OPEN_APP:
            case "打开应用":
                return 2;
            case TYPE_XIAOBU_SHORTCUT:
            case "执行小布快捷指令":
                return 3;
            case TYPE_CUSTOM_ACTIVITY:
            case "自定义Activity":
                return 4;
            case TYPE_CUSTOM_URL_SCHEME:
            case "自定义UrlScheme":
                return 5;
            case TYPE_CUSTOM_SHELL:
            case "自定义Shell命令":
                return 6;
            default:
                return 0;
        }
    }

    private String getSelectedTypeValue() {
        switch (spinnerType.getSelectedItemPosition()) {
            case 1:
                return TYPE_COMMON;
            case 2:
                return TYPE_OPEN_APP;
            case 3:
                return TYPE_XIAOBU_SHORTCUT;
            case 4:
                return TYPE_CUSTOM_ACTIVITY;
            case 5:
                return TYPE_CUSTOM_URL_SCHEME;
            case 6:
                return TYPE_CUSTOM_SHELL;
            default:
                return TYPE_NONE;
        }
    }

    private void updateLayout(int pos) {
        layoutCommon.setVisibility(View.GONE);
        layoutCustomActivity.setVisibility(View.GONE);
        layoutActivitySelection.setVisibility(View.GONE);
        layoutUrlScheme.setVisibility(View.GONE);
        layoutxiaobuShortcuts.setVisibility(View.GONE);
        layoutShell.setVisibility(View.GONE);

        switch (pos) {
            case 1:
                layoutCommon.setVisibility(View.VISIBLE);
                break;
            case 2:
                layoutCustomActivity.setVisibility(View.VISIBLE);
                break;
            case 3:
                layoutxiaobuShortcuts.setVisibility(View.VISIBLE);
                break;
            case 4:
                layoutCustomActivity.setVisibility(View.VISIBLE);
                layoutActivitySelection.setVisibility(View.VISIBLE);
                break;
            case 5:
                layoutUrlScheme.setVisibility(View.VISIBLE);
                break;
            case 6:
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

    private final class AppListAdapter extends BaseAdapter {
        private boolean loading;

        @Override
        public int getCount() {
            return loading ? 0 : filteredAppOptions.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredAppOptions.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        void setLoading(boolean loading) {
            this.loading = loading;
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_app, parent, false);
                holder = new ViewHolder(
                        convertView.findViewById(R.id.imageAppIcon),
                        convertView.findViewById(R.id.textAppLabel),
                        convertView.findViewById(R.id.textAppPackage)
                );
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AppOption option = filteredAppOptions.get(position);
            holder.icon.setImageDrawable(option.icon);
            holder.label.setText(option.label);
            holder.detail.setText(option.packageName);
            return convertView;
        }
    }

    private final class ActivityListAdapter extends BaseAdapter {
        private boolean loading;

        @Override
        public int getCount() {
            return loading ? 0 : activityOptions.size();
        }

        @Override
        public Object getItem(int position) {
            return activityOptions.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        void setLoading(boolean loading) {
            this.loading = loading;
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ActivityViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_activity, parent, false);
                holder = new ActivityViewHolder(
                        convertView.findViewById(R.id.textActivityLabel),
                        convertView.findViewById(R.id.textActivityClass)
                );
                convertView.setTag(holder);
            } else {
                holder = (ActivityViewHolder) convertView.getTag();
            }

            ActivityOption option = activityOptions.get(position);
            holder.label.setText(option.label);
            holder.detail.setText(option.className);
            return convertView;
        }
    }

    private static final class ViewHolder {
        private final ImageView icon;
        private final TextView label;
        private final TextView detail;

        private ViewHolder(ImageView icon, TextView label, TextView detail) {
            this.icon = icon;
            this.label = label;
            this.detail = detail;
        }
    }

    private static final class ActivityViewHolder {
        private final TextView label;
        private final TextView detail;

        private ActivityViewHolder(TextView label, TextView detail) {
            this.label = label;
            this.detail = detail;
        }
    }

    private static final class AppOption {
        private final String label;
        private final String packageName;
        private final Drawable icon;
        private final boolean systemApp;
        private final boolean launchable;

        private AppOption(String label, String packageName, Drawable icon, boolean systemApp, boolean launchable) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.systemApp = systemApp;
            this.launchable = launchable;
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
