package com.nlscan.uhf.bu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;

import androidx.appcompat.widget.AppCompatImageView;

import java.util.ArrayList;
import java.util.List;

public class OverCameraView extends AppCompatImageView {
    private Context context;

    private Rect touchFocusRect;//焦点附近设置矩形区域作为对焦区域
    private Paint touchFocusPaint;//新建画笔

    public OverCameraView(Context context){
        this(context, null, 0);
    }
    public OverCameraView(Context context, AttributeSet attrs){
        this(context, attrs, 0);
    }
    public OverCameraView(Context context, AttributeSet attrs, int defStyleAttr){
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context){
        this.context = context;
        //画笔设置
        touchFocusPaint = new Paint();
        touchFocusPaint.setColor(Color.GREEN);
        touchFocusPaint.setStyle(Paint.Style.STROKE);
        touchFocusPaint.setStrokeWidth(3);
    }

    //对焦并绘制对焦矩形框
    public void setTouchFoucusRect(Camera camera, Camera.AutoFocusCallback autoFocusCallback, float x, float y){
        //以焦点为中心，宽度为200的矩形框
        touchFocusRect = new Rect((int)(x - 100), (int)(y - 100), (int)(x + 100), (int)(y + 100));

        //对焦区域
        final Rect targetFocusRect=new Rect(
                touchFocusRect.left*2000/Constants.WIDTH - 1000,
                touchFocusRect.top*2000/Constants.HEIGHT - 1000,
                touchFocusRect.right*2000/Constants.WIDTH - 1000,
                touchFocusRect.bottom*2000/Constants.HEIGHT - 1000);

        doTouchFocus(camera,autoFocusCallback,targetFocusRect);//对焦
        postInvalidate();//刷新界面，调用onDraw(Canvas canvas)函数绘制矩形框
    }

    //设置camera参数，并完成对焦
    public void doTouchFocus(Camera camera, Camera.AutoFocusCallback autoFocusCallback, final Rect tfocusRect){
        try{

            final List<Camera.Area> focusList = new ArrayList<>();
            Camera.Area focusArea = new Camera.Area(tfocusRect, 1000);//相机参数：对焦区域
            focusList.add(focusArea);

            Camera.Parameters para = camera.getParameters();
            para.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); // 设置对焦模式
            para.setFocusAreas(focusList);
//            para.setMeteringAreas(focusList);

            camera.cancelAutoFocus();
            camera.setParameters(para);//相机参数生效
            camera.autoFocus(autoFocusCallback);
        } catch(Exception e){

        }
    }

    //对焦完成后，清除对焦矩形框
    public void disDrawTouchFocusRect(){
        touchFocusRect = null;//将对焦区域设置为null，刷新界面后对焦框消失
        postInvalidate();//刷新界面，调用onDraw(Canvas canvas)函数
        Log.d("cameraDraw","draw touch");
    }

    @Override
    protected void onDraw(Canvas canvas){ //在画布上绘图，postInvalidate()后自动调用
        drawTouchFocusRect(canvas);
        Log.d("cameraDraw","on draw");
        super.onDraw(canvas);
    }

    private void drawTouchFocusRect(Canvas canvas){
        if(null != touchFocusRect){
            Log.d("cameraDraw","draw the reset");
            //根据对焦区域targetFocusRect，绘制自己想要的对焦框样式，本文在矩形四个角取L形状
            //左下角
            canvas.drawRect(touchFocusRect.left-2, touchFocusRect.bottom, touchFocusRect.left+20, touchFocusRect.bottom+2, touchFocusPaint);
            canvas.drawRect(touchFocusRect.left-2, touchFocusRect.bottom-20, touchFocusRect.left, touchFocusRect.bottom, touchFocusPaint);
            //左上角
            canvas.drawRect(touchFocusRect.left-2, touchFocusRect.top-2, touchFocusRect.left+20, touchFocusRect.top, touchFocusPaint);
            canvas.drawRect(touchFocusRect.left-2, touchFocusRect.top, touchFocusRect.left, touchFocusRect.top+20, touchFocusPaint);
            //右上角
            canvas.drawRect(touchFocusRect.right-20, touchFocusRect.top-2, touchFocusRect.right+2, touchFocusRect.top, touchFocusPaint);
            canvas.drawRect(touchFocusRect.right, touchFocusRect.top, touchFocusRect.right+2, touchFocusRect.top+20, touchFocusPaint);
            //右下角
            canvas.drawRect(touchFocusRect.right-20, touchFocusRect.bottom, touchFocusRect.right+2, touchFocusRect.bottom+2, touchFocusPaint);
            canvas.drawRect(touchFocusRect.right, touchFocusRect.bottom-20, touchFocusRect.right+2, touchFocusRect.bottom, touchFocusPaint);
        }
    }

}

