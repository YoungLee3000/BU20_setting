package com.nlscan.uhf.settings;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;

public abstract class AbsDialogFragment extends DialogFragment
{
	protected ISettingChangeListener mSettingChangeListener;
	
	protected ScrollView getRootScrollView() {
		ScrollView scrollView = new ScrollView(getActivity().getApplicationContext());
		scrollView.setScrollbarFadingEnabled(false);
		return scrollView;
	}
	
	@Override
	public void onActivityCreated (Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity!=null && (activity instanceof ISettingChangeListener)) {
			mSettingChangeListener = (ISettingChangeListener) activity;
		}
	}
}
