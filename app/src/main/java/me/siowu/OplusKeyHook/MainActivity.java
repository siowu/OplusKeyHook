package me.siowu.OplusKeyHook;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.*;

import com.google.android.material.button.MaterialButton;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import me.siowu.OplusKeyHook.utils.SPUtils;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerGesture, spinnerType, spinnerCommon;
    private EditText editPackage, editActivity, editUrlScheme, editxiaobuShortcuts, editShell;
    private LinearLayout layoutCommon, layoutCustomActivity, layoutUrlScheme, layoutxiaobuShortcuts, layoutShell;
    private Button btnSave, btnDonate;
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
        btnDonate = findViewById(R.id.btnDonate);

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
                new String[]{"无", "常用功能", "打开应用", "执行小布快捷指令", "自定义Activity", "自定义UrlScheme", "自定义Shell命令"}
        );
        adapterType.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(adapterType);

        ArrayAdapter<String> adapterCommon = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"微信付款码", "微信扫一扫", "支付宝付款码", "支付宝扫一扫", "一键闪记", "小布记忆"}
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
        btnDonate.setOnClickListener(v -> showDonateDialog());

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
            case "打开应用":
                return 2;
            case "执行小布快捷指令":
                return 3;
            case "自定义Activity":
                return 4;
            case "自定义UrlScheme":
                return 5;
            case "自定义Shell命令":
                return 6;
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
                layoutCustomActivity.setVisibility(View.VISIBLE);
                break;
            case 3:
                layoutxiaobuShortcuts.setVisibility(View.VISIBLE);
                break;
            case 4:
                layoutCustomActivity.setVisibility(View.VISIBLE);
                break;
            case 5:
                layoutUrlScheme.setVisibility(View.VISIBLE);
                break;
            case 6:
                layoutShell.setVisibility(View.VISIBLE);
                break;
        }
    }

    public void applyRootPermission() {
        Intent intent = new Intent("me.siowu.OplusKeyHook.TRIGGER");
        intent.putExtra("cmd", "su");
        sendBroadcast(intent);
    }

    private void showShellPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("由于系统限制，执行Shell命令需要Root权限，否则无法执行。\n\n为避免后台限制导致命令无法被及时执行，模块现改用新方案: 委托「系统桌面」执行命令，请按照以下步骤授予权限: \n1. 在 LSPosed 等管理器中将模块作用域勾选「系统桌面」并长按强行停止。\n2. 完成上一步之后，点击下方按钮授予Root权限。\n\n如已授权请忽略本提示。")
                .setCancelable(false)
                .setNegativeButton("授予Root权限", (dialog, which) -> {
                    applyRootPermission();
                })
//                .setPositiveButton("确定", null)
                .show();
    }

    private void showDonateDialog() {
        ImageView imageView = new ImageView(this);
        try {
            java.io.InputStream is = getAssets().open("wechat.png");
            Drawable drawable = Drawable.createFromStream(is, null);
            imageView.setImageDrawable(drawable);
            is.close();
        } catch (Exception e) {
            Log.e("OplusKeyHook", "加载微信付款码图片失败: " + e.getMessage());
            return;
        }
        int dp240 = (int) (240 * getResources().getDisplayMetrics().density);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(dp240, dp240));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(
                (int) (32 * getResources().getDisplayMetrics().density),
                (int) (24 * getResources().getDisplayMetrics().density),
                (int) (32 * getResources().getDisplayMetrics().density),
                0
        );
        layout.addView(imageView);

        new AlertDialog.Builder(this)
                .setTitle("投喂我~")
                .setView(layout)
                .setPositiveButton("关闭", null)
                .show();
    }

}
