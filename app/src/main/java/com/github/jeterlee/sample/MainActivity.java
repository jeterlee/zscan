package com.github.jeterlee.sample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.github.jeterlee.zscan.CaptureActivity;
import com.tbruyelle.rxpermissions2.RxPermissions;

import io.reactivex.functions.Consumer;

public class MainActivity extends AppCompatActivity {
    private RxPermissions rxPermissions;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        rxPermissions = new RxPermissions(this);

        // 先确保是否已经申请过摄像头,和写入外部存储的权限
        boolean isPermissionsGranted = (rxPermissions.isGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                && rxPermissions.isGranted(Manifest.permission.CAMERA));

        // 已经申请过,直接执行操作
        if (isPermissionsGranted) {
            startActivity(new Intent(this, CaptureActivity.class));
            // 没有申请过,则申请
        } else {
            rxPermissions.request(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA)
                    .subscribe(new Consumer<Boolean>() {
                        @Override
                        public void accept(Boolean aBoolean) throws Exception {
                            if (aBoolean){
                                startActivity(new Intent(MainActivity.this, CaptureActivity.class));
                            }else {

                            }
                        }
                    });
        }

    }
}
