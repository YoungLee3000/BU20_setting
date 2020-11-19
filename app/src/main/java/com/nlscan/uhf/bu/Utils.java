package com.nlscan.uhf.bu;

import java.lang.reflect.Field;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.nlscan.android.scan.ScanManager;

public class Utils
{
  public static boolean isValidHexString(String hexString)
  {
    if (!TextUtils.isEmpty(hexString))
      return hexString.matches("[0-9a-fA-F]*");
    return false;
  }

  public static void showSettingFailed(Context paramContext)
  {
    Toast.makeText(paramContext, R.string.failed, Toast.LENGTH_SHORT).show();
  }

  public static void showSettingSuccess(Context paramContext)
  {
    Toast.makeText(paramContext, R.string.success, Toast.LENGTH_SHORT).show();
  }
  
  public static boolean isBlank(String text)
	{
		text = text == null?text:text.replaceAll("\\s", "");
		return text == null||text.length()==0;
	}
  
  public static int getAPIVerionCode()
	{
		try {
			Class<?> scanMgrCls = Class.forName(ScanManager.class.getName());
			Field verisonCodeField = scanMgrCls.getDeclaredField("VERSION_CODE");
			if(verisonCodeField != null){
				int versionCode =  (Integer)verisonCodeField.get(scanMgrCls);
				return versionCode;
			}
		} catch (Exception e) {
			Log.w("TAG", "Get api verion failed.", e);
		}
		
		return -1;
	}

  public static boolean isNquire300() {
	  return "NQuire 300".equals(Build.MODEL);
  }
}
