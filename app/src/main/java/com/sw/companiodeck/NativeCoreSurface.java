package com.sw.companiodeck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.SurfaceView;

public class NativeCoreSurface extends SurfaceView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String title = "Native Runtime Slot";
    private String subtitle = "Surface pronta para render nativo/offline.";

    public NativeCoreSurface(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.BLACK);
    }

    public void setSlotText(String title, String subtitle) {
        this.title = title == null ? "Native Runtime Slot" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        paint.setColor(Color.rgb(3, 4, 3));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setColor(Color.argb(32, 163, 230, 53));
        canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) * 0.34f, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(26f, w * 0.052f));
        paint.setColor(Color.rgb(244, 247, 239));
        canvas.drawText(title, w / 2f, h / 2f - 12f, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(Math.max(15f, w * 0.026f));
        paint.setColor(Color.rgb(164, 173, 157));
        canvas.drawText(subtitle, w / 2f, h / 2f + 26f, paint);

        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(12f, w * 0.022f));
        paint.setColor(Color.rgb(217, 249, 157));
        canvas.drawText("NO WEB CORE • NATIVE FOUNDATION", w / 2f, h - 42f, paint);
        paint.setFakeBoldText(false);
    }
}
