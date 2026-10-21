package com.robo.medicalmonitors.sdkdemo.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import com.robo.medicalmonitors.sdkdemo.R;

import java.util.ArrayList;
import java.util.List;

public class PulseWaveView extends View {
    private static final int DEFAULT_MIN_VALUE = 0;
    private static final int DEFAULT_MAX_VALUE = 255;
    private static final int DEFAULT_LINE_COLOR = Color.GREEN;
    private static final int DEFAULT_GRID_COLOR = 0x33AAAAAA;
    private static final int DEFAULT_BACKGROUND_COLOR = Color.BLACK;
    private static final float DEFAULT_LINE_WIDTH = 1f;
    private static final boolean DEFAULT_SHOW_GRID = false;
    private static final int DEFAULT_FPS = 30;
    private static final float PIXELS_PER_MM = 6.3f;

    public enum Speed {
        SPEED_25(25f),
        SPEED_12_5(12.5f),
        SPEED_6_25(6.25f);

        private final float value;

        Speed(float value) {
            this.value = value;
        }

        public float getValue() {
            return value;
        }
    }

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();
    private final List<Long> markers = new ArrayList<>();

    private int[] dataPoints = new int[1];
    private int dataHead;
    private int dataTail;
    private int dataSize;
    private long totalDataCount;
    private int minValue = DEFAULT_MIN_VALUE;
    private int maxValue = DEFAULT_MAX_VALUE;
    private int lineColor = DEFAULT_LINE_COLOR;
    private int gridColor = DEFAULT_GRID_COLOR;
    private int backgroundColor = DEFAULT_BACKGROUND_COLOR;
    private float lineWidth = DEFAULT_LINE_WIDTH;
    private int viewWidth;
    private int viewHeight;
    private float pixelsPerMm;
    private float pixelsPerSecond;
    private int maxDataPoints = 1;
    private boolean showGrid = DEFAULT_SHOW_GRID;
    private Speed currentSpeed = Speed.SPEED_25;
    private int targetFps = DEFAULT_FPS;
    private long frameInterval = 1000L / DEFAULT_FPS;
    private long lastDrawTime;

    public PulseWaveView(Context context) {
        this(context, null);
    }

