package com.nlscan.uhf.bu;

import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.nlscan.android.uhf.UHFManager;
import com.nlscan.android.uhf.UHFModuleInfo;
import com.nlscan.android.uhf.UHFReader;

public class MainActivity extends BasePrefenceActivity implements ISettingChangeListener{

	private UHFManager mUHFMgr = UHFManager.getInstance();
	private HeaderAdapter mAdapter = null;
	private Dialog mRestoreDialog = null;
	private ProgressDialog mRestoreProgDialog = null;
	private Dialog mExitConfirmDialog = null;
	
	private ProgressDialog mCheckingProgDialog = null;
	private Dialog mReLoadDialog = null;
	private static final String TAG = "MainList";
	private boolean mModuleAvailable =  false;
	
	private final static int MSG_UHF_POWER_ON = 0x01;
	private final static int MSG_DISMISS_DIALOG = 0x02;
	private final static int MSG_POWER_ON_COMPLETE = 0x03;
	private final static int MSG_UPDATE_VIEW = 0x04;
	private final static int MSG_RESTORE_SUCCESS = 0x05;
	private final static int MSG_RESTORE_FAILED = 0x06;
	private final static int MSG_RELOAD_MODULE_DELAY = 0x07;
	private final static int MSG_LOAD_MODULE_COMPLETED = 0x08;


	//
    private final static int FUN_RESTORE = 21;
    private final static int FUN_FACTORY = 22;

	private List<Header> myHeaders = new ArrayList<>();


	private MyHandler gMyHandler = new MyHandler(this);
	
	static class MyHandler extends Handler{


		private SoftReference<MainActivity> mySoftReference;

		public MyHandler(MainActivity mainActivity) {
			this.mySoftReference = new SoftReference<>(mainActivity);
		}

		@Override
		public void handleMessage(Message msg) {

			final MainActivity mainActivity = mySoftReference.get();
			
			switch (msg.what) 
			{
			case MSG_UHF_POWER_ON :
					boolean on = (Boolean) msg.obj;
					if(on)
					{
						Thread t = new Thread(new Runnable() {
							@Override
							public void run() {
								UHFReader.READER_STATE er = mainActivity.mUHFMgr.powerOn();
								Message.obtain(mainActivity.gMyHandler, MSG_POWER_ON_COMPLETE, er).sendToTarget();
							}
						});
						t.start();
					}else{
						mainActivity.mUHFMgr.powerOff();
						mainActivity.mAdapter.notifyDataSetChanged();
					}
				break;
			case MSG_DISMISS_DIALOG:
				mainActivity.cancelMyDialog();
				break;
			case MSG_POWER_ON_COMPLETE:
				mainActivity.cancelMyDialog();
				mainActivity.mAdapter.notifyDataSetChanged();
				UHFReader.READER_STATE er = (UHFReader.READER_STATE)msg.obj;
				if(er == UHFReader.READER_STATE.OK_ERR){
					mainActivity.mDisconnect = false;
					mainActivity.getConnectDevice();
					Toast.makeText(mainActivity, mainActivity.getString(R.string.power_on_success), Toast.LENGTH_SHORT).show();
				}else{
					Toast.makeText(mainActivity, mainActivity.getString(R.string.power_on_failed_prompt), Toast.LENGTH_SHORT).show();
				}
				break;
			case MSG_UPDATE_VIEW :
				mainActivity.initHeaders(mainActivity.newGetHeaders());
				mainActivity.mAdapter.notifyDataSetChanged();
				break;
			case MSG_RESTORE_SUCCESS :
				mainActivity.cancelRestoreDialog();
				Toast.makeText(mainActivity, R.string.success, Toast.LENGTH_SHORT).show();
				sendEmptyMessage(MSG_UPDATE_VIEW);
				break;
			case MSG_RESTORE_FAILED :
				mainActivity.cancelRestoreDialog();
				Toast.makeText(mainActivity, R.string.failed, Toast.LENGTH_SHORT).show();
				break;
			case MSG_LOAD_MODULE_COMPLETED:
				mainActivity.cancelCheckDialog();
				
				mainActivity.mModuleAvailable = (mainActivity.mUHFMgr.getUHFModuleInfo() == null ? false : true);
				if(mainActivity.mModuleAvailable)
					sendEmptyMessage(MSG_UPDATE_VIEW);
				else{
					mainActivity.showReloadModuleWindow();//显示重新检测的对话框
				}
				break;
			case MSG_RELOAD_MODULE_DELAY:
				mainActivity.reLoadModule();
				break;
			}//end switch
		}
		
	}




