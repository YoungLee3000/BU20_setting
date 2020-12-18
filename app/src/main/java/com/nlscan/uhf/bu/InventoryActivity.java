package com.nlscan.uhf.bu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.nlscan.android.uhf.TagInfo;
import com.nlscan.android.uhf.UHFManager;
import com.nlscan.android.uhf.UHFReader;
import com.nlscan.luggage.DataKey;



public class InventoryActivity extends BaseActivity {

	
	/**读码结果发送的广播action*/
	public final static String ACTION_UHF_RESULT_SEND = "nlscan.intent.action.uhf.ACTION_RESULT";
	/**读码结果发送的广播Extra*/
	public final static String EXTRA_TAG_INFO = "tag_info";
	
	private Button btn_power_on,
								btn_power_off,
								btn_start_read,
								btn_stop_read,
								btn_clear,
								btn_lock,
								btn_save,
								btn_settings;
	
	private TextView tv_once,
										tv_state,
										tv_tags, 
										tv_costt,
										tv_total_freq,
										tv_span_time;
	
	private Context mContext;
	private UHFManager mUHFMgr = UHFManager.getInstance();
	private ListView listView;
	private MyAdapter Adapter;
	
	Map<String, TagInfo> TagsMap = new LinkedHashMap<String, TagInfo>();// 有序
	private int gTagTotalFreq = 0;
	private List<Map<String, ?>> ListMs = new ArrayList<Map<String, ?>>();
	private String[] Coname = new String[] { "序号", "EPC ID", "次数", "天线", "协议", "RSSI", "频率", "附加数据" };//表头


	private Map<String, String> listHeader = new HashMap<String, String>(); 
	
	private long exittime;

	private long gStartReadTime = 0L;
	
