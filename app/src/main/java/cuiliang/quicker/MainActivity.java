package cuiliang.quicker;

import android.app.Activity;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Message;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.support.v7.widget.GridLayout;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import cuiliang.quicker.client.ClientService;
import cuiliang.quicker.client.ConnectionStatus;
import cuiliang.quicker.client.MessageCache;
import cuiliang.quicker.events.ConnectionStatusChangedEvent;
import cuiliang.quicker.events.ServerMessageEvent;
import cuiliang.quicker.messages.MessageBase;
import cuiliang.quicker.messages.recv.UpdateButtonsMessage;
import cuiliang.quicker.messages.recv.VolumeStateMessage;
import cuiliang.quicker.messages.send.CommandMessage;
import cuiliang.quicker.messages.send.TextDataMessage;
import cuiliang.quicker.util.ImagePicker;

import com.google.android.gms.common.api.CommonStatusCodes;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

import android.app.ProgressDialog;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private static final int REQ_CODE_SCAN_BRCODE = 10;
    private static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;
    static final int REQUEST_TAKE_PHOTO = 1;


    SparseArray _buttons = new SparseArray<UiButtonItem>();


    // ClientManager clientManager;

    private SeekBar seekbarVolume;

    private VolumeStateMessage oldVolumeState;

    private ImageButton btnMute;

    private TextView txtProfileName;

    private Intent clientServiceIntent;
    private ServiceConnection conn;


    private ClientService clientService;


    // 最后处理的消息，防止重复处理
    private MessageCache _lastProcessedMessages = new MessageCache();

    PowerManager.WakeLock wakeLock;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.d(TAG, "onCreate");

        super.onCreate(savedInstanceState);

        //
        // 数据初始化
        //
        clientServiceIntent = new Intent(this, ClientService.class);

        //
        // 界面相关操作
        //
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // 禁止屏幕关闭
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 隐藏底部的导航按钮
        hideBottomUIMenu();

        // 依据屏幕方向加载
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_main);
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(R.layout.activity_main_portrait);
        }

        // 组件引用
        txtProfileName = (TextView) findViewById(R.id.txtProfileName);
        seekbarVolume = (SeekBar) findViewById(R.id.seekbarVolume);
        btnMute = (ImageButton) findViewById(R.id.btnMute);


        // 创建action按钮
        createActionButtons(orientation);


        setupUiListeners();

        // region 建立与ClientService的链接
        conn = new ServiceConnection() {
            /**
             * 与服务器端交互的接口方法 绑定服务的时候被回调，在这个方法获取绑定Service传递过来的IBinder对象，
             * 通过这个IBinder对象，实现宿主和Service的交互。
             */
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "绑定成功调用：onServiceConnected");
                ClientService.LocalBinder binder = (ClientService.LocalBinder) service;
                clientService = binder.getService();

                if (clientService.getClientManager().isConnected() == false){
                    Log.d(TAG, "网络未连接，进入配置界面。。。");
                    goConfigActivity(true);
                }

                processPcMessage(clientService.getMessageCache().lastVolumeStateMessage);
                processPcMessage(clientService.getMessageCache().lastUpdateButtonsMessage);
            }

            /**
             * 当取消绑定的时候被回调。但正常情况下是不被调用的，它的调用时机是当Service服务被意外销毁时，
             * 例如内存的资源不足时这个方法才被自动调用。
             */
            @Override
            public void onServiceDisconnected(ComponentName name) {
                clientService = null;
            }
        };


        //
        // 启动客户端线程
        startService(clientServiceIntent);

        //endregion


        // 进入configActivity，从而连接网络
        //goConfigActivity();


    }

    private void recreateView(){
        // 依据屏幕方向加载
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_main);
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(R.layout.activity_main_portrait);
        }

        // 组件引用
        txtProfileName = (TextView) findViewById(R.id.txtProfileName);
        seekbarVolume = (SeekBar) findViewById(R.id.seekbarVolume);
        btnMute = (ImageButton) findViewById(R.id.btnMute);

        // 创建action按钮
        createActionButtons(orientation);

        setupUiListeners();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        recreateView();

        clientService.getClientManager().requestReSendState();

//        processPcMessage(clientService.getMessageCache().lastVolumeStateMessage);
//        processPcMessage(clientService.getMessageCache().lastUpdateButtonsMessage);



//        int orientation = getResources().getConfiguration().orientation;
//        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//            setContentView(R.layout.activity_main);
//        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
//            setContentView(R.layout.activity_main_portrait);
//        }