	/**
	 *取消对话框 
	 */
	private void cancelCheckDialog(){
		if(mCheckingProgDialog != null)
			mCheckingProgDialog.dismiss();
	}

	/**
	 *取消对话框 
	 */
	private void cancelRestoreDialog(){
		if(mRestoreProgDialog != null)
			mRestoreProgDialog.dismiss();
	}
	
	//取消对话框
	private void cancelMyDialog(){
		if(mDialog != null)
			mDialog.dismiss();
		if (mReLoadDialog !=null){
			mReLoadDialog.dismiss();
			mReLoadDialog = null;
		}
	}
	
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initActionBar();
		//动态权限申请
		PermissionUtils.requestAllRuntimePermission(MainActivity.this);

		mDisconnect = false;


		mModuleAvailable = (mUHFMgr.getUHFModuleInfo() != null);
		mUHFMgr.setPromptSoundEnable(false);
		mUHFMgr.setPromptVibrateEnable(false);
		if(!mModuleAvailable)
			gMyHandler.sendEmptyMessageDelayed(MSG_RELOAD_MODULE_DELAY, 50);
	}
	
	
	@Override
	protected void onResume() {
		super.onResume();
		if(!mModuleAvailable){
			gMyHandler.sendEmptyMessageDelayed(MSG_RELOAD_MODULE_DELAY, 50);
		}
		else{
			if ( !ifCurrentConnect()){
				Message msg = Message.obtain(gMyHandler, MSG_UHF_POWER_ON,false);
				msg.sendToTarget();
			}

		}

		if (mAdapter != null) {
			List<Header> headers = newGetHeaders();
			if(headers != null && headers.size() > 0)
				initHeaders(headers);
			mAdapter.notifyDataSetChanged();
		}
	}




	@Override
	public void onBuildHeaders(List<Header> headers) {
		loadHeadersFromResource(R.xml.uhf_settings_headers, headers);
		filterHeaders(headers);//过滤显示项
		initHeaders(headers);
//		myHeaders = headers;
	}


	/**
	 * 获取保存的header
	 * @return
	 */
	private List<Header> newGetHeaders(){
		myHeaders.clear();
		loadHeadersFromResource(R.xml.uhf_settings_headers, myHeaders);
		return myHeaders;
	}

	@Override
	public void setListAdapter(ListAdapter paramListAdapter) {
		if (mAdapter==null) {
			mAdapter = new HeaderAdapter(getApplicationContext(), 0, newGetHeaders());
		}
		super.setListAdapter(mAdapter);
	}
	
	
	@Override
	public void onHeaderClick(Header header, int position) 
	{
		if(!mModuleAvailable)
			return ;
		
		UHFModuleInfo moduleInfo = mUHFMgr.getUHFModuleInfo();
		String modulePackage = moduleInfo == null?null:moduleInfo.packageName;
		
		switch ((int) header.id)
		{
		case R.id.power_enable:
			boolean on = mUHFMgr.isPowerOn()?false:true;
			Message msg = Message.obtain(gMyHandler, MSG_UHF_POWER_ON, on);
			msg.sendToTarget();
			break;

		case R.id.inventory_demo:
			if( !mUHFMgr.isPowerOn() )
				break;
			try {
				header.intent.addCategory(Intent.CATEGORY_DEFAULT);
				startActivity(header.intent);
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;

		case R.id.find_device:
			mUHFMgr.setParam("FIND_DEVICE","PARAM_FIND_DEVICE","");
			break;


		case R.id.scan_connect:
			try {
				header.intent.addCategory(Intent.CATEGORY_DEFAULT);
				startActivity(header.intent);
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;

		case R.id.uhf_func_settings:

			if( !mUHFMgr.isPowerOn() )
				break;
			try {
				header.intent.addCategory(Intent.CATEGORY_DEFAULT);
				if(modulePackage != null)
					header.intent.setPackage(modulePackage);
				startActivity(header.intent);
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;
		case R.id.uhf_func_upgrade:

			if( !mUHFMgr.isPowerOn() )
				break;
			try {
				header.intent.addCategory(Intent.CATEGORY_DEFAULT);
				header.intent.setPackage("com.nlscan.upgradebadge");
				startActivity(header.intent);
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;

		case R.id.restore_default:
			showRestoreConfirmWindow(FUN_RESTORE);
			break;

        case  R.id.reset_factory:
            showRestoreConfirmWindow(FUN_FACTORY);
            break;
        default:
			super.onHeaderClick(header, position);
			break;
		}
		
	}






	BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	private boolean mDisconnect = true;
	private String mAddress = "";
	//获取当前连接的设备
	private void getConnectDevice(){

		Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
		for(BluetoothDevice device : bondedDevices) {
			try {

				if (device !=null && device.getName().startsWith("SR")){
					mAddress = device.getAddress();
					break;
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}


	}

	private boolean ifCurrentConnect(){
		if (ifBleConnect()) {

			Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();

			for(BluetoothDevice device : bondedDevices) {
				try {

					if (device !=null && device.getName().startsWith("SR")){
						 return true;
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}



	private boolean ifBleConnect(){
		boolean ifConnect = false;
		String connectStr = mUHFMgr.getParam("BLE_STATE","PARAM_BLE_STATE","false");
		try {
			ifConnect = Boolean.parseBoolean(connectStr);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ifConnect;

	}


	private BroadcastReceiver mBleReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			BluetoothDevice device =  intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

			String currentAddress = device != null ? device.getAddress() : "";
			String intentAddress = intent.getStringExtra("DeviceAddress");
			final String action = intent.getAction();

			if (device == null) return;
			int connectState = device.getBondState();
			Message msg;
			if(action != null){
				switch (action) {

					case BluetoothDevice.ACTION_ACL_CONNECTED:
						break;
					case BluetoothDevice.ACTION_ACL_DISCONNECTED:
						if (device.getName().startsWith("SR")){
							mDisconnect = true;
//							Toast.makeText(context,"蓝牙设备已断开",Toast.LENGTH_SHORT).show();
							msg = Message.obtain(gMyHandler, MSG_UHF_POWER_ON,false);
							msg.sendToTarget();
						}


						break;
				}

			}



		}
	};



	//注册广播
	private void register(){
		//蓝牙广播
		IntentFilter blueFilter = new IntentFilter();

		blueFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		blueFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);

		registerReceiver(mBleReceiver,blueFilter);

	}


	//取消注册
	private void unRegister(){
		try {
			unregisterReceiver(mBleReceiver);
		} catch (Exception e) {
			e.printStackTrace();
		}
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
	protected boolean isValidFragment(String fragmentName) {
		return true;
	}

	
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if(keyCode == KeyEvent.KEYCODE_BACK)
		{
			if(mUHFMgr.isPowerOn())
			{
				showExitPromptWindow();
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}


	@Override
	protected void onDestroy() {

		cancelCheckDialog();
		cancelRestoreDialog();
		cancelMyDialog();
		super.onDestroy();

	}


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
				if(mUHFMgr.isPowerOn())
					showExitPromptWindow();
				else
					finish();
			}
		});
	}
	
	/**
	 * 过滤显示项
	 * @param headers
	 */
	private void filterHeaders(List<PreferenceActivity.Header> headers)
	{
		if (headers != null) {
		}
	}
	
	private void initHeaders(List<PreferenceActivity.Header> headers)
	{
		if (headers != null) 
		{
			for (Header header : headers) 
			{
				switch ((int) header.id) {
				case R.id.uhf_func_settings:
					String model = mUHFMgr.getUHFDeviceModel();
					header.summary = (model == null ?"unknown" : model);
					break;
				}
				
			}//end for
		}

	}// end initHeaders
	
	private void showTriggerMode() {
		new TriggerModeSettingFragment().show(getFragmentManager(), "triggermode");
	}
	
	private void showPromptSetting() {
		new PromptSettingFragment().show(getFragmentManager(), "promptsetting");
	}
	
	
	
	@Override
	protected void uhfPowerOning() {
		gMyHandler.sendEmptyMessage(MSG_UPDATE_VIEW);
		super.uhfPowerOning();
	}


	@Override
	protected void uhfPowerOn() {
		super.uhfPowerOn();
		gMyHandler.sendEmptyMessage(MSG_UPDATE_VIEW);
	}


	@Override
	protected void uhfPowerOff() {
		super.uhfPowerOff();
		gMyHandler.sendEmptyMessage(MSG_UPDATE_VIEW);
	}


	private synchronized void showRestoreConfirmWindow(final int funType)
	{
		if(mRestoreDialog != null)
		{
			if(!mRestoreDialog.isShowing())
				mRestoreDialog.show();
			return ;
		}
		
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

		if (funType == FUN_RESTORE){
            builder.setTitle(R.string.restore_default);
            builder.setMessage(getString(R.string.restore_default_promt));
        }
		else {
            builder.setTitle(R.string.reset_factory);
            builder.setMessage(getString(R.string.factory_promt));
        }

		builder.setPositiveButton(R.string.common_confirm, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				showRestoringWindow();
				new Thread(new Runnable() {
					
					@Override
					public void run() {
						boolean suc = true;
						if (funType == FUN_RESTORE){
						    suc = mUHFMgr.restoreDefaultSettings();
//							mAdapter.notifyDataSetChanged();
                        }
						else{
						    mUHFMgr.setParam("RESTORE_FACTORY","PARAM_RESTORE_FACTORY","");
                        }


						if(suc)
							gMyHandler.sendEmptyMessage(MSG_RESTORE_SUCCESS);
						else
							gMyHandler.sendEmptyMessage(MSG_RESTORE_FAILED);
					}
				}).start();
			}
		});
		
		builder.setNegativeButton(R.string.common_cancel, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		
		mRestoreDialog = builder.create();
		mRestoreDialog.show();
	}
	
	private  synchronized void showRestoringWindow()
	{
		if(mPaused)
			return ;
		
		mRestoreProgDialog = new ProgressDialog(MainActivity.this);
		mRestoreProgDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);// 设置进度条的形式为圆形转动的进度条
		mRestoreProgDialog.setCancelable(true);// 设置是否可以通过点击Back键取消
		mRestoreProgDialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
        // 设置提示的title的图标，默认是没有的，如果没有设置title的话只设置Icon是不会显示图标的
		mRestoreProgDialog.setMessage(getString(R.string.common_onning));
		mRestoreProgDialog.show();
	}
	
	/**
	 * 上电状态下退出时,提示是否下电
	 */
	private void showExitPromptWindow()
	{
		synchronized (this) {
			if(mExitConfirmDialog != null)
			{
				if(!mExitConfirmDialog.isShowing())
					mExitConfirmDialog.show();
				return ;
			}
			
			AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
			builder.setTitle(R.string.common_tip);
			builder.setMessage(getString(R.string.power_off_prompt));
			builder.setPositiveButton(R.string.do_power_down, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mUHFMgr.powerOff();
					finish();
				}
			});
			
			builder.setNegativeButton(R.string.common_no, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					finish();
				}
			});
			
			mExitConfirmDialog = builder.create();
			mExitConfirmDialog.show();
		}
	}
	
	//重新检测模块
	private void reLoadModule()
	{
		Log.d(TAG,"reload module paused is " + mPaused);
		synchronized (this) {
			
			if(mPaused)
				return ;
			
			if(mCheckingProgDialog != null)
			{
				if(!mCheckingProgDialog.isShowing())
					mCheckingProgDialog.show();
			}else{
				mCheckingProgDialog = new ProgressDialog(MainActivity.this);
				mCheckingProgDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);// 设置进度条的形式为圆形转动的进度条
				mCheckingProgDialog.setCancelable(true);// 设置是否可以通过点击Back键取消
				mCheckingProgDialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
		        // 设置提示的title的图标，默认是没有的，如果没有设置title的话只设置Icon是不会显示图标的
				mCheckingProgDialog.setMessage(getString(R.string.loading_uhf_module));
				mCheckingProgDialog.show();
			}
			
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					mUHFMgr.loadUHFModule();
					gMyHandler.sendEmptyMessage(MSG_LOAD_MODULE_COMPLETED);
				}
			}).start();
		}
	}
	
	//未检测到模块,弹出窗口
	private void showReloadModuleWindow()
	{
		Log.d(TAG,"show reload");
		synchronized (this) {
			
			if(mReLoadDialog != null)
			{
				if(!mReLoadDialog.isShowing() ){
					try {
						mReLoadDialog.show();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				return ;
			}
			
			AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
			builder.setTitle(R.string.common_tip);
			builder.setMessage(getString(R.string.uhf_module_unavailable));
			builder.setPositiveButton(R.string.search_again, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					gMyHandler.sendEmptyMessageDelayed(MSG_RELOAD_MODULE_DELAY, 50);
				}
			});

			builder.setNegativeButton(R.string.scan_connect_go, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();

					startActivity(new Intent(MainActivity.this,SearchActivity.class));

//					finish();
				}
			});
			
			mReLoadDialog = builder.create();
			mReLoadDialog.setCanceledOnTouchOutside(false);
			if (!MainActivity.this.isFinishing())
				mReLoadDialog.show();



		}
	}
	
	
	/*---------------------------------------------------------------------------------------------------------------------------------------------------
	 * Inner Class
	 * ---------------------------------------------------------------------------------------------------------------------------------------------------
	 */
	private class HeaderAdapter extends ArrayAdapter<PreferenceActivity.Header> {
		private Context mContext;
		private List<PreferenceActivity.Header> mHeaderList;
		private LayoutInflater mInflater;

		public HeaderAdapter(Context context, int resource, List<PreferenceActivity.Header> headers) {
			super(context, resource, headers);
			mContext = context;
			mInflater = LayoutInflater.from(mContext);
			mHeaderList = headers;
		}

		public int getCount() {
			if (mHeaderList == null)
				return 0;
			return mHeaderList.size();
		}

		public PreferenceActivity.Header getItem(int paramInt) {
			if (mHeaderList == null)
				return null;
			return (PreferenceActivity.Header) mHeaderList.get(paramInt);
		}

		public long getItemId(int paramInt) {
			PreferenceActivity.Header header = getItem(paramInt);
			if (header == null)
				return 0L;
			return header.id;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			PreferenceActivity.Header localHeader = (PreferenceActivity.Header) mHeaderList.get(position);
			Holder localHolder;
			if (convertView == null) {
				localHolder = new Holder();
				convertView = mInflater.inflate(R.layout.list_item_main_face, null);
				localHolder.icon = ((ImageView) convertView.findViewById(R.id.icon));
				localHolder.tv_title = ((TextView) convertView.findViewById(R.id.tv_title));
				localHolder.tv_summary = ((TextView) convertView.findViewById(R.id.tv_summary));
				localHolder._switch = (CheckBox) convertView.findViewById(R.id.switchWidget);
				convertView.setTag(localHolder);

			} else
				localHolder = (Holder) convertView.getTag();

			localHolder.icon.setImageResource(localHeader.iconRes);
			localHolder.icon.setVisibility(View.GONE);
			localHolder.tv_title.setText(localHeader.titleRes);
			localHolder.tv_summary.setVisibility(View.VISIBLE);
			if (localHeader.summaryRes > 0)
				localHolder.tv_summary.setText(localHeader.summaryRes);
			else if (!TextUtils.isEmpty(localHeader.summary)) {
				localHolder.tv_summary.setText(localHeader.summary);
			} else
				localHolder.tv_summary.setVisibility(View.GONE);

			if (localHeader.id == R.id.power_enable) {
				localHolder._switch.setId(R.id.power_enable);
				localHolder._switch.setVisibility(View.VISIBLE);
				localHolder._switch.setChecked(mModuleAvailable && mUHFMgr.isPowerOn());
				localHolder.tv_title.setTextColor( mModuleAvailable ? Color.BLACK : Color.LTGRAY);
			} else if(
					 localHeader.id == R.id.restore_default
					|| localHeader.id == R.id.scan_connect){
				localHolder._switch.setId(R.id.switchWidget);
				localHolder._switch.setVisibility(View.GONE);
				localHolder.tv_title.setTextColor(mModuleAvailable ? Color.BLACK : Color.LTGRAY);
			}else {
				localHolder._switch.setId(R.id.switchWidget);
				localHolder._switch.setVisibility(View.GONE);
				localHolder.tv_title.setTextColor(mModuleAvailable && mUHFMgr.isPowerOn() ? Color.BLACK : Color.LTGRAY);
			}
			return convertView;
		}

		private class Holder {
			public ImageView icon;
			public TextView tv_summary;
			public TextView tv_title;
			public CheckBox _switch;
		}
	}

	@Override
	public void onSettingsChange() {
		gMyHandler.sendEmptyMessage(MSG_UPDATE_VIEW);
	}
}
