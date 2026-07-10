package com.sw.companiodeck;

import android.app.Activity;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.swordfish.libretrodroid.GLRetroView;
import com.swordfish.libretrodroid.GLRetroViewData;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SameBoyPlayerActivity extends Activity implements LifecycleOwner {
    public static final String EXTRA_ROM_URI = "rom_uri";
    public static final String EXTRA_ROM_NAME = "rom_name";
    public static final String EXTRA_FILE_NAME = "file_name";

    private static final String CORE_LIBRARY = "libsameboy_libretro_android.so";
    private static final String PREFS = "companion_deck_native_prefs";
    private static final int MAX_ROM_BYTES = 64 * 1024 * 1024;

    private static final int ICON_UP = 1;
    private static final int ICON_DOWN = 2;
    private static final int ICON_LEFT = 3;
    private static final int ICON_RIGHT = 4;
    private static final int ICON_A = 5;
    private static final int ICON_B = 6;
    private static final int ICON_START = 7;
    private static final int ICON_SELECT = 8;
    private static final int ICON_CENTER = 9;
    private static final int ICON_MENU = 10;

    private final LifecycleRegistry lifecycleRegistry = new LifecycleRegistry(this);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean saving = new AtomicBoolean(false);

    private FrameLayout root;
    private FrameLayout controlsLayer;
    private GLRetroView retroView;
    private View sidebar;
    private String romUri;
    private String romName;
    private String fileName;
    private File saveRamFile;
    private File quickStateFile;

    private boolean hapticsEnabled = true;
    private boolean controlsVisible = true;
    private boolean turboEnabled = false;
    private boolean landscape = true;
    private volatile boolean coreReady = false;

    private final Runnable autosaveRunnable = new Runnable() {
        @Override
        public void run() {
            saveBatteryAsync();
            handler.postDelayed(this, 15000L);
        }
    };

    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        applyImmersiveMode();

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        hapticsEnabled = prefs.getBoolean("player_haptics", true);

        romUri = getIntent().getStringExtra(EXTRA_ROM_URI);
        romName = getIntent().getStringExtra(EXTRA_ROM_NAME);
        fileName = getIntent().getStringExtra(EXTRA_FILE_NAME);
        if (romName == null || romName.trim().isEmpty()) romName = "Jogo GB/GBC";
        if (fileName == null || fileName.trim().isEmpty()) fileName = romName;

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        showLoading("Preparando SameBoy nativo...");
        new Thread(this::loadRomAndStart, "SameBoyRomLoader").start();
    }

    private void loadRomAndStart() {
        try {
            if (romUri == null || romUri.trim().isEmpty()) {
                throw new IllegalArgumentException("URI da ROM não foi recebida.");
            }

            byte[] rom = readRom(Uri.parse(romUri), fileName);
            String key = buildSaveKey(romName, romUri);

            File base = new File(getFilesDir(), "sameboy");
            File system = new File(base, "system");
            File saves = new File(base, "saves");
            File states = new File(base, "states");
            ensureDirectory(system);
            ensureDirectory(saves);
            ensureDirectory(states);

            saveRamFile = new File(saves, key + ".srm");
            quickStateFile = new File(states, key + ".state");

            File core = new File(getApplicationInfo().nativeLibraryDir, CORE_LIBRARY);
            if (!core.isFile() || core.length() == 0) {
                throw new IllegalStateException(
                        "Core SameBoy ausente para " + Build.SUPPORTED_ABIS[0]
                );
            }

            GLRetroViewData data = new GLRetroViewData(this);
            data.setCoreFilePath(core.getAbsolutePath());
            data.setGameFileBytes(rom);
            data.setSystemDirectory(system.getAbsolutePath());
            data.setSavesDirectory(saves.getAbsolutePath());
            data.setPreferLowLatencyAudio(true);
            data.setSkipDuplicateFrames(true);
            data.setRumbleEventsEnabled(false);

            if (saveRamFile.isFile() && saveRamFile.length() > 0) {
                data.setSaveRAMState(readFile(saveRamFile, 8 * 1024 * 1024));
            }

            runOnUiThread(() -> startNativePlayer(data));
        } catch (Throwable error) {
            String report = saveErrorReport(error);
            runOnUiThread(() -> showFatalError(error, report));
        }
    }

    private void startNativePlayer(GLRetroViewData data) {
        try {
            root.removeAllViews();

            retroView = new GLRetroView(this, data);
            getLifecycle().addObserver(retroView);
            root.addView(retroView, new FrameLayout.LayoutParams(-1, -1));

            addControls();
            addMenuButton();

            handler.postDelayed(() -> {
                coreReady = true;
                handler.removeCallbacks(autosaveRunnable);
                handler.postDelayed(autosaveRunnable, 15000L);
            }, 1800L);
        } catch (Throwable error) {
            String report = saveErrorReport(error);
            showFatalError(error, report);
        }
    }

    private void showLoading(String message) {
        root.removeAllViews();
        TextView label = new TextView(this);
        label.setText(message);
        label.setTextColor(Color.rgb(230, 235, 246));
        label.setTextSize(20);
        label.setGravity(Gravity.CENTER);
        root.addView(label, new FrameLayout.LayoutParams(-1, -1));
    }

    private void showFatalError(Throwable error, String report) {
        root.removeAllViews();

        TextView label = new TextView(this);
        label.setText(
                "Não foi possível iniciar o SameBoy nativo.\n\n"
                        + compactError(error)
                        + (report.isEmpty() ? "" : "\n\nLog: " + report)
        );
        label.setTextColor(Color.rgb(235, 238, 246));
        label.setTextSize(16);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.addView(label, new FrameLayout.LayoutParams(-1, -1));

        Button back = actionButton("Voltar");
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(180), dp(52), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        lp.setMargins(0, 0, 0, dp(26));
        root.addView(back, lp);
    }

    private void addControls() {
        controlsLayer = new FrameLayout(this);
        controlsLayer.setTag("nativeControls");
        root.addView(controlsLayer, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout dpad = new LinearLayout(this);
        dpad.setOrientation(LinearLayout.VERTICAL);
        dpad.setGravity(Gravity.CENTER);

        View up = control(ICON_UP, "Cima", KeyEvent.KEYCODE_DPAD_UP);
        View down = control(ICON_DOWN, "Baixo", KeyEvent.KEYCODE_DPAD_DOWN);

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.HORIZONTAL);
        middle.setGravity(Gravity.CENTER);

        View left = control(ICON_LEFT, "Esquerda", KeyEvent.KEYCODE_DPAD_LEFT);
        View center = control(ICON_CENTER, "Centro", KeyEvent.KEYCODE_UNKNOWN);
        center.setEnabled(false);
        View right = control(ICON_RIGHT, "Direita", KeyEvent.KEYCODE_DPAD_RIGHT);

        middle.addView(left, new LinearLayout.LayoutParams(dp(58), dp(58)));
        middle.addView(center, new LinearLayout.LayoutParams(dp(58), dp(58)));
        middle.addView(right, new LinearLayout.LayoutParams(dp(58), dp(58)));
        dpad.addView(up, new LinearLayout.LayoutParams(dp(58), dp(58)));
        dpad.addView(middle);
        dpad.addView(down, new LinearLayout.LayoutParams(dp(58), dp(58)));

        FrameLayout.LayoutParams dpadLp = new FrameLayout.LayoutParams(
                dp(190), dp(190), Gravity.BOTTOM | Gravity.LEFT
        );
        dpadLp.setMargins(dp(22), 0, 0, dp(24));
        controlsLayer.addView(dpad, dpadLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        // O frontend troca o layout Android A/B para o RetroPad.
        View b = control(ICON_B, "Botão B", KeyEvent.KEYCODE_BUTTON_A);
        View a = control(ICON_A, "Botão A", KeyEvent.KEYCODE_BUTTON_B);

        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(dp(76), dp(76));
        actionLp.setMargins(dp(5), 0, dp(5), 0);
        actions.addView(b, actionLp);
        actions.addView(a, actionLp);

        FrameLayout.LayoutParams actionsLp = new FrameLayout.LayoutParams(
                dp(180), dp(92), Gravity.BOTTOM | Gravity.RIGHT
        );
        actionsLp.setMargins(0, 0, dp(22), dp(58));
        controlsLayer.addView(actions, actionsLp);

        LinearLayout system = new LinearLayout(this);
        system.setOrientation(LinearLayout.HORIZONTAL);
        system.setGravity(Gravity.CENTER);

        View select = control(ICON_SELECT, "Select", KeyEvent.KEYCODE_BUTTON_SELECT);
        View start = control(ICON_START, "Start", KeyEvent.KEYCODE_BUTTON_START);

        LinearLayout.LayoutParams systemButtonLp = new LinearLayout.LayoutParams(dp(108), dp(46));
        systemButtonLp.setMargins(dp(4), 0, dp(4), 0);
        system.addView(select, systemButtonLp);
        system.addView(start, systemButtonLp);

        FrameLayout.LayoutParams systemLp = new FrameLayout.LayoutParams(
                dp(236), dp(54), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        systemLp.setMargins(0, 0, 0, dp(14));
        controlsLayer.addView(system, systemLp);
    }

    private View control(int icon, String description, int keyCode) {
        return new NativeControlButton(icon, description, keyCode);
    }

    private void addMenuButton() {
        View menu = new NativeControlButton(ICON_MENU, "Menu", KeyEvent.KEYCODE_UNKNOWN);
        menu.setOnClickListener(v -> showSidebar());

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(58), dp(58), Gravity.TOP | Gravity.RIGHT
        );
        lp.setMargins(0, dp(14), dp(16), 0);
        root.addView(menu, lp);
    }

    private void showSidebar() {
        if (sidebar != null) return;

        FrameLayout overlay = new FrameLayout(this);
        sidebar = overlay;

        View dim = new View(this);
        dim.setBackgroundColor(Color.argb(150, 0, 0, 0));
        dim.setOnClickListener(v -> closeSidebar());
        overlay.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(22), dp(18), dp(18));
        panel.setBackgroundColor(Color.rgb(5, 7, 13));

        TextView title = new TextView(this);
        title.setText("SameBoy");
        title.setTextSize(27);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(title);

        addSidebarButton(panel, "Continuar", v -> closeSidebar(), true);
        addSidebarButton(
                panel,
                controlsVisible ? "Ocultar controles" : "Mostrar controles",
                v -> {
                    controlsVisible = !controlsVisible;
                    if (controlsLayer != null) {
                        controlsLayer.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
                    }
                    closeSidebar();
                },
                false
        );
        addSidebarButton(
                panel,
                turboEnabled ? "Avanço rápido: Ligado" : "Avanço rápido: Desligado",
                v -> {
                    setTurbo(!turboEnabled);
                    closeSidebar();
                },
                false
        );
        addSidebarButton(panel, "Salvar estado", v -> {
            closeSidebar();
            saveQuickState();
        }, false);
        addSidebarButton(panel, "Carregar estado", v -> {
            closeSidebar();
            loadQuickState();
        }, false);
        addSidebarButton(
                panel,
                landscape ? "Mudar para vertical" : "Mudar para horizontal",
                v -> {
                    landscape = !landscape;
                    setRequestedOrientation(
                            landscape
                                    ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    );
                    closeSidebar();
                },
                false
        );
        addSidebarButton(panel, "Reiniciar jogo", v -> {
            closeSidebar();
            if (retroView != null && coreReady) {
                try {
                    retroView.reset(true);
                } catch (Throwable error) {
                    Toast.makeText(this, compactError(error), Toast.LENGTH_LONG).show();
                }
            }
        }, false);
        addSidebarButton(panel, "Biblioteca", v -> exitToLibrary(), false);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                dp(330), -1, Gravity.RIGHT
        );
        overlay.addView(panel, panelLp);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void addSidebarButton(
            LinearLayout panel,
            String text,
            View.OnClickListener listener,
            boolean primary
    ) {
        Button button = actionButton(text);
        if (primary) button.setBackgroundColor(Color.rgb(67, 86, 255));
        button.setOnClickListener(listener);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, dp(9), 0, 0);
        panel.addView(button, lp);
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setBackgroundColor(Color.rgb(13, 18, 31));
        return button;
    }

    private void closeSidebar() {
        if (sidebar != null && sidebar.getParent() == root) root.removeView(sidebar);
        sidebar = null;
    }

    private void setTurbo(boolean enabled) {
        turboEnabled = enabled;
        if (retroView == null) return;

        try {
            retroView.setFrameSpeed(enabled ? 3 : 1);
            retroView.setAudioEnabled(!enabled);
            Toast.makeText(
                    this,
                    enabled ? "Avanço rápido ligado" : "Velocidade normal",
                    Toast.LENGTH_SHORT
            ).show();
        } catch (Throwable error) {
            Toast.makeText(this, compactError(error), Toast.LENGTH_LONG).show();
        }
    }

    private void saveQuickState() {
        if (retroView == null || !coreReady || quickStateFile == null) {
            Toast.makeText(this, "Core ainda não está pronto.", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                byte[] state = retroView.serializeState(true);
                writeFile(quickStateFile, state);
                runOnUiThread(() -> Toast.makeText(
                        this, "Estado salvo", Toast.LENGTH_SHORT
                ).show());
            } catch (Throwable error) {
                runOnUiThread(() -> Toast.makeText(
                        this, compactError(error), Toast.LENGTH_LONG
                ).show());
            }
        }, "SameBoySaveState").start();
    }

    private void loadQuickState() {
        if (retroView == null || !coreReady || quickStateFile == null || !quickStateFile.isFile()) {
            Toast.makeText(this, "Nenhum estado salvo.", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                byte[] state = readFile(quickStateFile, 32 * 1024 * 1024);
                boolean loaded = retroView.unserializeState(state, true);
                runOnUiThread(() -> Toast.makeText(
                        this,
                        loaded ? "Estado carregado" : "O core recusou o estado",
                        Toast.LENGTH_SHORT
                ).show());
            } catch (Throwable error) {
                runOnUiThread(() -> Toast.makeText(
                        this, compactError(error), Toast.LENGTH_LONG
                ).show());
            }
        }, "SameBoyLoadState").start();
    }

    private void saveBatteryAsync() {
        if (!coreReady || retroView == null || saveRamFile == null) return;
        if (!saving.compareAndSet(false, true)) return;

        new Thread(() -> {
            try {
                byte[] sram = retroView.serializeSRAM(true);
                if (sram != null && sram.length > 0) writeFile(saveRamFile, sram);
            } catch (Throwable ignored) {
            } finally {
                saving.set(false);
            }
        }, "SameBoySaveRAM").start();
    }

    private void exitToLibrary() {
        closeSidebar();
        saveBatteryAsync();
        handler.postDelayed(this::finish, 180L);
    }

    private byte[] readRom(Uri uri, String sourceName) throws Exception {
        String lower = sourceName == null ? "" : sourceName.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".zip")) {
            try (
                    InputStream raw = getContentResolver().openInputStream(uri);
                    ZipInputStream zip = raw == null ? null : new ZipInputStream(raw)
            ) {
                if (zip == null) throw new IllegalStateException("Não foi possível abrir o ZIP.");

                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName() == null
                            ? ""
                            : entry.getName().toLowerCase(Locale.ROOT);
                    if (!entry.isDirectory() && (name.endsWith(".gb") || name.endsWith(".gbc"))) {
                        return readAll(zip, MAX_ROM_BYTES);
                    }
                }
            }
            throw new IllegalArgumentException("O ZIP não contém ROM .gb ou .gbc.");
        }

        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("Android não abriu o arquivo.");
            return readAll(input, MAX_ROM_BYTES);
        }
    }

    private byte[] readAll(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;

        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("Arquivo excede o limite de segurança.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private byte[] readFile(File file, int maxBytes) throws Exception {
        try (InputStream input = new FileInputStream(file)) {
            return readAll(input, maxBytes);
        }
    }

    private void writeFile(File file, byte[] data) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) ensureDirectory(parent);

        File temp = new File(file.getAbsolutePath() + ".tmp");
        try (OutputStream output = new FileOutputStream(temp)) {
            output.write(data);
            output.flush();
        }

        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Falha ao substituir save antigo.");
        }
        if (!temp.renameTo(file)) {
            throw new IllegalStateException("Falha ao concluir save.");
        }
    }

    private void ensureDirectory(File directory) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Falha ao criar " + directory.getName() + ".");
        }
    }

    private String buildSaveKey(String title, String uri) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest((title + "|" + uri).getBytes("UTF-8"));
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < 10 && i < hash.length; i++) {
            out.append(String.format(Locale.ROOT, "%02x", hash[i] & 0xff));
        }
        return out.toString();
    }

    private String saveErrorReport(Throwable error) {
        String file = "CompanionDeck-v1.3-sameboy-error-" + System.currentTimeMillis() + ".txt";
        String report =
                "Companion Deck v1.3-r1\n"
                        + "Core: SameBoy native/libretro\n"
                        + "ABI: " + Build.SUPPORTED_ABIS[0] + "\n"
                        + "ROM: " + romName + "\n"
                        + "Erro: " + error + "\n\n"
                        + android.util.Log.getStackTraceString(error);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, file);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri target = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                );
                if (target != null) {
                    try (OutputStream output = getContentResolver().openOutputStream(target)) {
                        if (output != null) output.write(report.getBytes("UTF-8"));
                    }
                    return "Download/" + file;
                }
            }

            File directory = getExternalFilesDir(null);
            if (directory == null) return "";
            File target = new File(directory, file);
            try (OutputStream output = new FileOutputStream(target)) {
                output.write(report.getBytes("UTF-8"));
            }
            return target.getAbsolutePath();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String compactError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = error.getClass().getSimpleName();
        }
        return message.length() > 220 ? message.substring(0, 220) : message;
    }

    private void applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onStart() {
        super.onStart();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        applyImmersiveMode();
    }

    @Override
    protected void onPause() {
        saveBatteryAsync();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
        super.onPause();
    }

    @Override
    protected void onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (sidebar != null) {
            closeSidebar();
            return;
        }
        exitToLibrary();
    }

    private final class NativeControlButton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF bounds = new RectF();
        private final int icon;
        private final int keyCode;
        private boolean pressed;

        NativeControlButton(int icon, String description, int keyCode) {
            super(SameBoyPlayerActivity.this);
            this.icon = icon;
            this.keyCode = keyCode;
            setClickable(true);
            setFocusable(true);
            setContentDescription(description);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float width = getWidth();
            float height = getHeight();
            float inset = dp(2);
            float radius = Math.min(width, height) * 0.28f;

            bounds.set(inset, inset, width - inset, height - inset);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(
                    !isEnabled()
                            ? Color.argb(145, 14, 19, 30)
                            : pressed
                            ? Color.rgb(68, 91, 255)
                            : Color.argb(225, 10, 15, 27)
            );
            canvas.drawRoundRect(bounds, radius, radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(
                    pressed
                            ? Color.argb(235, 220, 227, 255)
                            : Color.argb(95, 87, 126, 255)
            );
            canvas.drawRoundRect(bounds, radius, radius, paint);

            drawIcon(canvas, width / 2f, height / 2f, Math.min(width, height));
        }

        private void drawIcon(Canvas canvas, float cx, float cy, float size) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(247, 249, 255));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            if (icon >= ICON_UP && icon <= ICON_RIGHT) {
                float arrow = size * 0.23f;
                path.reset();

                if (icon == ICON_UP) {
                    path.moveTo(cx, cy - arrow);
                    path.lineTo(cx - arrow, cy + arrow * 0.72f);
                    path.lineTo(cx + arrow, cy + arrow * 0.72f);
                } else if (icon == ICON_DOWN) {
                    path.moveTo(cx, cy + arrow);
                    path.lineTo(cx - arrow, cy - arrow * 0.72f);
                    path.lineTo(cx + arrow, cy - arrow * 0.72f);
                } else if (icon == ICON_LEFT) {
                    path.moveTo(cx - arrow, cy);
                    path.lineTo(cx + arrow * 0.72f, cy - arrow);
                    path.lineTo(cx + arrow * 0.72f, cy + arrow);
                } else {
                    path.moveTo(cx + arrow, cy);
                    path.lineTo(cx - arrow * 0.72f, cy - arrow);
                    path.lineTo(cx - arrow * 0.72f, cy + arrow);
                }

                path.close();
                canvas.drawPath(path, paint);
                return;
            }

            if (icon == ICON_CENTER) {
                paint.setColor(Color.argb(175, 160, 175, 205));
                canvas.drawCircle(cx, cy, size * 0.115f, paint);
                return;
            }

            if (icon == ICON_MENU) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(dp(2), size * 0.055f));
                float half = size * 0.20f;
                canvas.drawLine(cx - half, cy - half * 0.72f, cx + half, cy - half * 0.72f, paint);
                canvas.drawLine(cx - half, cy, cx + half, cy, paint);
                canvas.drawLine(cx - half, cy + half * 0.72f, cx + half, cy + half * 0.72f, paint);
                return;
            }

            String label;
            if (icon == ICON_A) label = "A";
            else if (icon == ICON_B) label = "B";
            else if (icon == ICON_START) label = "START";
            else label = "SELECT";

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            paint.setTextSize(
                    icon == ICON_START || icon == ICON_SELECT
                            ? size * 0.22f
                            : size * 0.35f
            );
            Paint.FontMetrics metrics = paint.getFontMetrics();
            canvas.drawText(label, cx, cy - (metrics.ascent + metrics.descent) / 2f, paint);
            paint.setFakeBoldText(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!isEnabled()) return false;
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN) {
                pressed = true;
                setScaleX(0.92f);
                setScaleY(0.92f);

                if (keyCode != KeyEvent.KEYCODE_UNKNOWN && retroView != null) {
                    retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0);
                }

                if (hapticsEnabled) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                invalidate();
                return true;
            }

            if (
                    action == MotionEvent.ACTION_UP
                            || action == MotionEvent.ACTION_CANCEL
                            || action == MotionEvent.ACTION_OUTSIDE
            ) {
                if (keyCode != KeyEvent.KEYCODE_UNKNOWN && retroView != null) {
                    retroView.sendKeyEvent(KeyEvent.ACTION_UP, keyCode, 0);
                }

                pressed = false;
                setScaleX(1f);
                setScaleY(1f);
                invalidate();
                if (action == MotionEvent.ACTION_UP) performClick();
                return true;
            }

            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
