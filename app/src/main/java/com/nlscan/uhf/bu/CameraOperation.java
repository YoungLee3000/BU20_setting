/*
 * Copyright 2020. Huawei Technologies Co., Ltd. All rights reserved.
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
package com.nlscan.uhf.bu;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import com.huawei.hms.ml.camera.CameraMeteringData;
import com.huawei.hms.scankit.A;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CameraOperation {

    private static final String TAG = "CameraOperation";
    private Camera camera = null;
    private Camera.Parameters parameters = null;
    private boolean isPreview = false;
    private FrameCallback frameCallback = new FrameCallback();
    private int width = Constants.WIDTH;
    private int height = Constants.HEIGHT;
    private double defaultZoom = 1.0;
    private Timer focusTimer = new Timer();

    private OverCameraView mOverCameraView;//绘制对焦框控件


    /**
     * Open up the camera.
     */
    public synchronized void open(SurfaceHolder holder,  OverCameraView overCameraView) throws IOException {
        camera = Camera.open();
        parameters = camera.getParameters();
        parameters.setPictureSize(width, height);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        parameters.setPictureFormat(ImageFormat.NV21);
        camera.setPreviewDisplay(holder);
        camera.setParameters(parameters);
        mOverCameraView = overCameraView;
    }

    //打开闪光灯
    public void openFlash(){
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        camera.setParameters(parameters);
    }

    //打开闪光灯
    public void closeFlash(){
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        camera.setParameters(parameters);
    }

    private Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            Log.d("cameraDraw","on focus");

            mOverCameraView.disDrawTouchFocusRect();//清除对焦框

            //对焦完成后改回连续对焦
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(parameters);
        }
    };

    public void focus(float x, float y){
        mOverCameraView.setTouchFoucusRect(camera,autoFocusCallback, x, y);
    }

    //自动对焦
    public void focus(int x,int y) {

        focusTimer.cancel();
        Rect focusArea = new Rect();
        focusArea.left = Math.max(x - 100, -1000); // 取最大或最小值，避免范围溢出屏幕坐标
        focusArea.top = Math.max(y - 100, -1000);
        focusArea.right = Math.min(x + 100, 1000);
        focusArea.bottom = Math.min(y + 100, 1000);
        // 创建Camera.Area
        Camera.Area cameraArea = new Camera.Area(focusArea, 1000);
        List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
        List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
        if (parameters.getMaxNumMeteringAreas() > 0) {
            meteringAreas.add(cameraArea);
            focusAreas.add(cameraArea);
        }
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); // 设置对焦模式
        parameters.setFocusAreas(focusAreas); // 设置对焦区域

//        parameters.setMeteringAreas(meteringAreas); // 设置测光区域
        try {
            camera.cancelAutoFocus(); // 每次对焦前，需要先取消对焦
            camera.setParameters(parameters); // 设置相机参数
            camera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {

                }
            }); // 开启对焦
        } catch (Exception e) {
        }

        focusTimer = new Timer();

        focusTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                camera.setParameters(parameters);
            }
        },1000);

    }





    public synchronized void close() {
        if (camera != null) {
            focusTimer.cancel();
            camera.release();
            camera = null;
        }
    }

    public synchronized void startPreview() {
        if (camera != null && !isPreview) {
            camera.startPreview();
            isPreview = true;
        }
    }

    public synchronized void stopPreview() {
        if (camera != null && isPreview) {
            camera.stopPreview();
            frameCallback.setProperties(null);
            isPreview = false;
        }
    }

    public synchronized void callbackFrame(Handler handler, double zoomValue) {
        if (camera != null && isPreview) {
            frameCallback.setProperties(handler);
            if (camera.getParameters().isZoomSupported() && zoomValue != defaultZoom) {
                //Auto zoom.
                parameters.setZoom(convertZoomInt(zoomValue));
                camera.setParameters(parameters);
            }
            camera.setOneShotPreviewCallback(frameCallback);
        }
    }

    public int convertZoomInt(double zoomValue) {
        List<Integer> allZoomRatios = parameters.getZoomRatios();
        float maxZoom = Math.round(allZoomRatios.get(allZoomRatios.size() - 1) / 100f);
        if (zoomValue >= maxZoom) {
            return allZoomRatios.size() - 1;
        }
        for (int i = 1; i < allZoomRatios.size(); i++) {
            if (allZoomRatios.get(i) >= (zoomValue * 100) && allZoomRatios.get(i - 1) <= (zoomValue * 100)) {
                return i;
            }
        }
        return -1;
    }



    class FrameCallback implements Camera.PreviewCallback {

        private Handler handler;

        public void setProperties(Handler handler) {
            this.handler = handler;

        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (handler != null) {
                Message message = handler.obtainMessage(0, camera.getParameters().getPreviewSize().width,
                        camera.getParameters().getPreviewSize().height, data);
                message.sendToTarget();
                handler = null;
            }
        }
    }
}
