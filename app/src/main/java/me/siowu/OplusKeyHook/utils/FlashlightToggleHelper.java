package me.siowu.OplusKeyHook.utils;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedHelpers;

public final class FlashlightToggleHelper {
    private static final Object LOCK = new Object();
    private static final long TORCH_STATE_TIMEOUT_MS = 150L;
    private static volatile String flashCameraId;
    private static volatile Integer flashStrengthMaximumLevel;
    private static volatile String trackedCameraId;
    private static volatile Boolean torchEnabled;
    private static volatile boolean torchCallbackRegistered;
    private static CameraManager.TorchCallback torchCallback;

    private FlashlightToggleHelper() {
    }

    public static ToggleResult toggle() {
        Context context = resolveSystemContext();
        if (context == null) {
            return ToggleResult.failure("system context unavailable");
        }

        ToggleResult primary = toggleWithCameraManager(context);
        if (primary.isSuccess()) {
            return primary;
        }

        ToggleResult fallback = toggleWithShell(primary.getDesiredState());
        if (fallback.isSuccess()) {
            return fallback;
        }

        return ToggleResult.failure(primary.getMessage() + "; " + fallback.getMessage());
    }

    private static ToggleResult toggleWithCameraManager(Context context) {
        Boolean desiredState = null;
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) {
                return ToggleResult.failure("camera service unavailable");
            }

            String cameraId = getFlashCameraId(cameraManager);
            if (cameraId == null) {
                return ToggleResult.failure("no flash camera available");
            }

            ensureTorchCallback(cameraManager, cameraId);

            Boolean currentState = awaitTorchState();
            if (currentState == null) {
                return ToggleResult.failure("camera_manager failed: torch state unavailable");
            }

