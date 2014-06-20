package com.video.user;

import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.video.R;
import com.video.data.PreferData;
import com.video.data.Value;
import com.video.data.XmlMessage;
import com.video.main.MainActivity;
import com.video.play.TunnelCommunication;
import com.video.service.BackstageService;
import com.video.socket.ZmqCtrl;
import com.video.socket.ZmqHandler;
import com.video.socket.ZmqThread;
import com.video.utils.UpdateAPK;
import com.video.utils.Utils;
import com.video.utils.WiFiAlertDialog;

public class LoginActivity extends Activity implements OnClickListener {

	private Context mContext;
	private PreferData preferData = null;
	
	private EditText et_name;
	private EditText et_pwd;
	private boolean isAutoLogin = false;
	private CheckBox cb_auto_login;
	private int loginTimes = 0;
	
	private Button button_delete_username;
	private Button button_delete_password;
	private Dialog mDialog = null;
	
	private String userName = "";
	private String userPwd = "";
	
	private final int IS_LOGINNING = 1;
	private final int LOGIN_TIMEOUT = 2;
	private final int LOGIN_AGAIN = 3;
	private final int SEARCH_TIMEOUT = 4;
	
	private LocalDeviceReceiver localDeviceReceiver = null;
	private ArrayList<HashMap<String, String>> localDeviceList = null;
	private WiFiAlertDialog localDeviceDialog = null;
	private LocalDeviceAdapter localDeviceAdapter = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);
		
		initView();
		
		if (!Value.isManulLogout) {
			new UpdateAPK(LoginActivity.this).startCheckUpgadeThread();
		}
	}
	
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		initData();
	}
	
	private void initView() {
		
		//注册广播
		localDeviceReceiver = new LocalDeviceReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(Value.SEARCH_LOCAL_DEVICE_ACTION);
		registerReceiver(localDeviceReceiver, filter);
		
		cb_auto_login = (CheckBox) super.findViewById(R.id.cb_auto_login);
		cb_auto_login.setOnCheckedChangeListener(new onCheckedChangeListenerImpl());
		
		Button button_login = (Button) super.findViewById(R.id.btn_login);
		button_login.setOnClickListener(this);
		
		Button button_register = (Button)super.findViewById(R.id.btn_login_regist);
		button_register.setOnClickListener(this);
		
		Button button_find_pwd = (Button)super.findViewById(R.id.btn_login_find);
		button_find_pwd.setOnClickListener(this);
		
		button_delete_username = (Button)super.findViewById(R.id.btn_login_username_del);
		button_delete_username.setOnClickListener(this);
		
		button_delete_password = (Button)super.findViewById(R.id.btn_login_password_del);
		button_delete_password.setOnClickListener(this);
		
		et_name = (EditText) super.findViewById(R.id.et_login_username);
		et_name.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (s.length() == 0) {
					button_delete_username.setVisibility(View.INVISIBLE);
				} else {
					button_delete_username.setVisibility(View.VISIBLE);
				}
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		
		et_pwd = (EditText) super.findViewById(R.id.et_login_password);
		et_pwd.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (s.length() == 0) {
					button_delete_password.setVisibility(View.INVISIBLE);
				} else {
					button_delete_password.setVisibility(View.VISIBLE);
				}
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		ImageView login_demo = (ImageView) this.findViewById(R.id.iv_login_demo);
		login_demo.setOnClickListener(this);
	}
	
	private void initData() {
		mContext = LoginActivity.this;
		ZmqHandler.mHandler = handler;
		if (preferData == null) {
			preferData = new PreferData(mContext);
		}
		
		if (preferData.isExist("AutoLogin")) {
			isAutoLogin = preferData.readBoolean("AutoLogin");
		}
		if (isAutoLogin) {
			cb_auto_login.setChecked(true);
		} else {
			cb_auto_login.setChecked(false);
		}
		
		if (preferData.isExist("UserName")) {
			userName = preferData.readString("UserName");
			et_name.setText(userName);
		}
		if (preferData.isExist("UserPwd")) {
			userPwd = preferData.readString("UserPwd");
			et_pwd.setText(userPwd);
		}
	}
	
	/**
	 * 生成JSON的登录字符串
	 */
	private String generateLoginJson(String username, String pwd) {
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("type", "Client_Login");
			jsonObj.put("UserName", username);
			jsonObj.put("Pwd", Utils.CreateMD5Pwd(pwd));
			return jsonObj.toString();
		} catch (JSONException e) {
			System.out.println("MyDebug: generateLoginJson()异常！");
			e.printStackTrace();
		}
		return null;
	}
	
	private Handler handler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			super.handleMessage(msg);
			switch (msg.what) {
				case IS_LOGINNING:
					mDialog = Utils.createLoadingDialog(mContext, "正在登录...");
					mDialog.show();
					break;
				case LOGIN_TIMEOUT:
					loginTimes ++;
					if (handler.hasMessages(LOGIN_TIMEOUT)) {
						handler.removeMessages(LOGIN_TIMEOUT);
					}
					//超时之后关闭服务，断开连接，再重启服务
					ZmqCtrl.getInstance().exit();
			    	stopService(new Intent(mContext, BackstageService.class));
					Value.resetValues();
					sendHandlerMsg(LOGIN_AGAIN, 3000);
					break;
				case LOGIN_AGAIN:
					ZmqCtrl.getInstance().init();
					if (loginTimes >= 3) {
						loginTimes = 0;
						if (mDialog != null) {
							mDialog.dismiss();
							mDialog = null;
						}
						Toast.makeText(mContext, "登录超时，请重试！", Toast.LENGTH_SHORT).show();
					} else {
						Handler sendHandler = ZmqThread.zmqThreadHandler;
						String data = generateLoginJson(userName, userPwd);
						sendHandlerMsg(LOGIN_TIMEOUT, Value.REQ_TIME_6S);
						sendHandlerMsg(sendHandler, R.id.zmq_send_data_id, data);
					}
					break;
				case SEARCH_TIMEOUT:
					if ((mDialog != null) && (mDialog.isShowing())) {
						Toast.makeText(mContext, "搜索完毕，暂无本地设备", Toast.LENGTH_SHORT).show();
						mDialog.dismiss();
					}
					if (handler.hasMessages(SEARCH_TIMEOUT)) {
						handler.removeMessages(SEARCH_TIMEOUT);
					}
					break;
				case R.id.login_id:
					if (handler.hasMessages(LOGIN_TIMEOUT)) {
						handler.removeMessages(LOGIN_TIMEOUT);
						if (mDialog != null) {
							mDialog.dismiss();
							mDialog = null;
						}
						loginTimes = 0;
						int resultCode = msg.arg1;
						if (resultCode == 0) {
							//登录成功
							Value.isLoginSuccess = true;
							startActivity(new Intent(mContext, MainActivity.class));
							LoginActivity.this.finish();
						} else {
							Toast.makeText(mContext, "登录失败，"+Utils.getErrorReason(resultCode), Toast.LENGTH_SHORT).show();
						}
					} else {
						handler.removeMessages(R.id.login_id);
					}
					break;
			}
		}
	};
	
	/**
	 * 发送Handler消息
	 */
	private void sendHandlerMsg(int what) {
		Message msg = new Message();
		msg.what = what;
		handler.sendMessage(msg);
	}
	private void sendHandlerMsg(int what, int timeout) {
		Message msg = new Message();
		msg.what = what;
		handler.sendMessageDelayed(msg, timeout);
	}
	private void sendHandlerMsg(Handler handler, int what, String obj) {
		Message msg = new Message();
		msg.what = what;
		msg.obj = obj;
		handler.sendMessage(msg);
	}
	
	private class onCheckedChangeListenerImpl implements OnCheckedChangeListener {

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			// TODO Auto-generated method stub
			if (preferData == null) {
				preferData = new PreferData(mContext);
			}
			preferData.writeData("AutoLogin", isChecked);
		}
	}
	
	public void clickLoginEvent() {
		if (Utils.isNetworkAvailable(mContext)) {
			if (checkRegisterData()) {
				if (preferData.isExist("UserName")) {
					//切换账号
					if (!userName.equals(preferData.readString("UserName"))) {
						if (preferData.isExist("AlarmCount")) {
							preferData.deleteItem("AlarmCount");
				        }
						MainActivity.setAlarmIconAndText(0);
					}
					XmlMessage xmlData = new XmlMessage(mContext);
					xmlData.deleteAllItem();
					preferData.deleteItem("UserName");
					preferData.writeData("UserName", userName);
				} else {
					preferData.writeData("UserName", userName);
				}
				
				if (preferData.isExist("UserPwd")) {
					preferData.deleteItem("UserPwd");
					preferData.writeData("UserPwd", userPwd);
				} else {
					preferData.writeData("UserPwd", userPwd);
				}
				Handler sendHandler = ZmqThread.zmqThreadHandler;
				String data = generateLoginJson(userName, userPwd);
				sendHandlerMsg(IS_LOGINNING);
				sendHandlerMsg(LOGIN_TIMEOUT, Value.REQ_TIME_6S);
				sendHandlerMsg(sendHandler, R.id.zmq_send_data_id, data);
			}
		} else {
			Toast.makeText(mContext, "没有可用的网络连接，请确认后重试！", Toast.LENGTH_SHORT).show();
		}
	}
	
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch (v.getId()) {
			case R.id.btn_login:
				clickLoginEvent();
				break;
			case R.id.btn_login_regist:
				startActivity(new Intent(this, RegisterActivity.class));
				break;
			case R.id.btn_login_find:
				startActivity(new Intent(this, FindPwdActivity.class));
				break;
			case R.id.btn_login_username_del:
				et_name.setText("");
				break;
			case R.id.btn_login_password_del:
				et_pwd.setText("");
				break;
			case R.id.iv_login_demo:
				TunnelCommunication.getInstance().searchLocalDevice();
				mDialog = Utils.createLoadingDialog(mContext, "正在搜索设备...");
				mDialog.show();
				sendHandlerMsg(SEARCH_TIMEOUT, Value.REQ_TIME_10S);
//				final OkOnlyDialog myDialog=new OkOnlyDialog(mContext);
//				myDialog.setTitle("温馨提示");
//				myDialog.setMessage("业务体验正在完善，敬请期待...");
//				myDialog.setPositiveButton("确认", new OnClickListener() {
//					@Override
//					public void onClick(View v) {
//						myDialog.dismiss();
//					}
//				});
				break;
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		if (mDialog != null)
			mDialog.dismiss();
		if (handler.hasMessages(LOGIN_TIMEOUT)) {
			handler.removeMessages(LOGIN_TIMEOUT);
		}
		if (handler.hasMessages(R.id.login_id)) {
			handler.removeMessages(R.id.login_id);
		}
		return super.onKeyDown(keyCode, event);
	}
	
	/**
	 * @return true:注册信息格式正确  false:注册信息格式错误
	 */
	private boolean checkRegisterData() {
		boolean resultFlag = false;
		
		//获取EditText输入框的字符串
		userName = et_name.getText().toString().trim();
		userPwd = et_pwd.getText().toString().trim();
		
		if (userName.equals("")) {
			resultFlag = false;
			Toast.makeText(mContext, "请输入用户名！", Toast.LENGTH_SHORT).show();
		}
		else if (Utils.isChineseString(userName)) {
			resultFlag = false;
			Toast.makeText(mContext, "不支持中文！", Toast.LENGTH_SHORT).show();
		}
		else if ((userName.length()<3) || (userName.length()>20)) {
			resultFlag = false;
			Toast.makeText(mContext, "用户名长度范围3~20！", Toast.LENGTH_SHORT).show();
		} else {
			resultFlag = true;
			if (userPwd.equals("")) {
				resultFlag = false;
				Toast.makeText(mContext, "请输入密码！", Toast.LENGTH_SHORT).show();
			}
			else if (Utils.isChineseString(userPwd)) {
				resultFlag = false;
				Toast.makeText(mContext, "不支持中文！", Toast.LENGTH_SHORT).show();
			}
			else if ((userPwd.length()<6) || (userPwd.length()>20)) {
				resultFlag = false;
				Toast.makeText(mContext, "密码长度范围6~20！", Toast.LENGTH_SHORT).show();
			} else {
				resultFlag = true;
			}
		}
		return resultFlag;
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		unregisterReceiver(localDeviceReceiver);
		Value.isManulLogout = false;
	}
	
	/**
	 * 显示本地设备对话框
	 */
	private void showLocalDeviceList() {
		localDeviceDialog = new WiFiAlertDialog(mContext);
		localDeviceDialog.setTitle("本地设备列表");
		localDeviceAdapter = new LocalDeviceAdapter(mContext, localDeviceList);
		localDeviceDialog.setAdapter(localDeviceAdapter);
		localDeviceDialog.setOnItemClickListenerLocalDevice(localDeviceList);
	}
	
	/**
	 * 搜索本地设备的适配器
	 */
	private class LocalDeviceAdapter extends BaseAdapter {
		
		private Context context;
		private ArrayList<HashMap<String, String>> list = null;

		public LocalDeviceAdapter(Context context, ArrayList<HashMap<String, String>> list) {
			this.context = context;
			this.list = list;
		}

		@Override
		public int getCount() {
			return list.size();
		}

		@Override
		public Object getItem(int position) {
			return list.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup viewGroup) {
			ViewHolder holder;

			if (convertView == null) {
				convertView = LayoutInflater.from(context).inflate(R.layout.local_device_item, null);
				holder = new ViewHolder();
				convertView.setTag(holder);
				holder.tv_local_device_name = (TextView) convertView.findViewById(R.id.tv_local_device_name);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}
			if (holder.tv_local_device_name != null) {
				holder.tv_local_device_name.setText((CharSequence) list.get(position).get("MAC"));
			}
			return convertView;
		}

		class ViewHolder {
			TextView tv_local_device_name;
		}
	}


	/**
	 * @author sunfusheng
	 * 本地设备的广播接收
	 */
	public class LocalDeviceReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			boolean isRepeat = false;
			if ((mDialog != null) && (mDialog.isShowing())) {
				mDialog.dismiss();
			}
			if (handler.hasMessages(SEARCH_TIMEOUT)) {
				handler.removeMessages(SEARCH_TIMEOUT);
			}
			if (localDeviceList == null) {
				localDeviceList = new ArrayList<HashMap<String, String>>();
			}
			String action = intent.getAction();
			if ((action.equals(Value.SEARCH_LOCAL_DEVICE_ACTION)) && (intent != null)) {
				
				String mac = (String) intent.getCharSequenceExtra("MAC");
				String ip = (String) intent.getCharSequenceExtra("IP");
				int port = intent.getIntExtra("Port", 22616);
				String gateway = (String) intent.getCharSequenceExtra("Gateway");
				String mask = (String) intent.getCharSequenceExtra("Mask");
				
				for (int i=0; i<localDeviceList.size(); i++) {
					if (mac.equals(localDeviceList.get(i).get("MAC"))) {
						isRepeat = true;
					}
				}
				if (!isRepeat) {
					HashMap<String, String> item = new HashMap<String, String>();
					item.put("MAC", mac);
					item.put("IP", ip);
					item.put("Port", ""+port);
					item.put("Gateway", gateway);
					item.put("Mask", mask);
					localDeviceList.add(item);
				} else {
					isRepeat = false;
				}
				if (localDeviceDialog == null) {
					showLocalDeviceList();
				} else {
					if (localDeviceDialog.isShowing()) {
						localDeviceAdapter.notifyDataSetChanged();
					} else {
						showLocalDeviceList();
					}
				}
			}
		}
	}
}
