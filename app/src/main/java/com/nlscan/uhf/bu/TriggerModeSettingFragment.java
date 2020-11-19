package com.nlscan.uhf.bu;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import com.nlscan.android.scan.ScanSettings;
import com.nlscan.android.uhf.UHFCommonParams;
import com.nlscan.android.uhf.UHFManager;

public class TriggerModeSettingFragment extends AbsDialogFragment {
	
	private CheckBox cb_scan_main;
	private CheckBox cb_scan_side;
	private CheckBox cb_scan_back;
	private View mContentView;
	private Context mContext;
	private Dialog mDialog;
	private LayoutInflater mInflater;
	private UHFManager mUHFMgr ;
	private ISettingChangeListener mSettingChangeListener;
	private ProgressDialog pd;
	
	private final int MSG_SETTING_FAILED = 10001;
	private final int MSG_SETTING_SUCCESS = 10000;
	private Handler mHandler = new Handler() {
		public void handleMessage(Message paramAnonymousMessage) {
			switch (paramAnonymousMessage.what) {
			case MSG_SETTING_SUCCESS:
				
				hideSettingProgressDialog();
				Utils.showSettingSuccess(mContext);
				if(mSettingChangeListener!=null)
					mSettingChangeListener.onSettingsChange();
				break;
			case MSG_SETTING_FAILED:
				hideSettingProgressDialog();
				Utils.showSettingFailed(mContext);
				break;
			}
			
		}
	};
	

	public void onCreate(Bundle paramBundle) {
		super.onCreate(paramBundle);
		mContext = getActivity().getApplicationContext();
		mInflater = LayoutInflater.from(getActivity()
				.getApplicationContext());
		mUHFMgr = UHFManager.getInstance();
		initView();
	}
	
	private void hideSettingProgressDialog() {
		if (pd != null)
			pd.dismiss();
	}

	private void initView() {
		
		mContentView = mInflater.inflate(R.layout.activity_trigger_mode, getRootScrollView());
		cb_scan_main = ((CheckBox) mContentView.findViewById(R.id.cb_scan_main));
		cb_scan_side = ((CheckBox) mContentView.findViewById(R.id.cb_scan_side));
		cb_scan_back= ((CheckBox) mContentView.findViewById(R.id.cb_scan_back));
		
		//根据配置,显示指定的设置项
		String settingsShown = ScanSettings.Global.getString(mContext.getContentResolver(), "scan_trigger_settings_shown", null);
		if(settingsShown != null)
		{
			cb_scan_main.setVisibility(View.GONE);
			cb_scan_side.setVisibility(View.GONE);
			cb_scan_back.setVisibility(View.GONE);
			
			String[] shownList = settingsShown.split(",");
			for(String sw : shownList)
			{
				if("1".equals(sw))
					cb_scan_main.setVisibility(View.VISIBLE);
				if("2".equals(sw))
					cb_scan_side.setVisibility(View.VISIBLE);
				if("3".equals(sw))
					cb_scan_back.setVisibility(View.VISIBLE);
			}
		}
		
	}

	private void showSettingProgressDialog() {
		if (pd == null) {
			pd = new ProgressDialog(getActivity());
			pd.setMessage(getString(R.string.common_onning));
		}
		pd.show();
	}

	private void updateViewData()
  {
    
    boolean mainTriggerEnable = mUHFMgr.isTriggerOn(UHFCommonParams.TRIGGER_MODE.TRIGGER_MODE_MAIN);
    boolean leftTriggerEnable = mUHFMgr.isTriggerOn(UHFCommonParams.TRIGGER_MODE.TRIGGER_MODE_LEFT);
    boolean rightTriggerEnable = mUHFMgr.isTriggerOn(UHFCommonParams.TRIGGER_MODE.TRIGGER_MODE_RIGHT);
    boolean backTriggerEnable = mUHFMgr.isTriggerOn(UHFCommonParams.TRIGGER_MODE.TRIGGER_MODE_BACK);
    
    cb_scan_main.setChecked(mainTriggerEnable);
    if(leftTriggerEnable || rightTriggerEnable)
    	cb_scan_side.setChecked(true);
    else
    	cb_scan_side.setChecked(false);
    
    cb_scan_back.setChecked(backTriggerEnable);
  }

	

	public Dialog onCreateDialog(Bundle paramBundle) {
		mDialog = new AlertDialog.Builder(getActivity())
				.setTitle(R.string.trigger_mode)
				.setView(mContentView)
				.setPositiveButton(R.string.common_confirm,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								final boolean bManScan = cb_scan_main.isChecked();
								final boolean bSideScan = cb_scan_side.isChecked();
								final boolean bBackScan = cb_scan_back.isChecked();
								
								showSettingProgressDialog();
								new Thread(new Runnable() {
									public void run() {
										boolean bool = mUHFMgr.setTrigger(UHFCommonParams.TRIGGER_MODE.TRIGGER_MODE_MAIN,bManScan);
										if(bool)
											bool = mUHFMgr.setTrigger(UHFCommonParams.TRIGGER_MODE.TRIGGER_MODE_LEFT,bSideScan);
										if(bool)
											bool = mUHFMgr.setTrigger(UHFCommonParams.TRIGGER_MODE.TRIGGER_MODE_RIGHT,bSideScan);
										if(bool)
											bool = mUHFMgr.setTrigger(UHFCommonParams.TRIGGER_MODE.TRIGGER_MODE_BACK,bBackScan);
										
										if(bool) {
											mHandler.sendEmptyMessage(MSG_SETTING_SUCCESS);
										}else
											mHandler.sendEmptyMessage(MSG_SETTING_FAILED);
									}
								}).start();
							}
						})
				.setNegativeButton(R.string.common_cancel,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
							}
						}).create();
		// 刚弹出对话框时候需求刷新一下当前配置
		if (paramBundle==null) {
			updateViewData();
		}
		return mDialog;
	}

/*	
	public void onPause() {
		super.onPause();
		if (mDialog != null)
			mDialog.dismiss();
	}

	public void onResume() {
		super.onResume();
		updateViewData();
		if ((mDialog != null) && (!mDialog.isShowing()))
			mDialog.show();
	}
*/
}

