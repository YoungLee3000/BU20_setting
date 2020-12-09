package com.nlscan.uhf.bu;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.nlscan.android.uhf.UHFManager;
import com.nlscan.android.uhf.UHFReader;
import com.xys.libzxing.zxing.activity.CaptureActivity;

import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class SearchActivity extends BaseActivity {


    //扫码广播相关
    private static final String ACTION_SCAN_RESULT = "nlscan.action.SCANNER_RESULT";
    private static final String ACTION_SCAN_RESULT_MAIL = "ACTION_BAR_SCAN";

    //
    private static final String TAG = "bleDemo";


    //handler 相关
    private MyHandler gMyHandler = new MyHandler(this);
    private static final int CHANGE_SUCCESS = 1;
    private static final int CHANGE_RETRY = 2;
    private static final int CHANGE_FAIL = 3;
    private static final int CHANGE_FIND = 4;
    private static final int CHANGE_RE_POWER = 5;
    private Timer myTimer = new Timer();
    private static final int TIMEOUT_VAL = 30000;


    //UHF相关
    private UHFManager mUHFMgr = UHFManager.getInstance();




    //扫码接收广播
    private BroadcastReceiver mScanReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {

            final String scanResult_1=intent.getStringExtra("SCAN_BARCODE1");
            final String scanStatus=intent.getStringExtra("SCAN_STATE");

            if("ok".equals(scanStatus)){
//                connectTarget(scanResult_1);
                obtainAddress(scanResult_1);
            }

        }
    };

    //邮政码接收广播
    private BroadcastReceiver mScanReceiverMail = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            final String scanResult_1=intent.getStringExtra("EXTRA_SCAN_DATA");
            final String scanStatus=intent.getStringExtra("EXTRA_SCAN_STATE");

            if("ok".equals(scanStatus)){
//                connectTarget(scanResult_1);
                obtainAddress(scanResult_1);
            }

        }
    };


    //蓝牙广播
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    private BroadcastReceiver mBleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            BluetoothDevice device =  intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            String currentAddress = device != null ? device.getAddress() : "";
            String intentAddress = intent.getStringExtra("DeviceAddress");
            final String action = intent.getAction();
            int connectState = 0;
            if (device != null) connectState = device.getBondState();
            if (ACTION_GATT_CONNECTED.equals(action)) {

                if (   mDeviceAddress.equals(   intentAddress)){
//                    Toast.makeText(SearchActivity.this,"连接成功" ,Toast.LENGTH_SHORT).show();
//                    jumpTo();
                     powerOn();

                }

            }
            else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
                if (BluetoothDevice.BOND_BONDED == connectState){
//                    Toast.makeText(SearchActivity.this,"配对成功" , Toast.LENGTH_SHORT).show();
//                    if (mBluetoothLeService != null)  mBluetoothLeService.connect(mDeviceAddress);

                    if (   mDeviceAddress.equals(   currentAddress)  && !"".equals(currentAddress))
                        bleConnect(mDeviceAddress);
                }
                else if (BluetoothDevice.BOND_NONE == connectState){
                    Log.d(TAG,"refuse bonding");
                    if (isDialogShow()) gMyHandler.sendEmptyMessage(CHANGE_RETRY);
                }

            }

        }
    };



    //蓝牙相关全局变量
    private Button btnScan;
    private TextView textView;
    private ProgressDialog mDialog;
    private Map<String,String> mSerialMac = new HashMap<>();//序列号地址键值对
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothGatt mBluetoothGatt;
    private BluetoothLeScanner mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();


    //蓝牙设备地址
    private String mScanSerial = "";//扫码得到的序列号
    private String mDeviceAddress = "";//寻到的设备地址
    private String mConnectDeviceAddress = "";//建立连接的地址



    //打开蓝牙后的回调
    private static final int FIND_DEVICE = 1;
    private static final int FLAG_SCAN_RETURN = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);




        initGps();
        initActionBar();
        initBlue();
        initView();
        barcodeSet();



    }


    @Override
    protected void onPause() {
        super.onPause();

        unRegister();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        register();

    }

    @Override
    protected void onDestroy() {
        cancelDialog();
        mBluetoothLeScanner.stopScan(mLeScanCallback);
        super.onDestroy();

    }

    /**
     * 返回图标功能
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:

                this.finish(); // back button
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    /**
     * 初始化标题
     */
    private void initActionBar() {
        getActionBar().setDisplayShowHomeEnabled(false);
        getActionBar().setDisplayShowCustomEnabled(true);
        getActionBar().setCustomView(R.layout.action_bar);
        ((TextView) findViewById(R.id.tv_title)).setText(getTitle());

        ImageView leftHome = (ImageView) findViewById(R.id.img_home);
        leftHome.setVisibility(View.VISIBLE);
        leftHome.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }


    //开启GPS
    private void initGps(){
        if (!isOPen(this)){
            new AlertDialog.Builder(SearchActivity.this)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setTitle("开启定位")
                    .setMessage("开启定位以便搜索BLE蓝牙")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivityForResult(intent,887);
                            dialogInterface.dismiss();
                        }
                    })
                    .show();

        }

    }

    //GPS是否开启
    private static final boolean isOPen(final Context context) {
        LocationManager locationManager
                = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        // 通过GPS卫星定位，定位级别可以精确到街（通过24颗卫星定位，在室外和空旷的地方定位准确、速度快）
        boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 通过WLAN或移动网络(3G/2G)确定的位置（也称作AGPS，辅助GPS定位。主要用于在室内或遮盖物（建筑群或茂密的深林等）密集的地方定位）
        boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (gps|| network) {
            return true;
        }

        return false;
    }



    //初始化视图
    private void initView(){
        btnScan = (Button)  findViewById(R.id.btn_scan);

        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readCode();
            }
        });

        textView = (TextView) findViewById(R.id.text_tip);
        textView.setText(Html.fromHtml( getString(R.string.scan_connect_tips)));
    }

    //设置扫码配置
    private void barcodeSet(){
        Intent intentConfig = new Intent("ACTION_BAR_SCANCFG");
        intentConfig.putExtra("EXTRA_SCAN_MODE", 3);//广播输出
        intentConfig.putExtra("EXTRA_OUTPUT_EDITOR_ACTION_ENABLE", 0);//不输出软键盘
        sendBroadcast(intentConfig);
    }

    //初始化蓝牙搜索
    private void initBlue(){

        if (!mBluetoothAdapter.isEnabled()) {
            // 弹出对话框提示用户是否打开
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, FIND_DEVICE);
        }
        else{
//            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
////            mBluetoothAdapter.startDiscovery();
            mBluetoothLeScanner.startScan(mLeScanCallback);
//            mBluetoothAdapter.startLeScan(mLeScanCallback);

            Log.d(TAG,"start the discovery");
        }

    }




    //解析扫描数据
    private void obtainAddress(String serial){



        if(isDialogShow()) return;

        showLoadingWindow(getString(R.string.scan_connect_dialog));
        myTimer.cancel();
        myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                cancelDialog();
                gMyHandler.sendEmptyMessage(CHANGE_FAIL);
            }
        },TIMEOUT_VAL);


        mScanSerial = serial.toUpperCase();


        Log.d(TAG,"scan address is " + mScanSerial);

        reFind();


    }


    /**
     * 判断设备是否找到
     */
    private void reFind(){



        String scanAddress = mSerialMac.get(mScanSerial);



        //找到了设备
        if (scanAddress != null   && ! "".equals(scanAddress)){
            connectTarget(scanAddress);
        }
        //未找到设备
        else {

            int a2dp = mBluetoothAdapter.getProfileConnectionState(4);
            if (a2dp == BluetoothProfile.STATE_CONNECTED){

                //当前扫码的地址是否已经连接
                if (ifCurrentConnect(mScanSerial)){
                    gMyHandler.sendEmptyMessage(CHANGE_SUCCESS);
                }
                else{
                    reFindCmd();
                }

            }
            else{//如果未和绑定的设备连接,则解除绑定

                reFindCmd();
                Toast.makeText(this,"未搜索到该设备,请打开设备！",Toast.LENGTH_SHORT).show();
            }



        }

    }


    //发送重连指令
    private void reFindCmd(){
        if (!isDialogShow()) return;
//                    mBluetoothLeScanner.stopScan(mLeScanCallback);
//            removeAll();
            Log.d(TAG,"remove bond");
//                    mBluetoothLeScanner.startScan(mLeScanCallback);
            gMyHandler.sendEmptyMessageDelayed(CHANGE_FIND,1000);

    }


    //开始尝试上电
    private void powerOn(){

        if(!isDialogShow()) return;
        myTimer.cancel();
        mDialog.setMessage("上电中,请不要退出界面");

//        Toast.makeText(this,"上电中,请不要退出界面",Toast.LENGTH_SHORT).show();

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                UHFReader.READER_STATE er =  mUHFMgr.powerOn();
                if (er != UHFReader.READER_STATE.OK_ERR){

                    gMyHandler.sendEmptyMessage(CHANGE_RE_POWER);
                }
                else {

                    gMyHandler.sendEmptyMessage(CHANGE_SUCCESS);
                }
            }
        },3000);



    }


    //判断当前地址的设备是否已经连接
    private boolean ifCurrentConnect(String scanAddress){
        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : bondedDevices) {
            if (device != null){
                String address = device.getAddress();
                if (address !=null && address.toUpperCase().equals(scanAddress)) return  true;
            }
        }

        return false;
    }


    //移除所有已经绑定的设备
    private void removeAll(){
        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : bondedDevices) {
            try {
                Class btDeviceCls = BluetoothDevice.class;
                Method removeBond = btDeviceCls.getMethod("removeBond");
                removeBond.setAccessible(true);
                removeBond.invoke(device);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    //连接指定设备
    private void connectTarget(String address){


        removeAll();


        mDeviceAddress = address;


        reConnect();

    }


    /**
     * 重新连接设备
     */
    private void reConnect(){

        if (mDeviceAddress != null && !"".equals(mDeviceAddress)){
            BluetoothDevice targetDevice = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);
            Log.d(TAG,"device is reconnecting !");

            if ( !targetDevice.createBond()){

                try {
                    Class btDeviceCls = BluetoothDevice.class;
                    Method removeBond = btDeviceCls.getMethod("removeBond");
                    removeBond.setAccessible(true);
                    removeBond.invoke(targetDevice);
                } catch (Exception e) {
                    e.printStackTrace();
                }


                if (isDialogShow()){
                    gMyHandler.sendEmptyMessageDelayed(CHANGE_RETRY,1000);
                }
            }
        }


    }




    //连接指定地址
    public boolean bleConnect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if ( mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {

                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, myGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mConnectDeviceAddress = address;

        return true;
    }


    //蓝牙Gatt回调
    private final BluetoothGattCallback myGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG,"connect success");
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Intent intent = new Intent(ACTION_GATT_CONNECTED);
                intent.putExtra("DeviceAddress",mConnectDeviceAddress);
                sendBroadcast(intent);
            }
        }
    };


    /**
     * 搜索设备回调
     */
    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            ScanRecord scanRecord = result.getScanRecord();

            BluetoothDevice device = result.getDevice();
            if (device != null){
                Log.d(TAG,"the device address " + device.getAddress() );
                Log.d(TAG,"the device name " + device.getName() );
                String name = device.getName();
                if (name != null)
                    mSerialMac.put(device.getName().toUpperCase(),device.getAddress());
            }
