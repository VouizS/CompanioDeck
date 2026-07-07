package com.sw.companiodeck;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
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
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CoffeeGbPlayerView extends View {
    private static final int GB_W = 160;
    private static final int GB_H = 144;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Object frameLock = new Object();
    private final int[] latestFrame = new int[GB_W * GB_H];
    private final Bitmap frameBitmap = Bitmap.createBitmap(GB_W, GB_H, Bitmap.Config.ARGB_8888);

    private volatile boolean running;
    private volatile boolean hasFrame;
    private volatile String status = "Carregando core GB/GBC...";
    private Thread emuThread;
    private EventBusImpl eventBus;
    private Gameboy gameboy;

    public CoffeeGbPlayerView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
    }

    public void loadGame(Uri uri, String title) {
        stopEmulation();
        status = "Preparando " + (title == null ? "jogo" : title) + "...";
        invalidate();

        new Thread(() -> {
            try {
                File romFile = copyRomToCache(uri);
                Gameboy.GameboyConfiguration config = new Gameboy.GameboyConfiguration(romFile)
                        .setBootstrapMode(Gameboy.BootstrapMode.SKIP);

                gameboy = config.build();
                eventBus = new EventBusImpl(null, null, false);

                eventBus.register(new Subscriber<Display.DmgFrameReadyEvent>() {
                    @Override
                    public void onEvent(Display.DmgFrameReadyEvent event) {
                        int[] rgb = new int[GB_W * GB_H];
                        event.toRgb(rgb, false);
                        pushFrame(rgb);
                    }
                }, Display.DmgFrameReadyEvent.class);

                eventBus.register(new Subscriber<Display.GbcFrameReadyEvent>() {
                    @Override
                    public void onEvent(Display.GbcFrameReadyEvent event) {
                        int[] rgb = new int[GB_W * GB_H];
                        event.toRgb(rgb);
                        pushFrame(rgb);
                    }
                }, Display.GbcFrameReadyEvent.class);

                gameboy.init(eventBus, SerialEndpoint.NULL_ENDPOINT, null);
                running = true;
                status = "";
                startLoop();
            } catch (Throwable t) {
                status = "Não foi possível iniciar. Use .gb/.gbc ou .zip com uma ROM GB/GBC.";
                postInvalidate();
            }
        }, "CoffeeGbLoader").start();
    }

    private File copyRomToCache(Uri uri) throws Exception {
        File outFile = new File(getContext().getCacheDir(), "companion_deck_gb_rom_" + System.currentTimeMillis() + ".gb");

        // Primeiro tenta tratar como .zip. Se existir .gb/.gbc dentro, extrai somente a ROM real.
        try (InputStream raw = getContext().getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(raw)) {
            if (raw != null) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName() == null ? "" : entry.getName().toLowerCase(Locale.ROOT);
                    if (!entry.isDirectory() && (name.endsWith(".gb") || name.endsWith(".gbc"))) {
                        try (FileOutputStream out = new FileOutputStream(outFile)) {
                            copyLimited(zip, out);
                        }
                        return outFile;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Arquivo provavelmente não é zip. Abaixo copiamos como ROM direta.
        }

        try (InputStream in = getContext().getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(outFile)) {
            if (in == null) throw new IllegalStateException("ROM indisponível");
            copyLimited(in, out);
        }
        return outFile;
    }

    private void copyLimited(InputStream in, FileOutputStream out) throws Exception {
        byte[] buffer = new byte[16384];
        int read;
        long total = 0;
        long limit = 16L * 1024L * 1024L;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IllegalStateException("ROM acima do limite inicial");
            out.write(buffer, 0, read);
        }
    }

    private void startLoop() {
        emuThread = new Thread(() -> {
            final int ticksPerFrame = Gameboy.TICKS_PER_FRAME;
            while (running && gameboy != null) {
                long start = System.nanoTime();
                try {
                    for (int i = 0; i < ticksPerFrame && running; i++) {
                        gameboy.tick();
                    }
                } catch (Throwable t) {
                    status = "Core pausado por erro interno.";
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
        synchronized (frameLock) {
            for (int i = 0; i < latestFrame.length && i < rgb.length; i++) {
                latestFrame[i] = 0xFF000000 | (rgb[i] & 0x00FFFFFF);
            }
            hasFrame = true;
        }
        postInvalidate();
    }

    public void setButtonPressed(Button button, boolean pressed) {
        EventBusImpl bus = eventBus;
        if (bus == null || button == null) return;
        if (pressed) bus.post(new ButtonPressEvent(button));
        else bus.post(new ButtonReleaseEvent(button));
    }

    public void stopEmulation() {
        running = false;
        try {
            if (gameboy != null) gameboy.close();
        } catch (Throwable ignored) {
        }
        try {
            if (eventBus != null) eventBus.close();
        } catch (Throwable ignored) {
        }
        gameboy = null;
        eventBus = null;
        emuThread = null;
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

            float scale = Math.min(getWidth() / (float) GB_W, getHeight() / (float) GB_H);
            float drawW = GB_W * scale;
            float drawH = GB_H * scale;
            float left = (getWidth() - drawW) / 2f;
            float top = (getHeight() - drawH) / 2f;
            canvas.drawBitmap(frameBitmap, null, new android.graphics.RectF(left, top, left + drawW, top + drawH), paint);
            return;
        }

        paint.setColor(Color.BLACK);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        paint.setColor(Color.argb(34, 46, 123, 255));
        canvas.drawCircle(getWidth() * 0.50f, getHeight() * 0.42f, Math.min(getWidth(), getHeight()) * 0.34f, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(24f, getWidth() * 0.052f));
        paint.setColor(Color.rgb(248, 250, 252));
        canvas.drawText("GB/GBC Core", getWidth() / 2f, getHeight() / 2f - 12f, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(Math.max(14f, getWidth() * 0.028f));
        paint.setColor(Color.rgb(172, 181, 197));
        canvas.drawText(status, getWidth() / 2f, getHeight() / 2f + 24f, paint);
    }
}