	private HandlerThread mHandlerThread;
	private ResultHandler mResultHandler;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_inventory_demo);
		mContext = getApplicationContext();
		Coname = getResources().getStringArray(R.array.inventory_table_header);
		
		mHandlerThread = new HandlerThread("mHandlerThread",android.os.Process.THREAD_PRIORITY_BACKGROUND);
		mHandlerThread.start();
		mResultHandler = new ResultHandler(mHandlerThread.getLooper());
		
		initActionBar();
		initView();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN)
		{
			if ((System.currentTimeMillis() - exittime) > 2000) {
				Toast.makeText(getApplicationContext(), R.string.press_again_to_exit, Toast.LENGTH_SHORT).show();
				exittime = System.currentTimeMillis();
			}else
				return super.onKeyDown(keyCode, event);
		}
		
		AudioManager audioManager  = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
	    switch (keyCode) {
	        case KeyEvent.KEYCODE_VOLUME_UP:
	            audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_RAISE,AudioManager.FX_FOCUS_NAVIGATION_UP);
	            return true;
	        case KeyEvent.KEYCODE_VOLUME_DOWN:
	            audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_LOWER,AudioManager.FX_FOCUS_NAVIGATION_UP);
	            return true;
	    }

		return true;
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		unRegisterResultReceiver();
	}


	@Override
	protected void onResume() {
		super.onResume();
		registerResultReceiver();
		btn_start_read.setEnabled(mUHFMgr.isPowerOn());
		btn_stop_read.setEnabled(mUHFMgr.isPowerOn());
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		stopReading();
		//powerOff();
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
				finish();
			}
		});
	}

	private void initView()
	{

		FileUtil.createDir(filePath);

		btn_power_on = (Button) findViewById(R.id.btn_power_on);
		btn_power_off =  (Button)  findViewById(R.id.btn_power_off);
		btn_start_read = (Button)  findViewById(R.id.btn_start_read);
		btn_stop_read =  (Button) findViewById(R.id.btn_stop_read);
		btn_clear =  (Button) findViewById(R.id.btn_clear);
		btn_lock =  (Button) findViewById(R.id.btn_lock);
		btn_settings =  (Button) findViewById(R.id.btn_settings);
		btn_save = (Button) findViewById(R.id.btn_save);
		tv_span_time =  (TextView) findViewById(R.id.tv_span_time);
		
		btn_power_on.setVisibility(View.GONE);
		btn_power_off.setVisibility(View.GONE);
		btn_lock.setVisibility(View.GONE);
		btn_settings.setVisibility(View.GONE);
		
		listView = (ListView) findViewById(R.id.listView_epclist);
		
		tv_once = (TextView) findViewById(R.id.textView_readoncecnt);
		tv_tags = (TextView) findViewById(R.id.textView_readallcnt);
		tv_total_freq = (TextView) findViewById(R.id.textView_read_total_freq);
		
		
		for (int i = 0; i < Coname.length; i++)
			listHeader.put(Coname[i], Coname[i]);
		
		ListMs.add(listHeader);
		Adapter = new MyAdapter(mContext, ListMs, R.layout.listitemview_inv, Coname, new int[] { R.id.textView_readsort, R.id.textView_readepc, R.id.textView_readcnt,
			R.id.textView_readant, R.id.textView_readpro, R.id.textView_readrssi, R.id.textView_readfre, R.id.textView_reademd });
		
		listView.setAdapter(Adapter);
		
		btn_power_on.setOnClickListener(mClick);
		btn_power_off.setOnClickListener(mClick);
		btn_start_read.setOnClickListener(mClick);
		btn_stop_read.setOnClickListener(mClick);
		btn_clear.setOnClickListener(mClick);
		btn_lock.setOnClickListener(mClick);
		btn_save.setOnClickListener(mClick);
		btn_settings.setOnClickListener(mClick);
	}
	
	private void registerResultReceiver()
	{
		try {
			IntentFilter iFilter = new IntentFilter(ACTION_UHF_RESULT_SEND);
			mContext.registerReceiver(mResultReceiver, iFilter);
		} catch (Exception e) {
		}
		
	}
	
	private void unRegisterResultReceiver()
	{
		try {
			mContext.unregisterReceiver(mResultReceiver);
		} catch (Exception e) {
		}
		
	}
	
	/**
	 * 上电(连接)
	 */
	private void powerOn()
	{
		UHFReader.READER_STATE er = UHFReader.READER_STATE.CMD_FAILED_ERR;
		er = mUHFMgr.powerOn();
		Toast.makeText(getApplicationContext(), "Power on :"+er.toString(), Toast.LENGTH_SHORT).show();
	}
	
	/**
	 * 下电(断开)
	 */
	private void powerOff()
	{
		UHFReader.READER_STATE er = UHFReader.READER_STATE.CMD_FAILED_ERR;
		er = mUHFMgr.powerOff();
		Toast.makeText(getApplicationContext(), "Power off :"+er.toString(), Toast.LENGTH_SHORT).show();
	}
	
	/**
	 * 开始扫描
	 */
	private void startReading()
	{
		UHFReader.READER_STATE er = UHFReader.READER_STATE.CMD_FAILED_ERR;
		er= mUHFMgr.startTagInventory();
		gStartReadTime = System.currentTimeMillis();
		if(er == UHFReader.READER_STATE.OK_ERR)
			keepScreen();
		else
			Toast.makeText(getApplicationContext(), "Start reading :"+er.toString(), Toast.LENGTH_SHORT).show();
	}
	
	/**
	 * 停止扫描
	 */
	private UHFReader.READER_STATE stopReading()
	{
		UHFReader.READER_STATE er = UHFReader.READER_STATE.CMD_FAILED_ERR;
		er = mUHFMgr.stopTagInventory();
		releseScreenLock();
		return er;
		//Toast.makeText(getApplicationContext(), "Stop reading :"+er.toString(), Toast.LENGTH_SHORT).show();
	}
	
	/**
	 * 清除数据
	 */
	private void clearData()
	{
		
		tv_once.setText(String.valueOf(0));
		tv_tags.setText(String.valueOf(0));
		tv_total_freq.setText(String.valueOf(0));
		tv_span_time.setText(String.valueOf(0));

		gTagTotalFreq = 0;
		
		if(TagsMap != null)
			TagsMap.clear();
		
		if(ListMs != null){
			ListMs.clear();
			ListMs.add(listHeader);
		}
		
		Adapter.notifyDataSetChanged();
		
	}


	private static final String CSV_FILE_1 = "/sdcard/myLuggage/flight_data_1.json";
	private static final String CSV_FILE_2 = "/sdcard/myLuggage/flight_data_2.json";
	private static final String CSV_FILE_3 = "/sdcard/myLuggage/flight_data_3.json";
	private static final String CSV_FILE_3_1 = "/sdcard/myLuggage/flight_data_3_1.json";
	private static final String CSV_FILE_4 = "/sdcard/myLuggage/flight_data_4.json";
	private String filePath = "/sdcard/myLuggage/";

	//保存UHF标签成json文件
	private void saveData(){

		JSONArray relArray = new JSONArray();

		int size = ListMs.size();
		int halfSize = size / 2;


		JSONObject jsonObject1 = new JSONObject();
		jsonObject1.put(DataKey.J_FLIGHT_ID,"H120");
		jsonObject1.put(DataKey.J_FLIGHT_TIME,"2020-10-10 15:45:00");
		jsonObject1.put(DataKey.J_BOX_START,"BJ");
		jsonObject1.put(DataKey.J_BOX_DEST,"SH");
		JSONArray epcArray1 = new JSONArray();
		for (int i=1; i<halfSize + 1; i++){
			Map<String,?> map =    ListMs.get(i);
			String epcId =(String) map.get(Coname[1]);
			JSONObject epcBox = new JSONObject();
			epcBox.put(DataKey.J_EPC_ID,epcId);
			epcBox.put(DataKey.J_BOX_ID,epcId);
			epcArray1.add(epcBox);
		}
		jsonObject1.put(DataKey.JA_EPC_BOX_ARRAY,epcArray1);

		relArray.add(jsonObject1);



		JSONObject jsonObject2 = new JSONObject();
		jsonObject2.put(DataKey.J_FLIGHT_ID,"H220");
		jsonObject2.put(DataKey.J_FLIGHT_TIME,"2020-10-10 14:45:00");
		jsonObject2.put(DataKey.J_BOX_START,"PL");
		jsonObject2.put(DataKey.J_BOX_DEST,"TH");
		JSONArray epcArray2 = new JSONArray();
		for (int i=halfSize+1; i<size; i++){
			Map<String,?> map =    ListMs.get(i);
			String epcId =(String) map.get(Coname[1]);
			JSONObject epcBox = new JSONObject();
			epcBox.put(DataKey.J_EPC_ID,epcId);
			epcBox.put(DataKey.J_BOX_ID,epcId);
			epcArray2.add(epcBox);
		}
		jsonObject2.put(DataKey.JA_EPC_BOX_ARRAY,epcArray2);

		relArray.add(jsonObject2);


		String relStr = relArray.toJSONString();

		FileUtil.writeFile(relStr,CSV_FILE_1);
		FileUtil.writeFile(relStr,CSV_FILE_2);
		FileUtil.writeFile(relStr,CSV_FILE_3);
		FileUtil.writeFile(relStr,CSV_FILE_3_1);
		FileUtil.writeFile(relStr,CSV_FILE_4);


		Toast.makeText(this,"保存成功!",Toast.LENGTH_SHORT).show();



	}
	
	private void assesResult(Parcelable[] tagInfos)
	{
		if(tagInfos != null && tagInfos.length > 0)
		{
			
			for(int i =0 ;i < tagInfos.length; i++)
			{
				TagInfo tag = (TagInfo) tagInfos[i];
				String epcId = HexUtil.bytesToHexString(tag.EpcId);
					
				if (!TagsMap.containsKey(epcId)) {
					TagsMap.put(epcId, tag);

					// show
					Map<String, String> m = new HashMap<String, String>();
					m.put(Coname[0], String.valueOf(TagsMap.size()));

					String epcstr =epcId;
					if (epcstr.length() < 24)
						epcstr = String.format("%-24s", epcstr);

					m.put(Coname[1], epcstr);
					String cs = m.get(getString(R.string.inventory_count));
					if (cs == null)
						cs = "0";
					int isc = Integer.parseInt(cs) + tag.ReadCnt;

					gTagTotalFreq += tag.ReadCnt;

					m.put(Coname[2], String.valueOf(isc));
					m.put(Coname[3], String.valueOf(tag.AntennaID));
					m.put(Coname[4], getProtocol(tag.protocol.value()));
					m.put(Coname[5], String.valueOf(tag.RSSI));
					m.put(Coname[6], String.valueOf(tag.Frequency));

					if (tag.EmbededDatalen > 0) {
						String out = HexUtil.bytesToHexString(tag.EmbededData);
						m.put(Coname[7], String.valueOf(out));

					} else
						m.put(Coname[7], "                 ");

					ListMs.add(m);
				} else {
					TagInfo tf = TagsMap.get(epcId);
					if(tag.EmbededDatalen > 0){
						tf.EmbededDatalen = tag.EmbededDatalen;
						tf.EmbededData = tag.EmbededData;
					}
					String epcstr = epcId;
					if (epcstr.length() < 24)
						epcstr = String.format("%-24s", epcstr);

					for (int k = 0; k < ListMs.size(); k++) {
						Map<String, String> m = (Map<String, String>) ListMs.get(k);
						if (m.get(Coname[1]).equals(epcstr)) {
							gTagTotalFreq += tag.ReadCnt;
							tf.ReadCnt += tag.ReadCnt;
							tf.RSSI = tag.RSSI;
							tf.Frequency = tag.Frequency;

							m.put(Coname[2], String.valueOf(tf.ReadCnt));
							m.put(Coname[5], String.valueOf(tf.RSSI));
							m.put(Coname[6], String.valueOf(tf.Frequency));
							
							if (tf.EmbededDatalen > 0) {
								String out = HexUtil.bytesToHexString(tag.EmbededData);
								m.put(Coname[7], String.valueOf(out));

							} else
								m.put(Coname[7], "                 ");
							
							break;
						}
					}
				}
			}//end for
			
//			gTagTotalFreq += tagInfos.length;
		}//end if
	}

	private String getProtocol(int val){
	    String strProtocol ="";
	    switch (val){
            case 0:
                strProtocol = "NONE";
                break;
            case 3:
                strProtocol = "ISO180006B";
                break;
            case 5:
                strProtocol = "GEN2";
                break;
            case 6:
                strProtocol = "ISO180006B_UCODE";
                break;
            case 7:
                strProtocol = "IPX64";
                break;
            case 8:
                strProtocol = "IPX256";
                break;
        }
        return strProtocol;
    }


	
	PowerManager.WakeLock wl;
	private void keepScreen()
    {
		Log.d("TAG", "Wake up screen.");
		
		/*PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
				| PowerManager.ON_AFTER_RELEASE, "bright");
    	// 点亮屏幕
		wl.acquire();*/
		
//    	getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED  
//                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD  
//                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON  
//                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
	
	private void releseScreenLock()
    {
//    	getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED  
//                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD  
//                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON  
//                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    	/*if(wl != null && wl.isHeld()){
    		wl.release();
    		wl = null;
    	}*/
		
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); 
    }
	
	private View.OnClickListener mClick = new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			
			switch (v.getId()) {
			case R.id.btn_power_on:
				powerOn();
				break;
			case R.id.btn_power_off:
				powerOff();
				break;
			case R.id.btn_start_read:
				btn_start_read.setEnabled(false);
				btn_stop_read.setEnabled(true);
				startReading();
				break;
			case R.id.btn_stop_read:
				btn_stop_read.setEnabled(false);
				btn_start_read.setEnabled(true);
				UHFReader.READER_STATE er = stopReading();
				if(er != UHFReader.READER_STATE.OK_ERR)
					Toast.makeText(getApplicationContext(), "Stop reading :"+er.toString(), Toast.LENGTH_SHORT).show();
				break;
			case R.id.btn_clear:
				clearData();
				break;
			case R.id.btn_save:
				saveData();
				break;

			}
		}
	};
	
	public class MyAdapter extends SimpleAdapter
	{
		 private int cr;
		 
		public MyAdapter(Context context, List<? extends Map<String, ?>> data,
				int resource, String[] from, int[] to)
		{
			super(context, data, resource, from, to);
			cr=Color.WHITE;
		}
	    public void setColor(int color)
	    {
	    	cr=color;
	    }
	   
		@Override      
		public View getView(final int position, View convertView, ViewGroup parent)
		{           
			// listview每次得到一个item，都要view去绘制，通过getView方法得到view           
			// position为item的序号           
			View view = null;           
			if (convertView != null) {
				view = convertView;
				// 使用缓存的view,节约内存
				// 当listview的item过多时，拖动会遮住一部分item，被遮住的item的view就是convertView保存着。
				// 当滚动条回到之前被遮住的item时，直接使用convertView，而不必再去new view()
				} else {
					view = super.getView(position, convertView, parent);
					}
			int[] colors = {cr, Color.rgb(219, 238, 244) };//RGB颜色 
			 view.setBackgroundColor(colors[position % 2]);// 每隔item之间颜色不同 
			 //Log.d("MYINFO", "getview:"+String.valueOf(position));
			 return super.getView(position, view, parent); 
		}
	}//end MyAdapter
	
	private final static int MSG_REFRESH_RESULT_LIST = 0x01;
	
	private Handler mUIHandler = new Handler(){

		@Override
		public void handleMessage(Message msg) {
			
			switch (msg.what) {
				
			case MSG_REFRESH_RESULT_LIST:
				
				Parcelable[] results = (Parcelable[]) msg.obj;
				assesResult(results);
				int cll = TagsMap.size();
				if (cll < 0)
					cll = 0;
				
				int curTagCount = results.length;
				int totalTagCount = cll;
				
				tv_once.setText(String.valueOf(curTagCount));
				tv_tags.setText(String.valueOf(totalTagCount));
				tv_total_freq.setText(String.valueOf(gTagTotalFreq));
				Adapter.notifyDataSetChanged();
				break;
			}
		}
		
	};
	
	private BroadcastReceiver mResultReceiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) 
		{
			String action = intent.getAction();
			if(!ACTION_UHF_RESULT_SEND.equals(action))
				return ;
			Parcelable[] tagInfos =  intent.getParcelableArrayExtra(EXTRA_TAG_INFO);
			long startReading = intent.getLongExtra("extra_start_reading_time", 0l);
			
			Message msg = Message.obtain(mUIHandler,  MSG_REFRESH_RESULT_LIST,tagInfos);
			mUIHandler.sendMessage(msg);
			
			if(gStartReadTime > 0)
			{
				long spanTime = System.currentTimeMillis() - gStartReadTime;
				long secTime = spanTime/1000;
				long minutes = secTime/60;
				long hours = minutes /60;
				
				int minute = (int)(minutes - hours * 60);
				int second =  (int)(secTime - minutes * 60);
				
				String sTime = hours+"h:"+minute+"m:"+second+"s";
				//tv_span_time.setText(String.valueOf(startReading));
				tv_span_time.setText(sTime);
			}
			
			
		}//end onReceiver
	};
	
	private class ResultHandler extends Handler
	{

		public ResultHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
		}//end
	}//end
	
	@Override
	protected void uhfPowerOning() {
		super.uhfPowerOning();
	}

	@Override
	protected void uhfPowerOn() {
		super.uhfPowerOn();
		btn_start_read.setEnabled(mUHFMgr.isPowerOn());
		btn_stop_read.setEnabled(mUHFMgr.isPowerOn());
	}

	@Override
	protected void uhfPowerOff() {
		super.uhfPowerOff();
		btn_start_read.setEnabled(false);
		btn_stop_read.setEnabled(false);
	}

}
