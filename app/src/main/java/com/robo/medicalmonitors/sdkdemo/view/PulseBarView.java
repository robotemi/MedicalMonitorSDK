package com.robo.medicalmonitors.sdkdemo.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.robo.medicalmonitors.sdkdemo.R;


/**
 * @Description
 * @Author 池志豪
 * @CreateTime 2025-08-06
 */

public class PulseBarView extends View {

    // 默认值
    private static final int DEFAULT_MIN_VALUE = 0;
    private static final int DEFAULT_MAX_VALUE = 15;
    private static final int DEFAULT_BAR_COLOR = Color.GREEN;
    private static final int DEFAULT_BACKGROUND_COLOR = Color.BLACK;
    private static final float DEFAULT_BAR_WIDTH = 8f; // dp
    private static final float WAVE_SPEED_MM_PER_SECOND = 25f; // 25mm/s

    // 画笔
    private Paint barPaint;
    private Paint backgroundPaint;

    // 数据
    private int currentValue; // 当前数值
    private int minValue;
    private int maxValue;

    // 样式属性
    private int barColor;
    private int backgroundColor;

    // 视图属性
    private float barWidth; // 柱子宽度（像素）
    private int viewWidth;
    private int viewHeight;

    public PulseBarView(Context context) {
        super(context);
        init(context, null);
    }

    public PulseBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public PulseBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        // 设置默认值
        minValue = DEFAULT_MIN_VALUE;
        maxValue = DEFAULT_MAX_VALUE;
        barColor = DEFAULT_BAR_COLOR;
        backgroundColor = DEFAULT_BACKGROUND_COLOR;
        barWidth = DEFAULT_BAR_WIDTH;

        // 解析自定义属性
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PulseBarView);
            try {
                minValue = a.getInt(R.styleable.PulseBarView_minValue, DEFAULT_MIN_VALUE);
                maxValue = a.getInt(R.styleable.PulseBarView_maxValue, DEFAULT_MAX_VALUE);
                barColor = a.getColor(R.styleable.PulseBarView_barColor, DEFAULT_BAR_COLOR);
                backgroundColor = a.getColor(R.styleable.PulseBarView_backgroundColor, DEFAULT_BACKGROUND_COLOR);
                barWidth = a.getDimension(R.styleable.PulseBarView_barWidth, DEFAULT_BAR_WIDTH);
            } finally {
                a.recycle();
            }
        }

        // 初始化数据
        currentValue = minValue;

        // 初始化画笔
        setupPaints();
    }

    private void setupPaints() {
        // 柱子画笔
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(barColor);
        barPaint.setStyle(Paint.Style.FILL);

        // 背景画笔
        backgroundPaint = new Paint();
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;

        // 单根柱子不需要计算最大数据点数量
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制背景
        canvas.drawRect(0, 0, viewWidth, viewHeight, backgroundPaint);

        // 绘制单根柱子
        drawSingleBar(canvas);
    }



    private void drawSingleBar(Canvas canvas) {
        // 计算柱子位置（居中显示）
        float barX = (viewWidth - barWidth) / 2f;

        // 计算填充高度
        float fillHeight = mapValueToHeight(currentValue);

        // 绘制填充部分（从底部向上）
        if (fillHeight > 0) {
            RectF fillRect = new RectF(
                    barX,
                    viewHeight - fillHeight,
                    barX + barWidth,
                    viewHeight
            );
            canvas.drawRect(fillRect, barPaint);
        }
    }

    private float mapValueToHeight(int value) {
        // 将数值映射到填充高度
        float normalizedValue = (float) (value - minValue) / (maxValue - minValue);
        return normalizedValue * (viewHeight - 10); // 留出边距
    }



    /**
     * 设置当前数值（手动输入）
     * @param value 数值，应在minValue和maxValue范围内
     */
    public void addDataPoint(int value) {
        // 限制数值范围
        value = Math.max(minValue, Math.min(maxValue, value));

        currentValue = value;

        // 触发重绘
        postInvalidate();
    }

    /**
     * 设置当前数值（自动生成，不影响手动输入状态）
     * @param value 数值，应在minValue和maxValue范围内
     */
    public void addAutoDataPoint(int value) {
        // 限制数值范围
        value = Math.max(minValue, Math.min(maxValue, value));

        currentValue = value;

        // 触发重绘
        postInvalidate();
    }

    /**
     * 重置数值
     */
    public void clearData() {
        currentValue = minValue;
        postInvalidate();
    }



    /**
     * 设置数值范围
     */
    public void setValueRange(int minValue, int maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        postInvalidate();
    }

    /**
     * 设置柱子颜色
     */
    public void setBarColor(int color) {
        this.barColor = color;
        barPaint.setColor(color);
        postInvalidate();
    }

    /**
     * 设置背景颜色
     */
    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
        backgroundPaint.setColor(color);
        postInvalidate();
    }

    /**
     * 设置柱子宽度
     */
    public void setBarWidth(float width) {
        this.barWidth = width;
        postInvalidate();
    }

    /**
     * 获取当前数值
     */
    public int getCurrentValue() {
        return currentValue;
    }
}
