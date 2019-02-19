/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zxing.view;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;

import com.github.jeterlee.zscan.R;
import com.google.zxing.ResultPoint;
import com.zxing.camera.CameraManager;

import java.util.Collection;
import java.util.HashSet;

/**
 * <pre>
 * Title: ViewfinderView
 * Description: 自定义组件实现，扫描功能。该视图是覆盖在相机的预览视图之上的一层视图。
 * 扫描区构成原理，其实是在预览视图上画四块遮罩层，中间留下的部分保持透明，并画上一条激光线，
 * 实际上该线条就是展示而已，与扫描功能没有任何关系。
 * </pre>
 *
 * @author <a href="https://www.github.com/jeterlee"></a>
 * @date 2019/2/15 0015
 */
public final class ViewfinderView extends View {
    /**
     * 刷新界面的时间
     */
    private static final long ANIMATION_DELAY = 100L;
    private static final int OPAQUE = 0xFF;
    /**
     * 画笔对象的引用
     */
    private final Paint paint;
    private final Paint tradeMarkPaint;
    private Bitmap resultBitmap;
    /**
     * 颜色的定义
     */
    private final int maskColor;
    private final int resultColor;
    private final int resultPointColor;

    private Collection<ResultPoint> possibleResultPoints;
    private Collection<ResultPoint> lastPossibleResultPoints;

    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        tradeMarkPaint = new Paint();
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.viewfinder_mask);
        resultColor = resources.getColor(R.color.result_view);
        resultPointColor = resources.getColor(R.color.possible_result_points);
        possibleResultPoints = new HashSet<>(5);
        scanLight = BitmapFactory.decodeResource(resources, R.drawable.zscan_light);
        initInnerRect(context, attrs);
    }

    /**
     * 初始化内部框的大小
     *
     * @param context 上下文
     * @param attrs   属性
     */
    private void initInnerRect(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.innerrect);
        // 扫描框距离顶部
        float innerMarginTop = ta.getDimension(R.styleable.innerrect_inner_marginTop, -1);
        if (innerMarginTop != -1) {
            CameraManager.FRAME_MARGINTOP = (int) innerMarginTop;
        }
        // 扫描框的宽度 & 高度
        CameraManager.FRAME_WIDTH = (int) ta.getDimension(R.styleable.innerrect_inner_width, getScreenWidth(context) / 2);
        CameraManager.FRAME_HEIGHT = (int) ta.getDimension(R.styleable.innerrect_inner_height, getScreenWidth(context) / 2);
        // 扫描框边角颜色 & 边角长度 & 边角宽度
        innerCornerColor = ta.getColor(R.styleable.innerrect_inner_corner_color, Color.parseColor("#45DDDD"));
        innerCornerLength = (int) ta.getDimension(R.styleable.innerrect_inner_corner_length, 65);
        innerCornerWidth = (int) ta.getDimension(R.styleable.innerrect_inner_corner_width, 15);
        // 扫描框 bitmap
        Drawable drawable = ta.getDrawable(R.styleable.innerrect_inner_scan_bitmap);
        if (drawable != null) {
        }
        // 扫描控件
        scanLight = BitmapFactory.decodeResource(getResources(),
                ta.getResourceId(R.styleable.innerrect_inner_scan_bitmap, R.drawable.zscan_light));
        // 扫描速度
        scanVelocity = ta.getInt(R.styleable.innerrect_inner_scan_speed, 5);
        // 是否展示小圆点
        isCircle = ta.getBoolean(R.styleable.innerrect_inner_scan_isCircle, true);
        // 扫描框的文字标记
        textTradeMark = ta.getString(R.styleable.innerrect_inner_scan_textTradeMark);
        ta.recycle();
    }

    @Override
    public void onDraw(Canvas canvas) {
        Rect frame = CameraManager.get().getFramingRect();
        if (frame == null) {
            return;
        }
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw the exterior (i.e. outside the framing rect) darkened
        paint.setColor(resultBitmap != null ? resultColor : maskColor);
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);

        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(OPAQUE);
            canvas.drawBitmap(resultBitmap, frame.left, frame.top, paint);
        } else {
            drawFrameBounds(canvas, frame);
            drawScanLight(canvas, frame);
            drawTradeMark(canvas, frame);

            Collection<ResultPoint> currentPossible = possibleResultPoints;
            Collection<ResultPoint> currentLast = lastPossibleResultPoints;
            if (currentPossible.isEmpty()) {
                lastPossibleResultPoints = null;
            } else {
                // possibleResultPoints = new HashSet<>(5);
                lastPossibleResultPoints = currentPossible;
                paint.setAlpha(OPAQUE);
                paint.setColor(resultPointColor);

                if (isCircle) {
                    for (ResultPoint point : currentPossible) {
                        canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 6.0f, paint);
                    }
                }
            }
            if (currentLast != null) {
                paint.setAlpha(OPAQUE / 2);
                paint.setColor(resultPointColor);

                if (isCircle) {
                    for (ResultPoint point : currentLast) {
                        canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 3.0f, paint);
                    }
                }
            }

            postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top, frame.right, frame.bottom);
        }
    }

    /**
     * 扫描线移动的 y
     */
    private int scanLineTop;
    /**
     * 扫描线移动速度
     */
    private int scanVelocity;
    /**
     * 扫描线
     */
    private Bitmap scanLight;
    /**
     * 是否展示小圆点
     */
    private boolean isCircle;

    /**
     * 绘制移动扫描线
     *
     * @param canvas canvas
     * @param frame  扫描矩形框
     */
    private void drawScanLight(Canvas canvas, Rect frame) {
        if (scanLineTop == 0) {
            scanLineTop = frame.top;
        }

        if (scanLineTop >= frame.bottom - 30) {
            scanLineTop = frame.top;
        } else {
            scanLineTop += scanVelocity;
        }
        Rect scanRect = new Rect(frame.left, scanLineTop, frame.right, scanLineTop + 30);
        canvas.drawBitmap(scanLight, null, scanRect, paint);
    }

    /**
     * 扫描框边角颜色
     */
    private int innerCornerColor;
    /**
     * 扫描框边角长度
     */
    private int innerCornerLength;
    /**
     * 扫描框边角宽度
     */
    private int innerCornerWidth;

    /**
     * 绘制取景框边框
     *
     * @param canvas canvas
     * @param frame  扫描矩形框
     */
    private void drawFrameBounds(Canvas canvas, Rect frame) {
        /*paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(frame, paint);*/

        paint.setColor(innerCornerColor);
        paint.setStyle(Paint.Style.FILL);

        int corWidth = innerCornerWidth;
        int corLength = innerCornerLength;

        // 左上角
        canvas.drawRect(frame.left, frame.top, frame.left + corWidth, frame.top + corLength, paint);
        canvas.drawRect(frame.left, frame.top, frame.left + corLength, frame.top + corWidth, paint);
        // 右上角
        canvas.drawRect(frame.right - corWidth, frame.top, frame.right, frame.top + corLength, paint);
        canvas.drawRect(frame.right - corLength, frame.top, frame.right, frame.top + corWidth, paint);
        // 左下角
        canvas.drawRect(frame.left, frame.bottom - corLength, frame.left + corWidth, frame.bottom, paint);
        canvas.drawRect(frame.left, frame.bottom - corWidth, frame.left + corLength, frame.bottom, paint);
        // 右下角
        canvas.drawRect(frame.right - corWidth, frame.bottom - corLength, frame.right, frame.bottom, paint);
        canvas.drawRect(frame.right - corLength, frame.bottom - corWidth, frame.right, frame.bottom, paint);
    }

    /**
     * 扫描框的文字标记
     */
    private String textTradeMark;
    private final static int TRADE_MARK_TEXT_SIZE_SP = 14;

    /**
     * 提示标记的文字
     *
     * @param canvas canvas
     * @param frame  扫描矩形框
     */
    private void drawTradeMark(Canvas canvas, Rect frame) {
        float tradeMarkTop, tradeMarkLeft;

        tradeMarkPaint.setColor(Color.WHITE);
        tradeMarkPaint.setAntiAlias(true);
        tradeMarkPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                TRADE_MARK_TEXT_SIZE_SP, getResources().getDisplayMetrics()));

        if (frame != null) {
            tradeMarkTop = frame.bottom + tradeMarkPaint.getTextSize() + 10;
            tradeMarkLeft = frame.left;
        } else {
            tradeMarkTop = 10;
            tradeMarkLeft = canvas.getHeight() - tradeMarkPaint.getTextSize() - 10;
        }
        canvas.drawText(textTradeMark, tradeMarkLeft, tradeMarkTop, tradeMarkPaint);
    }

    public void drawViewfinder() {
        resultBitmap = null;
        invalidate();
    }

    public void addPossibleResultPoint(ResultPoint point) {
        possibleResultPoints.add(point);
    }

    public static int getScreenWidth(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        return dm.widthPixels;
    }

    public static int getScreenHeight(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        return dm.heightPixels;
    }

}