//            int dT =  device.getType();



//            if (scanRecord !=  null && device !=null ){
//                byte[] bytes  = scanRecord.getBytes();
//                String str = HexUtil.bytesToHexString(bytes);
//
//                String manuInfo = getManuInfo(str);
//                if (manuInfo.startsWith("ff00")){
//                    String manuInfoDecode = HexUtil.hexStringToString(manuInfo.substring(4));
//                    String serialInfo =  manuInfoDecode == null ? "" : manuInfoDecode.toUpperCase() ;
//                    if (!mSerialMac.containsKey(serialInfo))
//                        mSerialMac.put(serialInfo,device.getAddress());
//                    Log.d(TAG,"the serialInfo " + serialInfo + " device " + device.getAddress());
//                }
//
//            }



        }
    };


    /**
     * 根据广播信息获取厂家信息
     * @param record
     * @return
     */
    private String getManuInfo(String record){
        int recordLen = record.length();
        int beginIndex = 0;

        String resultInfo = "";
        while (beginIndex <= recordLen -4){
            int typeLen = Integer.parseInt(record.substring(beginIndex,beginIndex+2),16) * 2;
            if (typeLen >= 60) break;
            if (  "ff".equals( record.substring(beginIndex+2,beginIndex+4) )){
                resultInfo = record.substring(beginIndex + 4 , beginIndex + 2 + typeLen);
                break;
            }
            beginIndex = beginIndex + 2 + typeLen;
        }

        return resultInfo;
    }

