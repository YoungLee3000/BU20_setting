package com.nlscan.uhf.settings;

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

import com.nlscan.android.uhf.UHFManager;

public class PromptSettingFragment extends AbsDialogFragment {
	private final int MSG_SETTING_FAILED = 10001;
	private final int MSG_SETTING_SUCCESS = 10000;
	private CheckBox cb_sound;
	private CheckBox cb_vibrate;
	private CheckBox cb_led;
	private View mContentView;
	private Context mContext;
	private Dialog mDialog;
	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_SETTING_SUCCESS:
				hideSettingProgressDialog();
				Utils.showSettingSuccess(mContext);
				if (mSettingChangeListener != null)
					mSettingChangeListener.onSettingsChange();
				break;
			case MSG_SETTING_FAILED:
				hideSettingProgressDialog();
				Utils.showSettingFailed(mContext);
				break;
			}
			
		}
	};
	private LayoutInflater mInflater;
	private UHFManager mUHFMgr;
	private ProgressDialog pd;

	private void hideSettingProgressDialog() {
		if (pd != null)
			pd.dismiss();
	}

	private void initView() {
		mContentView = mInflater.inflate(R.layout.activity_scan_prompt, getRootScrollView());
		cb_sound = ((CheckBox) mContentView.findViewById(R.id.cb_sound));
		cb_vibrate = ((CheckBox) mContentView.findViewById(R.id.cb_vibrate));
		cb_led = ((CheckBox) mContentView.findViewById(R.id.cb_led));
		
		cb_led.setVisibility(View.GONE);
	}

	private void showSettingProgressDialog() {
		if (pd == null) {
			pd = new ProgressDialog(getActivity());
			pd.setMessage(getString(R.string.common_setting));
		}
		pd.show();
	}

	private void updateViewData() {
		
		boolean soundEnable = mUHFMgr.isPromptSoundEnable();
		boolean vibrateEnable = mUHFMgr.isPromptVibrateEnable();
		boolean ledEnable = mUHFMgr.isPromptLEDEnable();
		
		cb_sound.setChecked(soundEnable);
		cb_vibrate.setChecked(vibrateEnable);
		cb_led.setChecked(ledEnable);
	}

	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		mContext = getActivity().getApplicationContext();
		mInflater = LayoutInflater.from(getActivity().getApplicationContext());
		mUHFMgr = UHFManager.getInstance();
		initView();
	}

	public Dialog onCreateDialog(Bundle bundle) {
		mDialog = new AlertDialog.Builder(getActivity())
				.setTitle(R.string.scan_prompt)
				.setView(mContentView)
				.setPositiveButton(R.string.common_confirm,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								final boolean sound = cb_sound.isChecked();
								final boolean vibrate = cb_vibrate.isChecked();
								final boolean led=cb_led.isChecked();
								showSettingProgressDialog();
								new Thread(new Runnable() {
									public void run() {
										try {
											boolean bool;
											bool = mUHFMgr.setPromptSoundEnable(sound);
											if(bool)
												bool = mUHFMgr.setPromptVibrateEnable(vibrate);
											if(bool)
												bool = mUHFMgr.setPromptLEDEnable(led);
											
											if (bool)
												mHandler.sendEmptyMessage(MSG_SETTING_SUCCESS);
											else
												mHandler.sendEmptyMessage(MSG_SETTING_FAILED);
											
										} catch (Exception e) {
											e.printStackTrace();
										}
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
		if (bundle==null) {
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