//        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
//            //当前为横屏， 在此处添加额外的处理代码
//
//        } else if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
//
//            //当前为竖屏， 在此处添加额外的处理代码
//
//        }
    }

    /**
     * 设置界面按钮的事件处理
     */
    private void setupUiListeners() {
        ImageButton btnConfig = (ImageButton) findViewById(R.id.btnConfig);
        btnConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goConfigActivity(false);
            }
        });


        btnMute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clientService.getClientManager().sendToggleMuteMsg();
            }
        });

        seekbarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                clientService.getClientManager().sendUpdateVolumeMsg(seekBar.getProgress());
            }
        });


//        ImageButton btnScanQrcode = (ImageButton) findViewById(R.id.btnPc);
//        btnScanQrcode.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent intent = new Intent(MainActivity.this, ScanBarcodeActivity.class);
//                startActivityForResult(intent, REQ_CODE_SCAN_BRCODE);// 表示可以返回结果
//            }
//        });

        ImageButton btnPc = (ImageButton) findViewById(R.id.btnPc);
        if (btnPc != null){
            btnPc
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            clientService.getClientManager().sendCommandMsg(CommandMessage.OPEN_MAINWIN,"");
                        }
                    });
        }

        findViewById(R.id.btnPhoto).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beginTakePhoto();
            }
        });

        ImageButton btnVoice = findViewById(R.id.btnVoice);



        btnVoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startVoiceInput();


            }
        });
    }

    /**
     * 开启语音输入
     */
    private void startVoiceInput() {
        //开启语音识别功能
        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        //设置模式，目前设置的是自由识别模式
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        //提示语言开始文字，就是效果图上面的文字
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Please start your voice");
        //开始识别，这里检测手机是否支持语音识别并且捕获异常
        try {
            startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);

        } catch (ActivityNotFoundException a) {
            Toast t = Toast.makeText(getApplicationContext(),
                    "抱歉，您的设备当前不支持此功能。请安装Google语音搜索。",
                    Toast.LENGTH_SHORT);
            t.show();
        }
    }


    //region 依据服务器消息更新界面


    /**
     * 更新按钮状态
     *
     * @param item
     */
    private void updateButton(UpdateButtonsMessage.ButtonItem item) {
        UiButtonItem button = getButtonByIndex(item.Index);

        button.button.setClickable(item.IsEnabled);


        if (!item.IsEnabled) {

            button.button.setBackgroundColor(Color.rgb(220, 220, 220));

//            button.textView.setText(item.Label);
//            button.imageView.setImageBitmap(null);
        } else {
            button.button.setBackgroundColor(Color.WHITE);
        }

        //无论是否禁用，都加载文字和图片
        if (item.Label != null && !item.Label.isEmpty()) {
            button.textView.setText(item.Label);
            button.textView.setVisibility(View.VISIBLE);
        } else {
            button.textView.setText("");
            button.textView.setVisibility(View.GONE);
        }


        if (item.IconFileContent != null && !item.IconFileContent.isEmpty()) {
            byte[] imgContent = Base64.decode(item.IconFileContent, Base64.DEFAULT);
            Log.d(TAG, "图标文件长度：" + imgContent.length);
//                Drawable image = new BitmapDrawable(getResources(), BitmapFactory.decodeByteArray(imgContent, 0, imgContent.length));
//                button.setCompoundDrawables(image, null, null, null);


            Glide.with(this)
                    .asBitmap()
                    .load(imgContent)
                    .into(button.imageView);

            button.imageView.setVisibility(View.VISIBLE);

        } else if (item.IconFileName != null && !item.IconFileName.isEmpty()) {

            Glide.with(this)
                    .load(item.IconFileName)
                    .into(button.imageView);

            button.imageView.setVisibility(View.VISIBLE);
        }
        else {
            button.imageView.setImageBitmap(null);
            button.imageView.setVisibility(View.GONE);
        }
    }

    // 更新声音状态显示
    private void UpdateVolumeState(VolumeStateMessage message) {

        if (message.Mute) {
            btnMute.setImageResource(R.drawable.ic_volume_off_black_24dp);

            seekbarVolume.setProgress(message.MasterVolume);
            seekbarVolume.setVisibility(View.INVISIBLE);
        } else {
            if (message.MasterVolume > 50) {
                btnMute.setImageResource(R.drawable.ic_volume_up_black_24dp);
            } else if (message.MasterVolume > 5) {
                btnMute.setImageResource(R.drawable.ic_volume_down_black_24dp);
            } else {
                btnMute.setImageResource(R.drawable.ic_volume_mute_black_24dp);
            }

            seekbarVolume.setProgress(message.MasterVolume);
            seekbarVolume.setVisibility(View.VISIBLE);
        }


        oldVolumeState = message;


    }

    //endregion

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");

        //
        // 启动客户端线程
        stopService(clientServiceIntent);

        super.onDestroy();
    }


    /**
     * 根据序号获取按钮对象
     *
     * @param index
     * @return
     */
    private UiButtonItem getButtonByIndex(int index) {

        //TODO: 按钮不存在的情况

        return (UiButtonItem) _buttons.get(index);
    }

    private Integer getButtonIndex(boolean isGlobal, int row, int col){
        return  (isGlobal ? 0 : 1000000)
                + row * 1000
                + col;
    }

    // region 生成按钮

    /**
     * 生成界面按钮
     *
     * @param orientation 当前窗口是水平还是竖直的
     */
    private void createActionButtons(int orientation) {

        GridLayout gridGlobal = (GridLayout) findViewById(R.id.gridLayoutGlobal);
        GridLayout gridProfile = (GridLayout) findViewById(R.id.gridLayoutProfile);
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//            for (int i = 0; i < 12; i++) {
//                createButton(gridGlobal, i / 3, i % 3, i);
//            }
//
//            for (int i = 0; i < 16; i++) {
//                createButton(gridProfile, i / 4, i % 4, i + 100);
//            }

            for(int row =0; row < 4; row ++){
                for(int col=0; col<3; col ++){
                    createButton(gridGlobal, row, col, getButtonIndex(true, col, row));
                }
            }

            for(int row =0; row <4; row ++){
                for(int col=0; col<4; col ++){
                    createButton(gridProfile, row, col, getButtonIndex(false, row, col));
                }
            }


        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {

            for(int row =0; row < 3; row ++){
                for(int col=0; col<4; col ++){
                    createButton(gridGlobal, row, col, getButtonIndex(true, row, col));
                }
            }

            for(int row =0; row <4; row ++){
                for(int col=0; col<4; col ++){
                    createButton(gridProfile, row, col, getButtonIndex(false, row, col));
                }
            }

//            for (int i = 0; i < 12; i++) {
//                createButton(gridGlobal, i / 4, i % 4, i);
//            }
//
//            for (int i = 0; i < 16; i++) {
//                createButton(gridProfile, i / 4, i % 4, i + 100);
//            }
        }
    }


    /**
     * 创建一个action按钮
     *
     * @param grid
     * @param rowIndex
     * @param colIndex
     * @param btnIndex
     */
    private void createButton(GridLayout grid, int rowIndex, int colIndex, final int btnIndex) {
        final LinearLayout btn = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);


        btn.setLayoutParams(params);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setClickable(false);
        btn.setBackgroundColor(Color.rgb(220, 220, 220));
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(2, 2, 2, 2);

        btn.setTag(btnIndex);


        // region button event
        btn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (!btn.isClickable()) {
                    return false;
                }



                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        btn.setBackgroundColor(Color.rgb(200, 100, 50));
                        vibrate();
                        break;
                    case MotionEvent.ACTION_UP:

                        //set color back to default
                        btn.setBackgroundColor(Color.WHITE);

                        int btnIndex = (int) v.getTag();
                        Log.d(TAG, "按钮触摸！" + btnIndex);

                        clientService.getClientManager().sendButtonClickMsg(btnIndex);
                        break;
                }


                //vibrator.vibrate(new long[]{0,1000}, -1);

                /**
                 * 创建一次性振动
                 *
                 * @param milliseconds 震动时长（ms）
                 * @param amplitude 振动强度。这必须是1到255之间的值，或者DEFAULT_AMPLITUDE
                 */
                //vibrator.vibrate(VibrationEffect.createOneShot(20, 50));
                //vibrator.vibrate(20);


                return true;
            }


        });
        //endregion


        ImageView iv = new ImageView(this);
        iv.setClickable(false);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(pxFromDp(40), pxFromDp(40));

        iv.setLayoutParams(layoutParams);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(0, 0, 0, 4);


        TextView tv = new TextView(this);
        tv.setClickable(false);
        tv.setPadding(0, 4, 0, 0);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setSingleLine(true);


        tv.setGravity(Gravity.CENTER);
        //tv.setText("btn" + btnIndex);
        btn.addView(iv);
        btn.addView(tv);


        GridLayout.Spec row = GridLayout.spec(rowIndex, 1f);
        GridLayout.Spec col = GridLayout.spec(colIndex, 1f);
        GridLayout.LayoutParams gridLayoutParam = new GridLayout.LayoutParams(row, col);
        gridLayoutParam.setMargins(1, 1, 1, 1);
        gridLayoutParam.height = 0;
        gridLayoutParam.width = 0;


        grid.addView(btn, gridLayoutParam);

        UiButtonItem item = new UiButtonItem();
        item.button = btn;
        item.imageView = iv;
        item.textView = tv;
        _buttons.put(btnIndex, item);
    }

    // endregion


    // region Utility辅助代码

    private void vibrate(){
        Vibrator vibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);

        int version = Build.VERSION.SDK_INT;

        if (version >= 26){
            vibrator.vibrate(VibrationEffect.createOneShot(25, 50));
        }else{
            vibrator.vibrate(25);
        }
    }

    /**
     * 显示消息提示
     *
     * @param message
     */
    private void showToast(String message) {
        Toast t = Toast.makeText(getApplicationContext(),
                message,
                Toast.LENGTH_SHORT);
        t.show();
    }

    /**
     * 根据给定的dp单位数值，计算pix数值
     *
     * @param dp dp数值
     * @return pix数值
     */
    private int pxFromDp(int dp) {
        final float scale = getResources().getDisplayMetrics().density;
        return (int) (dp * scale);
    }

    /**
     * 隐藏虚拟按键，并且全屏
     */
    protected void hideBottomUIMenu() {
        //隐藏虚拟按键，并且全屏
        int flags;
        int curApiVersion = android.os.Build.VERSION.SDK_INT;
        // This work only for android 4.4+
        if(curApiVersion >= Build.VERSION_CODES.KITKAT){
            // This work only for android 4.4+
            // hide navigation bar permanently in android activity
            // touch the screen, the navigation bar will not show
            flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;

        }else{
            // touch the screen, the navigation bar will show
            flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }

        // must be executed in main thread :)
        getWindow().getDecorView().setSystemUiVisibility(flags);

//        if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) { // lower api
//            View v = this.getWindow().getDecorView();
//            v.setSystemUiVisibility(View.GONE);
//        } else if (Build.VERSION.SDK_INT >= 19) {
//            Window window = getWindow();
//            WindowManager.LayoutParams params = window.getAttributes();
//            params.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
//            window.setAttributes(params);
//
////            //for new api versions.
////            View decorView = getWindow().getDecorView();
////            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
////                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
////            decorView.setSystemUiVisibility(uiOptions);
//        }
    }

    //endregion


    /**
     * 进入配置页面，autoReturn指示了联网成功后，是否自动返回主activity
     * @param autoReturn
     */
    private void goConfigActivity(boolean autoReturn) {
        Intent intent = new Intent(this, ConfigActivity.class);
        intent.putExtra("autoReturn", autoReturn);
        startActivity(intent);
    }


    // 再重写一个onActivityResult方法，作用是将当前Activity中的数据传递到另一个Activity的意图中后，实现跳转，再回传回来。
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);


        if (requestCode == REQ_CODE_SCAN_BRCODE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                String qrcode = data.getStringExtra("barcode");
                Log.d(TAG, "扫描结果：" + qrcode);

                clientService.getClientManager().sendTextMsg(TextDataMessage.TYPE_QRCODE, qrcode);


            }
        } else if (requestCode == VOICE_RECOGNITION_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                //返回结果是一个list，我们一般取的是第一个最匹配的结果
                ArrayList<String> text = data
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);


                clientService.getClientManager().sendTextMsg(TextDataMessage.TYPE_VOICE_RECOGNITION, text.get(0));
            }
        } else if (requestCode == REQUEST_TAKE_PHOTO) {
            if (resultCode == RESULT_OK) {
               // readPic();

                Bitmap bitmap = ImagePicker.getImageFromResult(this, resultCode, data);
                sendImage(bitmap);
            } else {
//                    Bitmap bitmap=MediaStore.Images.Media.getBitmap(getContentResolver(),imageUri);
//                    iv_image.setImageBitmap(bitmap);
            }
        }


    }


    //region 拍照处理
    /// 开始拍照
    private void beginTakePhoto() {

        if (mDialog != null){
            Toast.makeText(getApplicationContext(),
                    "照片正在传输中，暂时无法拍照。",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent chooseImageIntent = ImagePicker.getPickImageIntent(this);
        startActivityForResult(chooseImageIntent, REQUEST_TAKE_PHOTO);
    }

    //
    ProgressDialog mDialog;

    private void sendImage(final Bitmap bitmap){
        Log.d(TAG, "开始发送图片");

        mDialog = ProgressDialog.show(MainActivity.this, "Quicker", "正在发送图片，请稍候……");

//        mDialog = new ProgressDialog(MainActivity.this);
//        mDialog.setMessage("正在发送图片...");
//        mDialog.setCancelable(false);
//        mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
//        mDialog.show();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
//            spandTimeMethod();// 耗时的方
//                 handler.sendEmptyMessage(0);// 执行耗时的方法之后发送消给handler

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream);
                byte[] byteArray = stream.toByteArray();

                clientService.getClientManager().sendPhotoMsg("image.png"
                        , byteArray);



                handler.sendEmptyMessage(0);
            }
        });

        thread.start();

