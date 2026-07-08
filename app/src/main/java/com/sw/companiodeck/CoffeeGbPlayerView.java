package com.sw.companiodeck;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;

import eu.rekawek.coffeegb.core.Gameboy;
import eu.rekawek.coffeegb.core.events.EventBusImpl;
import eu.rekawek.coffeegb.core.events.Subscriber;
import eu.rekawek.coffeegb.core.gpu.Display;
import eu.rekawek.coffeegb.core.joypad.Button;
import eu.rekawek.coffeegb.core.joypad.ButtonPressEvent;
import eu.rekawek.coffeegb.core.joypad.ButtonReleaseEvent;
import eu.rekawek.coffeegb.core.serial.SerialEndpoint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

public class CoffeeGbPlayerView extends View {
    private static final int GB_W = 160;
    private static final int GB_H = 144;
    private static final long MAX_ROM_BYTES = 16L * 1024L * 1024L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Object frameLock = new Object();
    private final int[] latestFrame = new int[GB_W * GB_H];
    private final Bitmap frameBitmap = Bitmap.createBitmap(GB_W, GB_H, Bitmap.Config.ARGB_8888);

    private volatile boolean running;
    private volatile boolean hasFrame;
    private volatile String status = "Carregando core GB/GBC...";
    private volatile String sourceInfo = "";

    private Runnable onFirstFrameListener;
    private Thread emuThread;
    private EventBusImpl eventBus;
    private Gameboy gameboy;

