package com.nlscan.uhf.settings;

import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.util.Log;

import androidx.core.app.ActivityCompat;

public class PermissionUtils {
	 
	private final static String TAG = "PermissionUtils";
    
    /**
     * 请求所有的动态权限
     * @param ctx
     * @param activity
     * @return
     */
    public static boolean requestAllRuntimePermission(Activity activity)
	{
    	if(android.os.Build.VERSION.SDK_INT < 23) //SDK >= 23之后才需要动态申请permission;
    		return true;
    	
    	Context ctx = activity.getApplicationContext();
		PackageManager pm = ctx.getPackageManager();
		String packageName = ctx.getPackageName();
		
		try {
			PackageInfo pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
			if(pkgInfo == null)
				return false;
			
			String[] permissions = pkgInfo.requestedPermissions;
			
			return requestRuntimePermission(activity, permissions);
			
		} catch (Exception e) {
			Log.w(TAG, "Request all runtime permission failed.", e);
		}
		
		return false;
	}
    
	public static boolean requestRuntimePermission(Activity activity,String[] permissions) throws Exception
	{
		if(permissions != null && permissions.length > 0)
		{
			List<String> rumtimePermissionList = new ArrayList<String>();
			for(String permissionName : permissions)
			{
				if(isRuntime(activity.getApplicationContext(),permissionName)){ //动态权限
					Log.d(TAG, "Runtime permission : "+permissionName);
					rumtimePermissionList.add(permissionName);
				}
			}
			
			rumtimePermissionList.remove(Manifest.permission.READ_EXTERNAL_STORAGE);
			rumtimePermissionList.remove(Manifest.permission.WRITE_EXTERNAL_STORAGE);
			
			rumtimePermissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
			rumtimePermissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
			if(rumtimePermissionList.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
				
			}
			
			if(rumtimePermissionList.size() > 0)
			{
				String[] REQUEST_RUNTIME_PERMISSIONS = new String[rumtimePermissionList.size()];
				rumtimePermissionList.toArray(REQUEST_RUNTIME_PERMISSIONS);
				 ActivityCompat.requestPermissions(activity, REQUEST_RUNTIME_PERMISSIONS,
						 REQUEST_RUNTIME_PERMISSIONS.length);
			}
		}
		
		return true;
		
	}//end requestRuntimePermission
	
    public  static boolean isRuntime(Context ctx,String permissionName){
		PackageManager pm = ctx.getPackageManager();
		try {
			PermissionInfo permissionInfo = pm.getPermissionInfo(permissionName, 0);
			if(permissionInfo != null)
			{
				int protectionLevel = permissionInfo.protectionLevel;
				
				return (protectionLevel & PermissionInfo.PROTECTION_MASK_BASE)
		                == PermissionInfo.PROTECTION_DANGEROUS;
			}
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		return false;
	}
}