    public PulseWaveView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PulseWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray values = context.obtainStyledAttributes(attrs, R.styleable.PulseWaveView);
            try {
                minValue = values.getInt(R.styleable.PulseWaveView_minValue, DEFAULT_MIN_VALUE);
                maxValue = values.getInt(R.styleable.PulseWaveView_maxValue, DEFAULT_MAX_VALUE);
                lineColor = values.getColor(R.styleable.PulseWaveView_lineColor, DEFAULT_LINE_COLOR);
                gridColor = values.getColor(R.styleable.PulseWaveView_gridColor, DEFAULT_GRID_COLOR);
                backgroundColor = values.getColor(R.styleable.PulseWaveView_backgroundColor, DEFAULT_BACKGROUND_COLOR);
                lineWidth = values.getDimension(R.styleable.PulseWaveView_lineWidth, DEFAULT_LINE_WIDTH);
                showGrid = values.getBoolean(R.styleable.PulseWaveView_showGrid, DEFAULT_SHOW_GRID);
            } finally {
                values.recycle();
            }
        }

        pixelsPerMm = getResources().getDisplayMetrics().density * PIXELS_PER_MM;
        pixelsPerSecond = currentSpeed.getValue() * pixelsPerMm;
        linePaint.setColor(lineColor);
        linePaint.setStrokeWidth(lineWidth);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setColor(Color.RED);
        markerPaint.setStrokeWidth(2f);
        markerPaint.setStyle(Paint.Style.STROKE);
        setBackgroundColor(backgroundColor);
        setWillNotDraw(false);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        viewWidth = width;
        viewHeight = height;
        synchronized (this) {
            resizeDataBuffer();
        }
        invalidate();
    }

    private void resizeDataBuffer() {
        float pointSpacing = pixelsPerSecond / targetFps;
        maxDataPoints = Math.max(1, (int) (viewWidth / pointSpacing) + 20);
        dataPoints = new int[maxDataPoints];
        dataHead = 0;
        dataTail = 0;
        dataSize = 0;
        totalDataCount = 0;
        markers.clear();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawWaveform(canvas);
    }

    private void drawGrid(Canvas canvas) {
        int gridLines = 5;
        for (int i = 0; i <= gridLines; i++) {
            float y = (float) i / gridLines * viewHeight;
            canvas.drawLine(0, y, viewWidth, y, gridPaint);
        }
        for (float x = 0; x < viewWidth; x += pixelsPerSecond) {
            canvas.drawLine(x, 0, x, viewHeight, gridPaint);
        }
    }

    private void drawWaveform(Canvas canvas) {
        synchronized (this) {
            canvas.drawColor(backgroundColor);
            if (showGrid) {
                drawGrid(canvas);
            }
            if (dataSize > 1 && maxDataPoints > 0 && maxValue > minValue) {
                wavePath.reset();
                float pointSpacing = pixelsPerSecond / targetFps;
                float x = viewWidth;
                for (int i = 0; i < dataSize && x >= 0; i++) {
                    int index = (dataTail - 1 - i + maxDataPoints) % maxDataPoints;
                    float y = mapValueToY(dataPoints[index]);
                    if (i == 0) {
                        wavePath.moveTo(x, y);
                    } else {
                        wavePath.lineTo(x, y);
                    }
                    long pointId = totalDataCount - 1 - i;
                    if (markers.contains(pointId)) {
                        canvas.drawLine(x, 0, x, viewHeight, markerPaint);
                    }
                    x -= pointSpacing;
                }
                canvas.drawPath(wavePath, linePaint);
            }
            drawOverlay(canvas);
        }
    }

    private float mapValueToY(int value) {
        float normalized = (float) (value - minValue) / (maxValue - minValue);
        return viewHeight - normalized * viewHeight;
    }

    public void addDataPoint(int value) {
        synchronized (this) {
            long now = SystemClock.uptimeMillis();
            if (lastDrawTime > 0 && now - lastDrawTime < frameInterval) return;
            lastDrawTime = now;
            if (maxDataPoints <= 0 || dataPoints.length == 0) return;
            dataPoints[dataTail] = value;
            dataTail = (dataTail + 1) % maxDataPoints;
            totalDataCount++;
            if (dataSize < maxDataPoints) {
                dataSize++;
            } else {
                dataHead = (dataHead + 1) % maxDataPoints;
            }
        }
        postInvalidateOnAnimation();
    }

    public void addMarker() {
        synchronized (this) {
            if (dataSize > 0) {
                markers.add(totalDataCount - 1);
            }
        }
        postInvalidateOnAnimation();
    }

    public void clearData() {
        synchronized (this) {
            dataHead = 0;
            dataTail = 0;
            dataSize = 0;
            totalDataCount = 0;
            lastDrawTime = 0;
            markers.clear();
        }
        postInvalidateOnAnimation();
    }

    public void resetToAutoMode() {
        invalidate();
    }

    public void setValueRange(int minValue, int maxValue) {
        if (maxValue <= minValue) throw new IllegalArgumentException("maxValue must be greater than minValue");
        this.minValue = minValue;
        this.maxValue = maxValue;
        invalidate();
    }

    public void setLineColor(int color) {
        lineColor = color;
        linePaint.setColor(color);
        invalidate();
    }

    public void setGridColor(int color) {
        gridColor = color;
        gridPaint.setColor(color);
        invalidate();
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

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
        invalidate();
    }

    public void setSpeed(Speed speed) {
        if (speed == null || speed == currentSpeed) return;
        currentSpeed = speed;
        pixelsPerSecond = currentSpeed.getValue() * pixelsPerMm;
        synchronized (this) {
            resizeDataBuffer();
            lastDrawTime = 0;
        }
        invalidate();
    }

    public Speed getSpeed() {
        return currentSpeed;
    }

    public void toggleSpeed() {
        setSpeed(currentSpeed == Speed.SPEED_25 ? Speed.SPEED_12_5 : Speed.SPEED_25);
    }

    public Speed[] getAvailableSpeeds() {
        return Speed.values();
    }

    public String getCurrentSpeedText() {
        return currentSpeed.getValue() + "mm/s";
    }

    public void setFrameRate(int fps) {
        if (fps <= 0 || fps == targetFps) return;
        targetFps = fps;
        updateFrameInterval();
        invalidate();
    }

    public int getFrameRate() {
        return targetFps;
    }

    private void updateFrameInterval() {
        frameInterval = Math.max(1, 1000L / targetFps);
    }

    protected void drawOverlay(Canvas canvas) {
    }

    protected void resetOverlay() {
    }

    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public int getLineColor() {
        return lineColor;
    }

    public int getGridColor() {
        return gridColor;
    }

    public float getLineWidth() {
        return lineWidth;
    }

    public boolean isShowGrid() {
        return showGrid;
    }
}
