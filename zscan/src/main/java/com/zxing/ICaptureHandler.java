package com.zxing;

import android.content.Intent;
import android.graphics.Bitmap;

import com.zxing.view.ViewfinderView;

/**
 * <pre>
 * Title: ICaptureHandler
 * Description: 扫码接口
 * </pre>
 *
 * @author <a href="https://www.github.com/jeterlee"></a>
 * @date 2019/2/15 0015
 */

public interface ICaptureHandler {
    /**
     * 获取 ViewfinderView，提供给相机
     *
     * @return ViewfinderView
     */
    ViewfinderView getViewfinderView();

    /**
     * 绘制扫码 Viewfinder 界面
     */
    void drawViewfinder();

    /**
     * 扫码结果处理，成功扫码才会执行
     *
     * @param result 扫码结果
     */
    void handleDecode(String result);

    /**
     * 扫码结果
     *
     * @param data 扫码结果
     */
    void deliverResult(Intent data);

    /**
     * 启动查询
     *
     * @param url
     */
    void launchQueryByUrl(String url);

    /**
     * 解析二维码结果接口
     */
    interface AnalyzeCallback {
        /**
         * 解析二维码成功
         *
         * @param mBitmap 二维码
         * @param result  解析结果
         */
        void onAnalyzeSuccess(Bitmap mBitmap, String result);

        /**
         * 解析二维码失败
         */
        void onAnalyzeFailed();
    }

}
