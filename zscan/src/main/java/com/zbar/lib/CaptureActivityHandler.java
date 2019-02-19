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

package com.zbar.lib;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.github.jeterlee.zscan.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.zxing.ICaptureHandler;
import com.zxing.camera.CameraManager;
import com.zxing.view.ViewfinderResultPointCallback;

import java.util.Collection;
import java.util.Map;

/**
 * This class handles all the messaging which comprises the state machine for
 * capture.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CaptureActivityHandler extends Handler {
    private static final String TAG = CaptureActivityHandler.class.getSimpleName();
    private final ICaptureHandler captureHandler;
    /**
     * 真正负责扫描任务的核心线程
     */
    private final DecodeThread decodeThread;
    private State state;

    /**
     * 当前扫描的状态
     */
    private enum State {
        /**
         * 预览
         */
        PREVIEW,
        /**
         * 扫描成功
         */
        SUCCESS,
        /**
         * 结束扫描
         */
        DONE
    }

    public CaptureActivityHandler(ICaptureHandler captureHandler,
                                  Collection<BarcodeFormat> decodeFormats,
                                  Map<DecodeHintType, ?> baseHints,
                                  String characterSet) {
        this.captureHandler = captureHandler;
        // 启动扫描线程
        decodeThread = new DecodeThread(this, decodeFormats, baseHints,
                characterSet,
                new ViewfinderResultPointCallback(captureHandler.getViewfinderView()));
        decodeThread.start();
        state = State.SUCCESS;
        // Start ourselves capturing previews and decoding.
        CameraManager.get().startPreview();
        restartPreviewAndDecode();
    }

    @Override
    public void handleMessage(Message message) {
        if (message.what == R.id.auto_focus) {
            // if (state == State.PREVIEW) {
            //     CameraManager.get().requestAutoFocus(this, R.id.auto_focus);
            // }

        } else if (message.what == R.id.restart_preview) {
            restartPreviewAndDecode();

        } else if (message.what == R.id.decode_succeeded) {
            state = State.SUCCESS;
            captureHandler.handleDecode((String) message.obj);

        } else if (message.what == R.id.decode_failed) {
            state = State.PREVIEW;
            CameraManager.get().requestPreviewFrame(decodeThread.getDecodeHandler(),
                    R.id.decode);

        } else if (message.what == R.id.return_scan_result) {
            captureHandler.deliverResult((Intent) message.obj);

        } else if (message.what == R.id.launch_product_query) {
            Log.d(TAG, "Got product query message");
            String url = (String) message.obj;
            captureHandler.launchQueryByUrl(url);

        }
    }

    public void quitSynchronously() {
        state = State.DONE;
        CameraManager.get().stopPreview();
        Message quit = Message.obtain(decodeThread.getDecodeHandler(), R.id.quit);
        quit.sendToTarget();
        try {
            decodeThread.join();
        } catch (InterruptedException e) {
            // continue
        }

        // Be absolutely sure we don't send any queued up messages
        removeMessages(R.id.decode_succeeded);
        removeMessages(R.id.decode_failed);
    }

    /**
     * 完成一次扫描后，只需要再调用此方法即可
     */
    public void restartPreviewAndDecode() {
        if (state == State.SUCCESS) {
            state = State.PREVIEW;
            // 向decodeThread绑定的handler（DecodeHandlerImpl)发送解码消息
            CameraManager.get().requestPreviewFrame(decodeThread.getDecodeHandler(),
                    R.id.decode);
            // CameraManager.get().requestAutoFocus(this, R.id.auto_focus);
            captureHandler.drawViewfinder();
        }
    }

}
