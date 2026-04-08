package me.siowu.OplusKeyHook;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import me.siowu.OplusKeyHook.utils.SPUtils;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerGesture, spinnerType, spinnerCommon;
    private EditText editPackage, editActivity, editUrlScheme, editxiaobuShortcuts, editShell;
    private LinearLayout layoutCommon, layoutCustomActivity, layoutUrlScheme, layoutxiaobuShortcuts, layoutShell;
    private Button btnSave;
    private CheckBox checkboxVibrate, checkboxExecuteWhenScreenOff;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            SPUtils.init(this);
        } catch (SecurityException e) {
            runOnUiThread(() -> {
                Toast.makeText(
                        MainActivity.this,
                        "请先激活模块",
                        Toast.LENGTH_LONG
                ).show();
            });
        }

        spinnerGesture = findViewById(R.id.spinnerGesture); // ⬅ 新增手势选择控件
        spinnerType = findViewById(R.id.spinnerType);
        spinnerCommon = findViewById(R.id.spinnerCommon);
        editPackage = findViewById(R.id.editPackage);
        editActivity = findViewById(R.id.editActivity);
        editUrlScheme = findViewById(R.id.editUrlScheme);
        editxiaobuShortcuts = findViewById(R.id.editxiaobuShortcuts);
        editShell = findViewById(R.id.editShell);
        layoutCommon = findViewById(R.id.layoutCommon);
        layoutCustomActivity = findViewById(R.id.layoutCustomActivity);
        layoutUrlScheme = findViewById(R.id.layoutUrlScheme);
        layoutxiaobuShortcuts = findViewById(R.id.layoutxiaobuShortcuts);
        layoutShell = findViewById(R.id.layoutShell);
        checkboxVibrate = findViewById(R.id.checkboxVibrate);
        checkboxExecuteWhenScreenOff = findViewById(R.id.checkboxExecuteWhenScreenOff);
        btnSave = findViewById(R.id.btnSave);

        // 手势选择
        ArrayAdapter<String> adapterGesture = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"短按", "双击", "长按"}
        );
        adapterGesture.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGesture.setAdapter(adapterGesture);

        // 类型选择
        ArrayAdapter<String> adapterType = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"无", "常用功能", "执行小布快捷指令", "自定义Activity", "自定义UrlScheme", "自定义Shell命令"}
        );
        adapterType.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(adapterType);

        ArrayAdapter<String> adapterCommon = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"微信付款码", "微信扫一扫", "支付宝付款码", "支付宝扫一扫", "云闪付付款码", "云闪付扫一扫", "一键闪记", "小布记忆", "开关手电筒"}
        );
        adapterCommon.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCommon.setAdapter(adapterCommon);

        // 当选择不同手势时加载不同配置
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

        loadGestureConfig(0); // 默认加载【短按】
    }

    private void loadGestureConfig(int gesture) {
        String prefix = getPrefix(gesture);

        spinnerType.setSelection(getTypeIndex(SPUtils.getString(prefix + "type", "无")));
        spinnerCommon.setSelection(SPUtils.getInt(prefix + "common_index", 0));

        editPackage.setText(SPUtils.getString(prefix + "package", ""));
        editActivity.setText(SPUtils.getString(prefix + "activity", ""));
        editUrlScheme.setText(SPUtils.getString(prefix + "url", ""));
        editxiaobuShortcuts.setText(SPUtils.getString(prefix + "xiaobu_shortcuts", ""));
        editShell.setText(SPUtils.getString(prefix + "shell", ""));

        checkboxVibrate.setChecked(SPUtils.getBoolean(prefix + "vibrate", true));
        checkboxExecuteWhenScreenOff.setChecked(SPUtils.getBoolean(prefix + "screen_off", true));

        updateLayout(spinnerType.getSelectedItemPosition());
    }

    private void saveConfig() {
        int gesture = spinnerGesture.getSelectedItemPosition();
        String prefix = getPrefix(gesture);

        SPUtils.putString(prefix + "type", (String) spinnerType.getSelectedItem());
        SPUtils.putInt(prefix + "common_index", spinnerCommon.getSelectedItemPosition());
        SPUtils.putString(prefix + "package", editPackage.getText().toString().trim());
        SPUtils.putString(prefix + "activity", editActivity.getText().toString().trim());
        SPUtils.putString(prefix + "url", editUrlScheme.getText().toString().trim());
        SPUtils.putString(prefix + "xiaobu_shortcuts", editxiaobuShortcuts.getText().toString().trim());
        SPUtils.putString(prefix + "shell", editShell.getText().toString().trim());

        SPUtils.putBoolean(prefix + "vibrate", checkboxVibrate.isChecked());
        SPUtils.putBoolean(prefix + "screen_off", checkboxExecuteWhenScreenOff.isChecked());

        Toast.makeText(this, "已保存（3 秒后生效）", Toast.LENGTH_SHORT).show();

        String type = (String) spinnerType.getSelectedItem();
        if (type.equals("自定义Shell命令")) {
            if (applyRootPermission()) {
                Toast.makeText(this, "已被授予Root权限", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已被拒绝Root权限，无法执行Shell命令", Toast.LENGTH_SHORT).show();
            }
            showShellPermissionDialog();
        }

    }

    private String getPrefix(int gesture) {
        switch (gesture) {
            case 0:
                return "single_"; // 短按
            case 1:
                return "double_"; // 双击
            case 2:
                return "long_";   // 长按
        }
        return "single_";
    }

    private int getTypeIndex(String type) {
        switch (type) {
            case "无":
                return 0;
            case "常用功能":
                return 1;
            case "执行小布快捷指令":
                return 2;
            case "自定义Activity":
                return 3;
            case "自定义UrlScheme":
                return 4;
            case "自定义Shell命令":
                return 5;
            default:
                return 0;
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
        }
    }

    public boolean applyRootPermission() {
        Process process = null;
        try {
            // 测试执行一条简单命令
            process = Runtime.getRuntime().exec("su -c echo root_ok");
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = br.readLine();
            return "root_ok".equals(result);  // 成功执行
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private void showShellPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("由于系统限制，执行Shell命令需要Root权限和自启动权限，否则无法执行。\n应用只会在执行命令的瞬间启动，执行完毕后自动退出，不会占用后台内存。\n在某些情况下，你可能还需要在应用详情的耗电管理中完全允许后台行为。")
                .setCancelable(false)
                .setNegativeButton("去授权", (dialog, which) -> {
                    // 继续执行跳转逻辑
                    gotoColorOSAutoStart();        // 自启动管理
                })
                .setPositiveButton("确定", null)
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


}