//    // Device scan callback.
//    private BluetoothAdapter.LeScanCallback mLeScanCallback =
//            new BluetoothAdapter.LeScanCallback() {
//
//                @Override
//                public void onLeScan(final BluetoothDevice device, int rssi, final byte[] scanRecord) {
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            Log.d(TAG,"the scan record " + HexUtil.bytesToHexString(scanRecord));
//                        }
//                    });
//                }
//    };


    //跳转到指定页面
    private void jumpTo(){
        mBluetoothLeScanner.stopScan(mLeScanCallback);
//        mBluetoothAdapter.stopLeScan(mLeScanCallback);
//        Intent intent = new Intent(this,MainActivity.class);
//        startActivity(intent);
        finish();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode){
            case FIND_DEVICE:
//                mBluetoothAdapter.startDiscovery();
                mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
                mBluetoothLeScanner.startScan(mLeScanCallback);
//                mBluetoothAdapter.startLeScan(mLeScanCallback);
                break;
            case FLAG_SCAN_RETURN:
                if (data != null) {
                    Bundle bundle = data.getExtras();
                    String scanResult = bundle.getString("result");
                    Log.d(TAG,"the result is" + scanResult);
                    obtainAddress(scanResult);
                }
                break;
            default:
                break;
        }
    }

    /**
     * 关闭进度条
     */
    protected void  cancelDialog(){
        if (mDialog != null){
            mDialog.dismiss();
        }
    }


    private boolean isDialogShow(){
        return mDialog != null && mDialog.isShowing();
    }

    /**
     * 显示进度条
     * @param message
     */
    protected void showLoadingWindow(String message)
    {


        if(isDialogShow())
            return ;

        mDialog = new ProgressDialog(this) ;
        mDialog.setProgressStyle(ProgressDialog.BUTTON_NEUTRAL);// 设置进度条的形式为圆形转动的进度条
        mDialog.setCancelable(true);// 设置是否可以通过点击Back键取消
        mDialog.setCanceledOnTouchOutside(true);// 设置在点击Dialog外是否取消Dialog进度条
        // 设置提示的title的图标，默认是没有的，如果没有设置title的话只设置Icon是不会显示图标的

        mDialog.setMessage(message);
        mDialog.show();

        View v = mDialog.getWindow().getDecorView();
        setDialogText(v);
    }



    //遍历整个View中的textview，然后设置其字体大小
    private void setDialogText(View v){
        if(v instanceof ViewGroup){
            ViewGroup parent=(ViewGroup)v;
            int count=parent.getChildCount();
            for(int i=0;i<count;i++){
                View child=parent.getChildAt(i);
                setDialogText(child);
            }
        }else if(v instanceof TextView){
            ((TextView)v).setTextSize(22);
        }
    }



    //扫码
    private void readCode(){

        mBluetoothLeScanner.startScan(mLeScanCallback);

//        //邮政广播
//        Intent intent = new Intent("ACTION_BAR_TRIGSCAN");
//        intent.putExtra("timeout", 3);//单位为秒，值为int类型，且不超过9秒
//        sendBroadcast(intent);
//
//        //普通广播
//        Intent intent1 = new Intent("nlscan.action.SCANNER_TRIG");
//        intent1.putExtra("SCAN_TIMEOUT", 3);//单位为秒，值为int类型，且不超过9秒
//        sendBroadcast(intent1);//content.


        Intent intent = new Intent(this, CaptureActivity.class);
        startActivityForResult(intent, FLAG_SCAN_RETURN);



    }

    //注册广播
    private void register(){
        //蓝牙广播
        IntentFilter blueFilter = new IntentFilter();


        blueFilter.addAction(ACTION_GATT_CONNECTED);

        blueFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        blueFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        blueFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

        registerReceiver(mBleReceiver,blueFilter);

        //扫码广播
        IntentFilter scanFilter = new IntentFilter(ACTION_SCAN_RESULT);
        registerReceiver(mScanReceiver,scanFilter);

        //邮政广播
        IntentFilter scanFilterMail = new IntentFilter(ACTION_SCAN_RESULT_MAIL);
        registerReceiver(mScanReceiverMail,scanFilterMail);


    }


    //取消注册
    private void unRegister(){
        try {
            unregisterReceiver(mBleReceiver);
            unregisterReceiver(mScanReceiver);
            unregisterReceiver(mScanReceiverMail);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    /**
     * 静态Handler
     */
    static class MyHandler extends Handler {

        private SoftReference<SearchActivity> mySoftReference;

        public MyHandler(SearchActivity mainActivity) {
            this.mySoftReference = new SoftReference<>(mainActivity);
        }

        @Override
        public void handleMessage(Message msg){
            final SearchActivity mainActivity = mySoftReference.get();
            String str = (String) msg.obj;
            switch (msg.what) {

                case CHANGE_SUCCESS:
                    mainActivity.cancelDialog();
                    Toast.makeText(mainActivity,"连接成功", Toast.LENGTH_SHORT).show();
                    mainActivity.jumpTo();
                    break;
                case CHANGE_RETRY:
//                    mainActivity.cancelDialog();
//                    Toast.makeText(mainActivity,str,Toast.LENGTH_SHORT).show();
//                    Toast.makeText(mainActivity,"重新建立配对中,请按住配对键...", Toast.LENGTH_SHORT).show();
                    mainActivity.mDialog.setMessage("重新建立配对中,请按住配对键...");
                    mainActivity.reConnect();
                    break;
                case CHANGE_FAIL:
                    Toast.makeText(mainActivity,"连接失败，请再次扫码", Toast.LENGTH_SHORT).show();
                    break;
                case CHANGE_FIND:
//                    Toast.makeText(mainActivity,"重新搜索设备中...", Toast.LENGTH_SHORT).show();
                    mainActivity.mDialog.setMessage("重新搜索设备中,若长时间搜索不到请重启读卡器...");
                    mainActivity.reFind();
                    break;
                case CHANGE_RE_POWER:
                    mainActivity.cancelDialog();
                    Toast.makeText(mainActivity,"上电失败，请重新上电", Toast.LENGTH_SHORT).show();
                    mainActivity.jumpTo();
                    break;
            }

        }
    }






}