    public CoffeeGbPlayerView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
    }

    public void setOnFirstFrameListener(Runnable listener) {
        this.onFirstFrameListener = listener;
    }

    public void loadGame(Uri uri, String title) {
        stopEmulation();
        hasFrame = false;
        sourceInfo = "";
        status = "Preparando " + (title == null ? "jogo" : title) + "...";
        invalidate();

        Thread loader = new Thread(() -> {
            try {
                File romFile = copyRomToCache(uri);
                sourceInfo = romFile.getName() + " • " + Math.max(1L, romFile.length() / 1024L) + " KB";
                status = "Iniciando Coffee GB...";
                postInvalidate();

                Gameboy.GameboyConfiguration config = new Gameboy.GameboyConfiguration(romFile)
                        .setBootstrapMode(Gameboy.BootstrapMode.SKIP);

                Gameboy builtGameboy = config.build();
                EventBusImpl builtBus = new EventBusImpl(null, null, false);

                builtBus.register(new Subscriber<Display.DmgFrameReadyEvent>() {
                    @Override
                    public void onEvent(Display.DmgFrameReadyEvent event) {
                        int[] rgb = new int[GB_W * GB_H];
                        event.toRgb(rgb, false);
                        pushFrame(rgb);
                    }
                }, Display.DmgFrameReadyEvent.class);

                builtBus.register(new Subscriber<Display.GbcFrameReadyEvent>() {
                    @Override
                    public void onEvent(Display.GbcFrameReadyEvent event) {
                        int[] rgb = new int[GB_W * GB_H];
                        event.toRgb(rgb);
                        pushFrame(rgb);
                    }
                }, Display.GbcFrameReadyEvent.class);

                gameboy = builtGameboy;
                eventBus = builtBus;
                gameboy.init(eventBus, SerialEndpoint.NULL_ENDPOINT, null);
                running = true;
                status = "Aguardando primeiro frame real...";
                postInvalidate();
                startLoop();
            } catch (Throwable t) {
                running = false;
                String reportPath = saveCoreErrorReport("loader", t);
                status = "Falha real do core: " + compactError(t) + (reportPath.isEmpty() ? "" : "\nLog salvo: " + reportPath);
                postInvalidate();
            }
        }, "CoffeeGbLoader");

        loader.start();
    }

    private File copyRomToCache(Uri uri) throws Exception {
        if (uri == null) {
            throw new IllegalStateException("URI da ROM indisponível");
        }

        boolean sawZipEntry = false;

        try (InputStream raw = getContext().getContentResolver().openInputStream(uri);
             ZipInputStream zip = raw == null ? null : new ZipInputStream(raw)) {
            if (zip != null) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    sawZipEntry = true;
                    String name = entry.getName() == null ? "" : entry.getName().toLowerCase(Locale.ROOT);
                    if (!entry.isDirectory() && (name.endsWith(".gb") || name.endsWith(".gbc"))) {
                        String suffix = name.endsWith(".gbc") ? ".gbc" : ".gb";
                        File outFile = new File(getContext().getCacheDir(), "companion_deck_rom_" + System.currentTimeMillis() + suffix);
                        try (FileOutputStream out = new FileOutputStream(outFile)) {
                            copyLimited(zip, out);
                        }
                        return outFile;
                    }
                }
            }
        } catch (ZipException ignored) {
            sawZipEntry = false;
        }

        if (sawZipEntry) {
            throw new IllegalStateException("ZIP sem arquivo .gb/.gbc dentro");
        }

        File outFile = new File(getContext().getCacheDir(), "companion_deck_rom_" + System.currentTimeMillis() + ".gb");
        try (InputStream in = getContext().getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(outFile)) {
            if (in == null) {
                throw new IllegalStateException("ROM indisponível");
            }
            copyLimited(in, out);
        }
        return outFile;
    }

    private void copyLimited(InputStream in, FileOutputStream out) throws Exception {
        byte[] buffer = new byte[16384];
        int read;
        long total = 0;

        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_ROM_BYTES) {
                throw new IllegalStateException("ROM acima do limite inicial de 16 MB");
            }
            out.write(buffer, 0, read);
        }

        if (total <= 0) {
            throw new IllegalStateException("Arquivo de ROM vazio");
        }
    }

    private void startLoop() {
        emuThread = new Thread(() -> {
            final int ticksPerFrame = Gameboy.TICKS_PER_FRAME;

            while (running && gameboy != null) {
                long start = System.nanoTime();

                try {
                    for (int i = 0; i < ticksPerFrame && running && gameboy != null; i++) {
                        gameboy.tick();
                    }
                } catch (Throwable t) {
                    String reportPath = saveCoreErrorReport("core-loop", t);
                    status = "Core pausado: " + compactError(t) + (reportPath.isEmpty() ? "" : "\nLog salvo: " + reportPath);
                    running = false;
                    postInvalidate();
                    break;
                }

                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                long sleep = 16L - elapsedMs;

                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }, "CoffeeGbCoreLoop");

        emuThread.start();
    }

    private void pushFrame(int[] rgb) {
        boolean firstFrame;

        synchronized (frameLock) {
            firstFrame = !hasFrame;
            for (int i = 0; i < latestFrame.length && i < rgb.length; i++) {
                latestFrame[i] = 0xFF000000 | (rgb[i] & 0x00FFFFFF);
            }
            hasFrame = true;
            status = "";
        }

        if (firstFrame && onFirstFrameListener != null) {
            try {
                onFirstFrameListener.run();
            } catch (Throwable ignored) {
            }
        }

        postInvalidate();
    }

    public void setButtonPressed(Button button, boolean pressed) {
        EventBusImpl bus = eventBus;
        if (bus == null || button == null || !hasFrame) return;

        if (pressed) {
            bus.post(new ButtonPressEvent(button));
        } else {
            bus.post(new ButtonReleaseEvent(button));
        }
    }

    public void stopEmulation() {
        running = false;

        Gameboy gb = gameboy;
        EventBusImpl bus = eventBus;
        gameboy = null;
        eventBus = null;
        onFirstFrameListener = null;

        try {
            if (gb != null) gb.close();
        } catch (Throwable ignored) {
        }

        try {
            if (bus != null) bus.close();
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopEmulation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (hasFrame) {
            synchronized (frameLock) {
                frameBitmap.setPixels(latestFrame, 0, GB_W, 0, 0, GB_W, GB_H);
            }

            paint.setFilterBitmap(false);
            float scale = Math.max(1f, (float) Math.floor(Math.min(getWidth() / (float) GB_W, getHeight() / (float) GB_H)));
            float drawW = GB_W * scale;
            float drawH = GB_H * scale;
            float left = (getWidth() - drawW) / 2f;
            float top = (getHeight() - drawH) / 2f;

            canvas.drawBitmap(frameBitmap, null, new android.graphics.RectF(left, top, left + drawW, top + drawH), paint);
            return;
        }

        drawLoading(canvas);
    }

    private void drawLoading(Canvas canvas) {
        paint.setFilterBitmap(true);
        paint.setColor(Color.BLACK);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        paint.setColor(Color.argb(34, 46, 123, 255));
        canvas.drawCircle(getWidth() * 0.50f, getHeight() * 0.42f, Math.min(getWidth(), getHeight()) * 0.34f, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(24f, getWidth() * 0.052f));
        paint.setColor(Color.rgb(248, 250, 252));
        canvas.drawText("GB/GBC Core Real", getWidth() / 2f, getHeight() / 2f - 28f, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(Math.max(12f, getWidth() * 0.026f));
        paint.setColor(Color.rgb(172, 181, 197));
        drawCenteredMultiline(canvas, status, getWidth() / 2f, getHeight() / 2f + 8f, getWidth() - 48);

        if (sourceInfo != null && !sourceInfo.isEmpty()) {
            paint.setTextSize(Math.max(10f, getWidth() * 0.022f));
            paint.setColor(Color.rgb(112, 126, 150));
            drawCenteredMultiline(canvas, sourceInfo, getWidth() / 2f, getHeight() - 56f, getWidth() - 48);
        }
    }

    private void drawCenteredMultiline(Canvas canvas, String text, float centerX, float startY, int maxWidth) {
        if (text == null) return;

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float y = startY;

        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > maxWidth && line.length() > 0) {
                canvas.drawText(line.toString(), centerX, y, paint);
                line = new StringBuilder(word);
                y += paint.getTextSize() * 1.35f;
            } else {
                line = new StringBuilder(candidate);
            }
        }

        if (line.length() > 0) {
            canvas.drawText(line.toString(), centerX, y, paint);
        }
    }

    private String saveCoreErrorReport(String phase, Throwable t) {
        String fileName = "CompanionDeck-core-error-" + System.currentTimeMillis() + ".txt";

        StringWriter stack = new StringWriter();
        if (t != null) {
            t.printStackTrace(new PrintWriter(stack));
        }

        String body = "Companion Deck Core Error\n"
                + "Version: v1.1-r9\n"
                + "Phase: " + phase + "\n"
                + "Source: " + (sourceInfo == null ? "" : sourceInfo) + "\n"
                + "Error: " + compactError(t) + "\n\n"
                + stack.toString();

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CompanionDeck");

                Uri outUri = getContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (outUri != null) {
                    try (OutputStream out = getContext().getContentResolver().openOutputStream(outUri)) {
                        if (out != null) {
                            out.write(bytes);
                            out.flush();
                            return "Download/CompanionDeck/" + fileName;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            File dir = new File(getContext().getExternalFilesDir(null), "logs");
            if (!dir.exists()) dir.mkdirs();
            File outFile = new File(dir, fileName);
            try (FileOutputStream out = new FileOutputStream(outFile)) {
                out.write(bytes);
                out.flush();
            }
            return outFile.getAbsolutePath();
        } catch (Throwable ignored) {
        }

        return "";
    }

    private String compactError(Throwable t) {
        if (t == null) return "erro desconhecido";

        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String message = t.getMessage();
        String main = (message == null || message.trim().isEmpty())
                ? t.getClass().getSimpleName()
                : t.getClass().getSimpleName() + ": " + message;

        if (root != t) {
            String rootMessage = root.getMessage();
            String rootText = (rootMessage == null || rootMessage.trim().isEmpty())
                    ? root.getClass().getSimpleName()
                    : root.getClass().getSimpleName() + ": " + rootMessage;
            return main + " | causa: " + rootText;
        }

        return main;
    }
}
