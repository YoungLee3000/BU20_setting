package com.nlscan.uhf.bu;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.PreferenceActivity;

import com.nlscan.android.uhf.UHFManager;

public class BasePrefenceActivity extends PreferenceActivity {

	protected boolean mPaused = false;
	
	protected ProgressDialog mDialog = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		registerUHFStateReceiver();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unRegisterUHFStateReceiver();
	}

	
	@Override
	protected void onResume() {
		super.onResume();
		mPaused = false;
	}

	@Override
	protected void onPause() {
		super.onPause();
//		mPaused = true;
	}
	
	protected void registerUHFStateReceiver()
	{
		IntentFilter iFilter = new IntentFilter(UHFManager.ACTOIN_UHF_STATE_CHANGE);
		getApplicationContext().registerReceiver(mUHFStateReceiver, iFilter);
	}
	
	protected void unRegisterUHFStateReceiver()
	{
		try {
			getApplicationContext().unregisterReceiver(mUHFStateReceiver);
		} catch (Exception e) {
		}
	}

	protected void showLoadingWindow()
	{
		if(mPaused)
			return ;
		
		if(mDialog != null && mDialog.isShowing())
			return ;
		mDialog = new ProgressDialog(BasePrefenceActivity.this);
		mDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);// 设置进度条的形式为圆形转动的进度条
		mDialog.setCancelable(false);// 设置是否可以通过点击Back键取消
		mDialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
        // 设置提示的title的图标，默认是没有的，如果没有设置title的话只设置Icon是不会显示图标的
		mDialog.setMessage(getString(R.string.power_oning));
		mDialog.show();
	}
	
	protected void uhfPowerOning()
	{
		showLoadingWindow();
	}
	
	protected void uhfPowerOn()
	{
		if(mDialog != null )
			mDialog.dismiss();
	}
	
	protected void uhfPowerOff()
	{
		if(mDialog != null )
			mDialog.dismiss();
	}
	
	private BroadcastReceiver mUHFStateReceiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			if(UHFManager.ACTOIN_UHF_STATE_CHANGE.equals(intent.getAction()))
			{
				int uhf_state = intent.getIntExtra(UHFManager.EXTRA_UHF_STATE, -1);
				switch (uhf_state) {
				case UHFManager.UHF_STATE_POWER_ONING:
					uhfPowerOning();
					break;
				case UHFManager.UHF_STATE_POWER_ON:
					uhfPowerOn();
					break;
				case UHFManager.UHF_STATE_POWER_OFF:
					uhfPowerOff();
					break;
				}
			}
		}
	};
	
}
