package me.siowu.OplusKeyHook.hooks;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ShortcutsHook implements IXposedHookLoadPackage {

    private static Context mContext = null;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        if (!lpparam.packageName.equals("com.coloros.shortcuts")) {
            return;
        }

        try {
            // 直接 hook 指定的 MainActivity
            Class<?> mainActivityClass = XposedHelpers.findClass(
                    "com.coloros.shortcuts.ui.MainActivity",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                    mainActivityClass,
                    "onCreate",
                    android.os.Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            mContext = (Context) param.thisObject;
                            XposedBridge.log("ShortcutsHook: Successfully obtained Context from MainActivity");
                        }
                    }
            );

            // 目标类 = k8.a
            Class<?> clazz = XposedHelpers.findClass(
                    "k8.a",
                    lpparam.classLoader
            );

            // 目标方法 = i(Shortcut shortcut, boolean z, int i)
            XposedHelpers.findAndHookMethod(
                    clazz,
                    "i",
                    XposedHelpers.findClass("com.coloros.shortcuts.framework.db.entity.Shortcut", lpparam.classLoader),
                    boolean.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object shortcutObj = param.args[0];

                            if (shortcutObj != null) {
                                try {
                                    // shortcut.tag
                                    String tag = (String) XposedHelpers.getObjectField(shortcutObj, "tag");
                                    String des = (String) XposedHelpers.getObjectField(shortcutObj, "des");

                                    XposedBridge.log("成功获取到快捷方式信息 DES: " + des);
                                    XposedBridge.log("成功获取到快捷方式信息 ID: " + tag);

                                    if (tag != null) {
                                        copyToClipboard(tag);
                                    } else {
                                        XposedBridge.log("快捷方式ID为空");
                                    }
                                } catch (Throwable e) {
                                    XposedBridge.log("ShortcutsHook: failed read tag: " + e);
                                }
                            } else {
                                XposedBridge.log("ShortcutsHook: shortcut is null");
                            }
                        }
                    }
            );

        } catch (Throwable t) {
            XposedBridge.log("ShortcutsHook failed: " + t);
        }
    }

    private void copyToClipboard(final String tag) {
        if (mContext == null) {
            XposedBridge.log("ShortcutsHook: Context is still null, cannot copy to clipboard");
            return;
        }

        try {
            // 确保在UI线程中执行
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 复制到剪贴板
                        ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Shortcut Tag", tag);
                        clipboard.setPrimaryClip(clip);

                        XposedBridge.log("快捷方式ID复制成功");
                    } catch (Exception e) {
                        XposedBridge.log("快捷方式ID复制失败" + e);
                    }
                }
            });
        } catch (Exception e) {
            XposedBridge.log("ShortcutsHook: Error setting up clipboard operation: " + e);
        }
    }
}
