package com.robo.medicalmonitors.sdkdemo.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class ECGWaveView extends View {
    public enum Gain {
        GAIN_0_25(0.25f, "0.25"),
        GAIN_0_5(0.5f, "0.5"),
        GAIN_1(1.0f, "1"),
        GAIN_2(2.0f, "2");

        private final float value;
        private final String displayText;

        Gain(float value, String displayText) {
            this.value = value;
            this.displayText = displayText;
        }

        public float getValue() {
            return value;
        }

        public String getDisplayText() {
            return displayText;
        }
    }

    private static final int DATA_CAPACITY = 4096;
    private static final int TARGET_FPS = 30;
    private static final float PIXELS_PER_MM = 6.3f;
    private static final float DATA_UNITS_PER_MV = 410f;
    private static final float MV_SCALE_HEIGHT_MM = 10f;
    private static final float CUSTOM_TEXT_MARGIN = 16f;

    private final Object dataLock = new Object();
    private final int[] dataPoints = new int[DATA_CAPACITY];
    private final Path wavePath = new Path();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mvScalePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mvTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint customTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int nextDataPoint;
    private int dataSize;
    private long lastAcceptedTime;
    private int minValue;
    private int maxValue = 4096;
    private int lineColor = Color.GREEN;
    private int gridColor = 0x33AAAAAA;
    private int backgroundColor = Color.BLACK;
    private float lineWidth = 1f;
    private float pixelsPerSecond;
    private long frameInterval = 1000L / TARGET_FPS;
    private boolean showGrid;
    private Gain currentGain = Gain.GAIN_1;
    private String customText = "";
    private boolean showCustomText;
    private PulseWaveView.Speed speed = PulseWaveView.Speed.SPEED_25;

    public ECGWaveView(Context context) {
        this(context, null);
    }

    public ECGWaveView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ECGWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        pixelsPerSecond = speed.getValue() * density * PIXELS_PER_MM;

        linePaint.setColor(lineColor);
        linePaint.setStrokeWidth(lineWidth);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);

        mvScalePaint.setColor(Color.GREEN);
        mvScalePaint.setStrokeWidth(2f);
        mvScalePaint.setStyle(Paint.Style.STROKE);

        mvTextPaint.setColor(Color.GREEN);
        mvTextPaint.setTextSize(20f);

        customTextPaint.setColor(Color.GREEN);
        customTextPaint.setTextSize(32f);
        customTextPaint.setStyle(Paint.Style.FILL);
        setBackgroundColor(backgroundColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(backgroundColor);
        if (showGrid) {
            drawGrid(canvas);
        }
        drawWaveform(canvas);
        drawMvScale(canvas);
        drawCustomText(canvas);
    }

    private void drawGrid(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        for (int i = 0; i <= 5; i++) {
            float y = height * i / 5f;
            canvas.drawLine(0, y, width, y, gridPaint);
        }
        for (float x = 0; x < width; x += pixelsPerSecond) {
            canvas.drawLine(x, 0, x, height, gridPaint);
        }
    }

    private void drawWaveform(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || maxValue <= minValue) return;

        synchronized (dataLock) {
            if (dataSize < 2) return;
            wavePath.reset();
            float x = width;
            float pointSpacing = pixelsPerSecond / TARGET_FPS;
            for (int i = 0; i < dataSize && x >= 0; i++) {
                int index = (nextDataPoint - 1 - i + DATA_CAPACITY) % DATA_CAPACITY;
                float y = height - ((dataPoints[index] - minValue) / (float) (maxValue - minValue)) * height;
                if (i == 0) {
                    wavePath.moveTo(x, y);
                } else {
                    wavePath.lineTo(x, y);
                }
                x -= pointSpacing;
            }
            canvas.drawPath(wavePath, linePaint);
        }
    }

    private void drawMvScale(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || maxValue <= 0) return;

        float oneMvHeight = DATA_UNITS_PER_MV * (height / (maxValue * 2f));
        float actualHeight = oneMvHeight * currentGain.getValue() * 2f;
        float x = width - 60f;
        float startY = height / 2f + actualHeight / 2f;
        float endY = height / 2f - actualHeight / 2f;
        if (endY < 20f) {
            endY = 20f;
            startY = endY + actualHeight;
        }
        if (startY > height - 20f) {
            startY = height - 20f;
            endY = startY - actualHeight;
        }

        canvas.drawLine(x, startY, x, endY, mvScalePaint);
        float markLength = 8f;
        canvas.drawLine(x - markLength / 2f, startY, x + markLength / 2f, startY, mvScalePaint);
        canvas.drawLine(x - markLength / 2f, endY, x + markLength / 2f, endY, mvScalePaint);
        float midY = (startY + endY) / 2f;
        canvas.drawLine(x - markLength / 4f, midY, x + markLength / 4f, midY, mvScalePaint);
        canvas.drawText("1mV", x - 45f, startY + 20f, mvTextPaint);
    }

    private void drawCustomText(Canvas canvas) {
        if (!showCustomText || customText.isEmpty()) return;
        canvas.drawText(customText, CUSTOM_TEXT_MARGIN,
                CUSTOM_TEXT_MARGIN + customTextPaint.getTextSize(), customTextPaint);
    }

    public void addECGDataPoint(int value) {
        long now = android.os.SystemClock.uptimeMillis();
        synchronized (dataLock) {
            if (lastAcceptedTime > 0 && now - lastAcceptedTime < frameInterval) return;
            lastAcceptedTime = now;
            dataPoints[nextDataPoint] = value;
            nextDataPoint = (nextDataPoint + 1) % DATA_CAPACITY;
            dataSize = Math.min(dataSize + 1, DATA_CAPACITY);
        }
        postInvalidateOnAnimation();
    }

    public void clearData() {
        synchronized (dataLock) {
            nextDataPoint = 0;
            dataSize = 0;
            lastAcceptedTime = 0;
        }
        postInvalidateOnAnimation();
    }

    public void setValueRange(int minValue, int maxValue) {
        if (maxValue <= minValue) throw new IllegalArgumentException("maxValue must be greater than minValue");
        this.minValue = minValue;
        this.maxValue = maxValue;
        invalidate();
    }

    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public void setLineColor(int color) {
        lineColor = color;
        linePaint.setColor(color);
        invalidate();
    }

    public int getLineColor() {
        return lineColor;
    }

    public void setGridColor(int color) {
        gridColor = color;
        gridPaint.setColor(color);
        invalidate();
    }

    public int getGridColor() {
        return gridColor;
    }

    @Override
    public void setBackgroundColor(int color) {
        backgroundColor = color;
        super.setBackgroundColor(color);
        invalidate();
    }

    public void setLineWidth(float width) {
        lineWidth = width;
        linePaint.setStrokeWidth(width);
        invalidate();
    }

    public float getLineWidth() {
        return lineWidth;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
        invalidate();
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setSpeed(PulseWaveView.Speed speed) {
        if (speed == null || speed == this.speed) return;
        this.speed = speed;
        pixelsPerSecond = speed.getValue() * getResources().getDisplayMetrics().density * PIXELS_PER_MM;
        invalidate();
    }

    public PulseWaveView.Speed getSpeed() {
        return speed;
    }

    public void setGain(Gain gain) {
        if (gain != null) {
            currentGain = gain;
            invalidate();
        }
    }

    public void setGain(float gain) {
        for (Gain item : Gain.values()) {
            if (Math.abs(item.getValue() - gain) < 0.001f) {
                setGain(item);
                return;
            }
        }
    }

    public Gain getGain() {
        return currentGain;
    }

    public float getGainValue() {
        return currentGain.getValue();
    }

    public void nextGain() {
        Gain[] gains = Gain.values();
        setGain(gains[(currentGain.ordinal() + 1) % gains.length]);
    }

    public Gain[] getAvailableGains() {
        return Gain.values();
    }

    public String getCurrentGainText() {
        return "×" + currentGain.getDisplayText();
    }

    public void setMvScaleColor(int color) {
        mvScalePaint.setColor(color);
        mvTextPaint.setColor(color);
        invalidate();
    }

    public void setMvTextSize(float textSize) {
        mvTextPaint.setTextSize(textSize);
        invalidate();
    }

    public void setCustomText(String text) {
        customText = text == null ? "" : text;
        invalidate();
    }

    public String getCustomText() {
        return customText;
    }

    public void setShowCustomText(boolean show) {
        showCustomText = show;
        invalidate();
    }

    public boolean isShowCustomText() {
        return showCustomText;
    }

    public void setCustomTextColor(int color) {
        customTextPaint.setColor(color);
        invalidate();
    }

    public int getCustomTextColor() {
        return customTextPaint.getColor();
    }

    public void setCustomTextSize(float textSize) {
        customTextPaint.setTextSize(textSize);
        invalidate();
    }

    public float getCustomTextSize() {
        return customTextPaint.getTextSize();
    }
}
