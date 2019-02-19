/*
 * Copyright (C) 2010 ZXing authors
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

package com.zbar.lib;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.github.jeterlee.zscan.R;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.zxing.camera.CameraManager;

import java.util.Map;

/**
 * <pre>
 * Title: DecodeHandler
 * Description: 解码 Handler，在 解码的 Handler {@link DecodeHandler} 中向
 * 扫码的 Handler {@link CaptureActivityHandler} 发送消息。
 * </pre>
 *
 * @author <a href="https://www.github.com/jeterlee"></a>
 * @date 2019/2/16 0016
 */
public class DecodeHandler extends Handler {
    private static final String TAG = DecodeHandler.class.getSimpleName();
    private final CaptureActivityHandler captureActivityHandler;
    private final MultiFormatReader multiFormatReader;
    private boolean running = true;
    private ZbarManager manager;

    public DecodeHandler(CaptureActivityHandler captureActivityHandler,
                         Map<DecodeHintType, Object> hints) {
        this.captureActivityHandler = captureActivityHandler;
        multiFormatReader = new MultiFormatReader();
        multiFormatReader.setHints(hints);
    }

    @Override
    public void handleMessage(Message message) {
        if (!running) {
            return;
        }
        if (message.what == R.id.decode) {
            // Log.d(TAG, "Got decode message");
            decode((byte[]) message.obj, message.arg1, message.arg2);

        } else if (message.what == R.id.quit) {
            running = false;
            Looper.myLooper().quit();

        }
    }

    /**
     * Decode the data within the viewfinder rectangle, and time how long it
     * took. For efficiency, reuse the same reader objects from one decode to
     * the next. （最大的不同点，使用 zbar {@link ZbarManager} 解码）
     *
     * @param data   The YUV preview frame.
     * @param width  The width of the preview frame.
     * @param height The height of the preview frame.
     */
    public void decode(byte[] data, int width, int height) {
        // modify here
        long start = System.currentTimeMillis();
        byte[] rotatedData = new byte[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                rotatedData[x * height + height - y - 1] = data[x + y * width];
            }
        }
        // Here we are swapping, that's the difference to #11
        int tmp = width;
        width = height;
        height = tmp;

        Rect rect = new Rect(CameraManager.get().getFramingRect());
        if (manager == null) {
            manager = new ZbarManager();
        }
        String result = manager.decode(rotatedData, width, height, true,
                rect.left, rect.top, rect.right - rect.left, rect.bottom
                        - rect.top);

        // zxing 解码方式
        // PlanarYUVLuminanceSource source =
        // CameraManager.get().buildLuminanceSource(rotatedData, width, height);
        // BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        // try {
        // rawResult = multiFormatReader.decodeWithState(bitmap);
        // } catch (ReaderException re) {
        // // continue
        // } finally {
        // multiFormatReader.reset();
        // }

        if (result != null) {
            // Don't log the barcode contents for security.
            long end = System.currentTimeMillis();
            Log.d(TAG, "Found barcode in " + (end - start) + " ms");
            if (captureActivityHandler != null) {
                Message message = Message.obtain(captureActivityHandler, R.id.decode_succeeded, result);
                // zxing 解码方式
                // Bundle bundle = new Bundle();
                // bundle.putParcelable(DecodeThread.BARCODE_BITMAP,
                // source.renderCroppedGreyscaleBitmap());
                // message.setData(bundle);
                // Log.d(TAG, "Sending decode succeeded message...");
                message.sendToTarget();
            }
        } else {
            if (captureActivityHandler != null) {
                Message message = Message.obtain(captureActivityHandler, R.id.decode_failed);
                message.sendToTarget();
            }
        }
    }

}
