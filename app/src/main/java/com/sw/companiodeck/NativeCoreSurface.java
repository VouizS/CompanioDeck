package com.sw.companiodeck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.SurfaceView;

public class NativeCoreSurface extends SurfaceView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String title = "Companion Deck";
    private String subtitle = "Player interno preparado.";

    public NativeCoreSurface(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.BLACK);
    }

    public void setSlotText(String title, String subtitle) {
        this.title = title == null ? "Companion Deck" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        paint.setColor(Color.BLACK);
        canvas.drawRect(0, 0, w, h, paint);

        paint.setColor(Color.argb(28, 46, 123, 255));
        canvas.drawCircle(w * 0.48f, h * 0.42f, Math.min(w, h) * 0.36f, paint);

        paint.setColor(Color.argb(24, 112, 77, 255));
        canvas.drawCircle(w * 0.62f, h * 0.56f, Math.min(w, h) * 0.28f, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(Math.max(28f, w * 0.058f));
        paint.setColor(Color.rgb(248, 250, 252));
        canvas.drawText(title, w / 2f, h / 2f - 8f, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(Math.max(15f, w * 0.028f));
        paint.setColor(Color.rgb(172, 181, 197));
        canvas.drawText(subtitle, w / 2f, h / 2f + 30f, paint);
    }
}