//        try {
//            thread.join();
//        } catch (InterruptedException e) {
//            // ...
//        }




    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {// handler接收到消息后就会执行此方法
            mDialog.dismiss();// 关闭ProgressDialog
            mDialog = null;
            showToast("已发送图片");
        }
    };




    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    //endregion


    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        // The activity is about to become visible.

        EventBus.getDefault().register(this);

        // 绑定到后台服务
        bindService(clientServiceIntent, conn, Service.BIND_AUTO_CREATE);

        //
        //if (clientService.getClientManager().isConnected() == false) {
        // 未连接，进入配置界面
        //showToast("尚未连接电脑");
        //goConfigActivity();
        //}

        setupScreenLight();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        if (clientService != null && clientService.getClientManager() != null){
            clientService.getClientManager().requestReSendState();
        }

        // The activity has become visible (it is now "resumed").
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        // Another activity is taking focus (this activity is about to be "paused").
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        // The activity is no longer visible (it is now "stopped")

        if (wakeLock != null){
            wakeLock.release();
            wakeLock = null;
        }

        EventBus.getDefault().unregister(this);

        if (clientService != null) {
            //clientService = null;  //保留引用的值，避免空指针问题
            unbindService(conn);
        }
    }


    //region 服务器消息的处理

    /**
     * 处理收到的pc消息
     *
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ServerMessageEvent event) {
        MessageBase originMessage = event.serverMessage;
        processPcMessage(originMessage);
    }

    /**
     * 处理pc消息
     *
     * @param originMessage
     */
    private void processPcMessage(MessageBase originMessage) {
        if (originMessage == null)
            return;

        if (originMessage instanceof UpdateButtonsMessage) {
            UpdateButtonsMessage serverMsg = (UpdateButtonsMessage) originMessage;

            //if (serverMsg != _lastProcessedMessages.lastUpdateButtonsMessage) {

                _lastProcessedMessages.lastUpdateButtonsMessage = serverMsg;

                Log.d(TAG, "更新" + serverMsg.Buttons.length + "个按钮！");

                txtProfileName.setText(serverMsg.ProfileName);
                for (UpdateButtonsMessage.ButtonItem btn : serverMsg.Buttons) {

                    //Button button = getButtonByIndex(btn.Index);
//                            button.setText(btn.Label);

                    updateButton(btn);

                }
//            } else {
//                Log.d(TAG, "已经处理过这个消息了。");
//            }


        } else if (originMessage instanceof VolumeStateMessage) {
            VolumeStateMessage volumeStateMessage = (VolumeStateMessage) originMessage;

            //if (volumeStateMessage != _lastProcessedMessages.lastVolumeStateMessage) {
                _lastProcessedMessages.lastVolumeStateMessage = volumeStateMessage;
                UpdateVolumeState(volumeStateMessage);
//            }else {
//                Log.d(TAG, "已经处理过这个消息了。");
//            }
        } else if (originMessage instanceof  CommandMessage){

            CommandMessage cmdMsg = (CommandMessage)originMessage;
            Log.d(TAG, "收到启动语音输入消息。" + cmdMsg.Command);
            if (cmdMsg.Command.equals(CommandMessage.START_VOICE_INPUT)){
                Log.d(TAG, "启动语音输入。");
                startVoiceInput();
            }
        }
    }


    /**
     * 网络连接状态改变了。
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(ConnectionStatusChangedEvent event) {
        // 如果连接断开，进入配置页面
        if (event.status == ConnectionStatus.Disconnected
                || event.status == ConnectionStatus.LoginFailed) {
            goConfigActivity(true);
        }
    }

    //endregion


    /**
     * 设置背光亮一段时间
     */
    void setupScreenLight(){
        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "MyWakeLock");
        wakeLock.acquire(60 * 60 * 1000);
    }
}


