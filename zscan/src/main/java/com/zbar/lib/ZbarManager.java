package com.zbar.lib;

public class ZbarManager {

    static {
        System.loadLibrary("zbar");
    }

    /**
     * 1. 声明本地方法 用到 native 关键字 本地方法不用去实现 (方法名为红色不影响)
     * 2. 本地函数命名规则: Java_包名_类名_本地方法名 (包名是固定不变)
     *    (此处的包名为: com.zbar.lib - Java_com_zbar_lib_decode)
     * 3. 在 java 代码中加载动态链接库 System.loadlibrary("动态链接库的名字"); Android.mkLOCAL_MODULE 所指定的名字
     *
     * @param data
     * @param width
     * @param height
     * @param isCrop
     * @param x
     * @param y
     * @param cwidth
     * @param cheight
     * @return
     */
    public native String decode(byte[] data, int width, int height, boolean isCrop, int x, int y, int cwidth, int cheight);
}