            desiredState = !currentState;
            setTorchState(cameraManager, cameraId, desiredState);
            synchronized (LOCK) {
                torchEnabled = desiredState;
                LOCK.notifyAll();
            }
            return ToggleResult.success("camera_manager", desiredState);
        } catch (Throwable t) {
            return ToggleResult.failure("camera_manager failed: " + t.getMessage(), desiredState);
        }
    }

    private static ToggleResult toggleWithShell(Boolean desiredState) {
        if (desiredState == null) {
            return toggleUnknownStateWithShell();
        }

        List<String> commands = new ArrayList<>();
        String target = desiredState ? "on" : "off";
        commands.add("cmd flashlight set " + target);
        commands.add("cmd flashlight " + target);
        commands.add("cmd statusbar click-tile com.android.systemui/.qs.tiles.FlashlightTileService");

        StringBuilder failures = new StringBuilder();
        for (String command : commands) {
            ShellResult shellResult = runShellCommand(command);
            if (shellResult.success) {
                synchronized (LOCK) {
                    torchEnabled = command.contains("click-tile") ? null : desiredState;
                    LOCK.notifyAll();
                }
                return ToggleResult.success("shell:" + command, desiredState);
            }

            if (failures.length() > 0) {
                failures.append(" | ");
            }
            failures.append(command).append(" -> ").append(shellResult.message);
        }

        return ToggleResult.failure("shell fallback failed: " + failures);
    }

    private static ToggleResult toggleUnknownStateWithShell() {
        String command = "cmd statusbar click-tile com.android.systemui/.qs.tiles.FlashlightTileService";
        ShellResult shellResult = runShellCommand(command);
        if (!shellResult.success) {
            return ToggleResult.failure("shell fallback failed: " + command + " -> " + shellResult.message);
        }

        synchronized (LOCK) {
            torchEnabled = null;
            LOCK.notifyAll();
        }
        return ToggleResult.success("shell:" + command, null);
    }

    private static Context resolveSystemContext() {
        try {
            Object application = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication"
            );
            if (application instanceof Context) {
                return ((Context) application).getApplicationContext();
            }
        } catch (Throwable ignored) {
        }

        try {
            Object activityThread = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentActivityThread"
            );
            if (activityThread != null) {
                Object systemContext = XposedHelpers.callMethod(activityThread, "getSystemContext");
                if (systemContext instanceof Context) {
                    return (Context) systemContext;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static String getFlashCameraId(CameraManager cameraManager) throws Exception {
        if (flashCameraId != null) {
            return flashCameraId;
        }

        String fallbackId = null;
        for (String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (!Boolean.TRUE.equals(flashAvailable)) {
                continue;
            }

            if (fallbackId == null) {
                fallbackId = cameraId;
            }

            Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                flashCameraId = cameraId;
                return flashCameraId;
            }
        }

        flashCameraId = fallbackId;
        return flashCameraId;
    }

    private static void setTorchState(CameraManager cameraManager, String cameraId, boolean enabled) throws Exception {
        if (!enabled) {
            cameraManager.setTorchMode(cameraId, false);
            return;
        }

        Integer maxStrengthLevel = getFlashStrengthMaximumLevel(cameraManager, cameraId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && maxStrengthLevel != null
                && maxStrengthLevel > 1) {
            cameraManager.turnOnTorchWithStrengthLevel(cameraId, maxStrengthLevel);
            return;
        }

        cameraManager.setTorchMode(cameraId, true);
    }

    private static Integer getFlashStrengthMaximumLevel(CameraManager cameraManager, String cameraId) throws Exception {
        if (flashStrengthMaximumLevel != null) {
            return flashStrengthMaximumLevel;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            flashStrengthMaximumLevel = 1;
            return flashStrengthMaximumLevel;
        }

        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
        Integer maxStrengthLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
        flashStrengthMaximumLevel = maxStrengthLevel == null ? 1 : maxStrengthLevel;
        return flashStrengthMaximumLevel;
    }

    private static void ensureTorchCallback(CameraManager cameraManager, String cameraId) throws Exception {
        synchronized (LOCK) {
            trackedCameraId = cameraId;
            if (torchCallbackRegistered) {
                return;
            }

            torchCallback = new CameraManager.TorchCallback() {
                @Override
                public void onTorchModeChanged(String changedCameraId, boolean enabled) {
                    if (!changedCameraId.equals(trackedCameraId)) {
                        return;
                    }
                    synchronized (LOCK) {
                        torchEnabled = enabled;
                        LOCK.notifyAll();
                    }
                }

                @Override
                public void onTorchModeUnavailable(String changedCameraId) {
                    if (!changedCameraId.equals(trackedCameraId)) {
                        return;
                    }
                    synchronized (LOCK) {
                        torchEnabled = false;
                        LOCK.notifyAll();
                    }
                }
            };

            Handler handler = Looper.getMainLooper() == null ? null : new Handler(Looper.getMainLooper());
            cameraManager.registerTorchCallback(torchCallback, handler);
            torchCallbackRegistered = true;
        }
    }

    private static Boolean awaitTorchState() {
        synchronized (LOCK) {
            if (torchEnabled != null) {
                return torchEnabled;
            }

            try {
                LOCK.wait(TORCH_STATE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return torchEnabled;
        }
    }

    private static ShellResult runShellCommand(String command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            String stdout = readAll(process.getInputStream());
            String stderr = readAll(process.getErrorStream());
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return ShellResult.success(stdout.isEmpty() ? "ok" : stdout);
            }
            String message = stderr.isEmpty() ? stdout : stderr;
            return ShellResult.failure("exit=" + exitCode + " " + message.trim());
        } catch (Throwable t) {
            return ShellResult.failure(t.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String readAll(java.io.InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
            return builder.toString();
        }
    }

    public static final class ToggleResult {
        private final boolean success;
        private final String backend;
        private final String message;
        private final Boolean desiredState;

        private ToggleResult(boolean success, String backend, String message, Boolean desiredState) {
            this.success = success;
            this.backend = backend;
            this.message = message;
            this.desiredState = desiredState;
        }

        public static ToggleResult success(String backend, Boolean desiredState) {
            return new ToggleResult(true, backend, "ok", desiredState);
        }

        public static ToggleResult failure(String message) {
            return new ToggleResult(false, null, message, null);
        }

        public static ToggleResult failure(String message, Boolean desiredState) {
            return new ToggleResult(false, null, message, desiredState);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getBackend() {
            return backend;
        }

        public String getMessage() {
            return message;
        }

        public Boolean getDesiredState() {
            return desiredState;
        }
    }

    private static final class ShellResult {
        private final boolean success;
        private final String message;

        private ShellResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        private static ShellResult success(String message) {
            return new ShellResult(true, message);
        }

        private static ShellResult failure(String message) {
            return new ShellResult(false, message == null ? "unknown shell error" : message);
        }
    }
}
