package com.github.jeterlee.zscan;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.zbar.lib.CaptureActivityHandler;
import com.zxing.AmbientLightManager;
import com.zxing.BeepManager;
import com.zxing.FinishListener;
import com.zxing.ICaptureHandler;
import com.zxing.InactivityTimer;
import com.zxing.IntentSource;
import com.zxing.camera.CameraManager;
import com.zxing.view.ViewfinderView;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * <pre>
 * Title: CaptureActivity
 * Description: 此 Activity 所做的事： 1.开启 camera，在后台独立线程中完成扫描任务；
 *  2.绘制了一个扫描区（viewfinder）来帮助用户将条码置于其中以准确扫描；
 *  3.扫描成功后会将扫描结果展示在界面上；
 * </pre>
 *
 * @author <a href="https://www.github.com/jeterlee"></a>
 * @date 2019/2/16 0016
 */
public class CaptureActivity extends AppCompatActivity
        implements SurfaceHolder.Callback, ICaptureHandler {
    private static final String TAG = CaptureActivity.class.getSimpleName();
    /**
     * 扫码
     */
    private CaptureActivityHandler captureActivityHandler;
    /**
     * 【辅助解码的参数(用作 MultiFormatReader 的参数)】 编码类型，该参数告诉扫描器采用何种编码方式解码，
     * 即 EAN-13，QR Code 等等 对应于 DecodeHintType.POSSIBLE_FORMATS 类型
     * 参考 DecodeThread 构造函数中如下代码：hints.put(DecodeHintType.POSSIBLE_FORMATS,
     * decodeFormats);
     */
    private Collection<BarcodeFormat> decodeFormats;
    /**
     * 【辅助解码的参数(用作MultiFormatReader的参数)】 该参数最终会传入MultiFormatReader，
     * 上面的decodeFormats和characterSet最终会先加入到decodeHints中 最终被设置到MultiFormatReader中
     * 参考DecodeHandler构造器中如下代码：multiFormatReader.setHints(hints);
     */
    private Map<DecodeHintType, ?> decodeHints;
    /**
     * 【辅助解码的参数(用作 MultiFormatReader 的参数)】 字符集，告诉扫描器该以何种字符集进行解码
     * 对应于 DecodeHintType.CHARACTER_SET 类型
     * 参考 DecodeThread 构造器如下代码：hints.put(DecodeHintType.CHARACTER_SET,
     * characterSet);
     */
    private String characterSet;
    /**
     * 活动监控器。如果手机没有连接电源线，那么当相机开启后如果一直处于不被使用状态则该服务会
     * 将当前 activity 关闭。活动监控器全程监控扫描活跃状态，与 CaptureActivity 生命周期相
     * 同。每一次扫描过后都会重置该监控，即重新倒计时。
     */
    private InactivityTimer inactivityTimer;
    private String lastResult;
    private IntentSource source;
    private Camera camera;
    /**
     * 扫描区域
     */
    private ViewfinderView viewfinderView;
    /**
     * 是否有预览
     */
    private boolean hasSurface;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    /**
     * 声音震动管理器。如果扫描成功后可以播放一段音频，也可以震动提醒，可以通过配置来决定扫描成功后的行为。
     */
    private BeepManager beepManager;
    /**
     * 闪光灯调节器。自动检测环境光线强弱并决定是否开启闪光灯
     */
    private AmbientLightManager ambientLightManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.capture);

        try {
            hasSurface = false;
            inactivityTimer = new InactivityTimer(this);
            beepManager = new BeepManager(this);
            ambientLightManager = new AmbientLightManager(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        viewfinderView = findViewById(R.id.capture_viewfinder_view);
        // 摄像头预览功能必须借助 SurfaceView，因此也需要在一开始对其进行初始化
        // 如果需要了解 SurfaceView 的原理
        // 参考:http://blog.csdn.net/luoshengyang/article/details/8661317
        // 预览
        surfaceView = findViewById(R.id.capture_preview_view);
        surfaceHolder = surfaceView.getHolder();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 相机初始化的动作需要开启相机并测量屏幕大小，这些操作
        // 不建议放到 onCreate 中，因为如果在 onCreate 中加上首次启动展示帮助信息的代码的 话，
        // 会导致扫描窗口的尺寸计算有误的 bug
        CameraManager.init(getApplicationContext());
        captureActivityHandler = null;

        try {
            if (hasSurface) {
                // The activity was paused but not stopped, so the surface still
                // exists. Therefore
                // surfaceCreated() won't be called, so init the camera here.
                initCamera(surfaceHolder);

            } else {
                // Install the callback and wait for surfaceCreated() to init the
                // camera.
                surfaceHolder.addCallback(this);
                // 防止 sdk8 的设备初始化预览异常
                surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 加载声音配置，其实在 BeepManager 的构造器中也会调用该方法，即在 onCreate 的时候会调用一次
        beepManager.updatePrefs();
        // 启动闪光灯调节器
        ambientLightManager.start(CameraManager.get());
        // 恢复活动监控器
        inactivityTimer.onResume();
        source = IntentSource.NONE;
        decodeFormats = null;
        characterSet = null;
    }

    @Override
    protected void onPause() {
        if (captureActivityHandler != null) {
            captureActivityHandler.quitSynchronously();
            captureActivityHandler = null;
        }
        inactivityTimer.onPause();
        ambientLightManager.stop();
        beepManager.close();

        // 关闭摄像头
        CameraManager.get().closeDriver();
        // 如果没有预览界面，就及时回收
        if (!hasSurface) {
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // 重新进行扫描
                if ((source == IntentSource.NONE) && lastResult != null) {
                    restartPreviewAfterDelay(0L);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_FOCUS:
            case KeyEvent.KEYCODE_CAMERA:
                // Handle these events so they don't launch the Camera app
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
                CameraManager.get().zoomIn();
                return true;

            case KeyEvent.KEYCODE_VOLUME_DOWN:
                CameraManager.get().zoomOut();
                return true;

            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 处理扫描结果
     *
     * @param result 扫描结果
     */
    @Override
    public void handleDecode(String result) {
        try {
            inactivityTimer.onActivity();
            lastResult = result;
            beepManager.playBeepSoundAndVibrate();

            Toast.makeText(this, "识别结果:" + result,
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // <editor-fold desc="surface 接口的实现（surface 生命周期）">

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
        // if (camera != null && CameraManager.get().isPreviewing()) {
        //     if (!CameraManager.get().isUseOneShotPreviewCallback()) {
        //         camera.setPreviewCallback(null);
        //     }
        //     camera.stopPreview();
        //     CameraManager.get().getPreviewCallback().setHandler(null, 0);
        //     CameraManager.get().getAutoFocusCallback().setHandler(null, 0);
        //     CameraManager.get().setPreviewing(false);
        // }
    }
    // </editor-fold>


    @Override
    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    @Override
    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    @Override
    public void deliverResult(Intent data) {
        this.setResult(Activity.RESULT_OK, data);
        this.finish();
    }

    @Override
    public void launchQueryByUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        startActivity(intent);
    }

    public void restartPreviewAfterDelay(long delayMS) {
        if (captureActivityHandler != null) {
            captureActivityHandler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
        }
        resetStatusView();
    }

    private void resetStatusView() {
        viewfinderView.setVisibility(View.VISIBLE);
        lastResult = null;
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (CameraManager.get().isOpen()) {
            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
            return;
        }

        try {
            CameraManager.get().openDriver(surfaceHolder);
            camera = CameraManager.get().getCamera();
            if (captureActivityHandler == null) {
                captureActivityHandler = new CaptureActivityHandler(this,
                        decodeFormats, decodeHints, characterSet);
            }
            decodeOrStoreSavedBitmap(null, null);
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    /**
     * 向 CaptureActivityHandler 中发送消息，并展示扫描到的图像
     *
     * @param bitmap
     * @param result
     */
    private void decodeOrStoreSavedBitmap(Bitmap bitmap, Result result) {
    }

    private void displayFrameworkBugMessageAndExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_name));
        builder.setMessage(getString(R.string.zscan_msg_camera_framework_bug));
        builder.setPositiveButton(R.string.zscan_button_ok, new FinishListener(this));
        builder.setOnCancelListener(new FinishListener(this));
        builder.show();
    }

}