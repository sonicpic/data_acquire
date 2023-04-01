package com.example.dataacquire.ui.data;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.dataacquire.databinding.FragmentDataBinding;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public class DataFragment extends Fragment {

    private FragmentDataBinding binding;

    private final String TAG = "sensor-sample";

    private CardView card_state;
    private TextView tv_state;
    private TextView tv_ip_address;
    private TextView tv_accelerometer_sensor;
    private TextView tv_magnetic_sensor;
    private TextView tv_rotation_sensor;
    private TextView tv_R;
    private TextView tv_location;

    private Button btn_start;
    private Button btn_end;
    private RadioGroup rg_tag;

    private SensorManager mSensorManager;
    private LocationManager locationManager;
    private MySensorEventListener mMySensorEventListener;
    private MyLocationListerner myLocationListerner;

    private String provider;                    //位置控制器种类

    float[] accValues = new float[3];           //加速度数据
    float[] magneticValues = new float[3];      //地磁数据
    float gri = 0;                              //z轴加速度数据
    Location locationData = null;               //GPS数据

    protected Handler mHandler;

    String ipAddress;
    int port;

    //stream
    FileWriter fstream;
    BufferedWriter out;

    private SharedPreferences sharedPreferences;
    int count;

    Thread writeThread;
    boolean isRun = false;
    View root;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        DataViewModel dataViewModel =
                new ViewModelProvider(this).get(DataViewModel.class);

        binding = FragmentDataBinding.inflate(inflater, container, false);
        root = binding.getRoot();

        Log.d("Pan", "OnCreate");

        card_state = binding.cardState;
        tv_state = binding.tvState;
        tv_ip_address = binding.tvIpAddress;
        tv_accelerometer_sensor = binding.tvAccelerometerSensor; //加速度
        tv_location = binding.locationTv;                        //GPS
        tv_magnetic_sensor = binding.tvMagneticSensor;           //磁场
        tv_rotation_sensor = binding.tvRotationSensor;           //旋转矢量
        tv_R = binding.tvR;                                      //加速度-修正
        btn_start = binding.btnStart;
        btn_end = binding.btnEnd;
        rg_tag = binding.rgTag;                                  //选项组

        /**
         * 网络配置
         */
        ipAddress = "239.0.0.1";
        port = 9999;
        tv_ip_address.setText(ipAddress + ":" +String.valueOf(port));

        /**
         * 网络线程
         */
        // 步骤3：创建线程辅助对象，即 实例化 线程辅助类
        ReceiveBroadcast receiveBroadcast = new ReceiveBroadcast();

        // 步骤4：创建线程对象，即 实例化线程类；线程类 = Thread类；
        // 创建时通过Thread类的构造函数传入线程辅助类对象
        // 原因：Runnable接口并没有任何对线程的支持，我们必须创建线程类（Thread类）的实例，从Thread类的一个实例内部运行
        Thread td1 = new Thread(receiveBroadcast);

        // 步骤5：通过 线程对象 控制线程的状态，如 运行、睡眠、挂起  / 停止
        // 当调用start（）方法时，线程对象会自动回调线程辅助类对象的run（），从而实现线程操作
        td1.start();

        /**
         * 写数据线程
         */
        WriteCSV writeCSV = new WriteCSV();
        writeThread = new Thread(writeCSV);
        writeThread.start();

        /**
         * 申请权限
         * 操作将用户引导至一个系统设置页面，在该页面上，用户可以为您的应用启用以下选项：授予所有文件的管理权限
         */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
        } else {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
        }

        /**
         * 初始化服务和监听接口
         */
        this.mSensorManager = (SensorManager) getActivity().getSystemService(getContext().SENSOR_SERVICE);      //获取传感器服务
        this.locationManager = (LocationManager) getActivity().getSystemService(getContext().LOCATION_SERVICE); //获取系统LocationManager服务
        this.mMySensorEventListener = new MySensorEventListener();                                              //自定义的传感器监听接口，重写回调函数
        this.myLocationListerner = new MyLocationListerner();                                                   //自定义的位置监听接口，重写回调函数


        /**
         * 获取当前可用的位置控制器
         */
        List<String> list = locationManager.getProviders(true);
        if (list.contains(LocationManager.GPS_PROVIDER)) {
            //是否为GPS位置控制器
            provider = LocationManager.GPS_PROVIDER;
            Toast.makeText(getActivity(), "GPS定位，精度高",
                    Toast.LENGTH_SHORT).show();
        } else if (list.contains(LocationManager.NETWORK_PROVIDER)) {
            //是否为网络位置控制器
            Toast.makeText(getActivity(), "NET定位，精度较低",
                    Toast.LENGTH_SHORT).show();
            provider = LocationManager.NETWORK_PROVIDER;
        } else {
            Toast.makeText(getActivity(), "请检查网络或GPS是否打开",
                    Toast.LENGTH_LONG).show();
            return root;
        }

        /**
         * 获取最近一次的位置信息
         */
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return root;
        }
        Location location = locationManager.getLastKnownLocation(provider);     //获取上次的位置
        updateGPS(location);                                                    //刷新一次文本显示

        /**
         * 请求位置信息更新，确保应用开始定期位置信息更新
         * 参1:选择定位的方式
         * 参2:定位的间隔时间
         * 参3:当位置改变多少时进行重新定位
         * 参4:位置的回调监听
         */
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                500, 0, myLocationListerner);

        /**
         * 主线程的Handler
         * 处理子线程的更新UI需求
         */
        mHandler = new MyHandler1(getContext());

        /**
         * 手动开始按钮
         */
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeFile("HAND");
                card_state.setCardBackgroundColor(Color.BLUE);
                tv_state.setText("HAND");
                tv_state.setTextColor(Color.WHITE);
            }
        });

        /**
         * 手动停止按钮
         */
        btn_end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isRun = false;
                card_state.setCardBackgroundColor(Color.WHITE);
                tv_state.setText("Stop");
                tv_state.setTextColor(Color.BLACK);
                /*关闭文件流*/
                try {
                    out.close();
                    fstream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        /*注册加速度传感器监听*/
        Sensor accelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometerSensor != null) {
            mSensorManager.registerListener(mMySensorEventListener, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            Log.d(TAG, "Accelerometer sensors are not supported on current devices.");
        }

        /*注册磁场传感器监听*/
        Sensor magSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magSensor != null) {
            mSensorManager.registerListener(mMySensorEventListener, magSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            Log.d(TAG, "magSensor sensors are not supported on current devices.");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSensorManager == null) {
            return;
        }
        mSensorManager.unregisterListener(mMySensorEventListener);  //取消所有注册
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        /*解除GPS监听器*/
        if (locationManager != null) {
            locationManager.removeUpdates(myLocationListerner);
        }
    }

    /**
     * 传感器监听接口，当传感器数据变化时触发回调函数
     * 当传感器数据发生改变时，更新全局变量并刷新文本显示
     */
    private class MySensorEventListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            /*加速度传感器数据*/
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                accValues = event.values.clone();    //有没有可能有读写安全问题
                tv_accelerometer_sensor.setText("x:" + event.values[0] + "\ny:" + event.values[1] + "\nz:" + event.values[2]);
            }
            /*磁场传感器数据*/
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                magneticValues = event.values.clone();
                tv_magnetic_sensor.setText("x:" + event.values[0] + "\ny:" + event.values[1] + "\nz:" + event.values[2]);
            }
            /*计算加速度-修正*/
            float[] R = new float[9];
            mSensorManager.getRotationMatrix(R, null, accValues, magneticValues);
            gri = accValues[0] * R[6] + accValues[1] * R[7] + accValues[2] * R[8];
            tv_R.setText(String.valueOf(gri));
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "onAccuracyChanged:" + sensor.getType() + "->" + accuracy);
        }
    }

    /**
     * GPS监听接口，当GPS信息改变时触发回调函数
     * 当GPS定位信息发生改变时，更新全局变量并调用刷新文本显示方法
     */
    private class MyLocationListerner implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            locationData = location;
            updateGPS(location);
        }

        @Override
        public void onProviderDisabled(String provider) {
            updateGPS(null);
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onProviderEnabled(String provider) {
            // 当GPS LocationProvider可用时，更新位置
            updateGPS(locationManager
                    .getLastKnownLocation(provider));
        }

        @Override
        public void onStatusChanged(String provider, int status,
                                    Bundle extras) {
        }
    }

    /**
     * 刷新位置文本显示
     *
     * @param location 位置信息
     */
    private void updateGPS(Location location) {
        if (location != null) {
            StringBuffer sb = new StringBuffer();
            sb.append("经度：");
            sb.append(location.getLongitude());
            sb.append("\n纬度：");
            sb.append(location.getLatitude());
            sb.append("\n高度：");
            sb.append(location.getAltitude());
            sb.append("\n速度：");
            sb.append(location.getSpeed());
            sb.append("\n方向：");
            sb.append(location.getBearing());
            sb.append("\n精度：");
            sb.append(location.getAccuracy());
            tv_location.setText(sb.toString());
        } else {
            // 如果传入的Location对象为空则清空EditText
            tv_location.setText("");
        }
    }


    /**
     * 网络线程子类，子线程
     */
    class ReceiveBroadcast implements Runnable {
        // 步骤2：复写run（），内容 = 定义线程行为
        @Override
        public void run() {
            try {
                MulticastSocket socket = new MulticastSocket(port);
                Log.d("Pan", socket.toString());
                InetAddress group = InetAddress.getByName(ipAddress); // 设置广播组地址
                socket.joinGroup(group); // 加入广播组
                byte[] data = new byte[1024];
                DatagramPacket packet = new DatagramPacket(data, data.length);
                while (true) {
                    socket.receive(packet); // 接收广播消息
                    String res = new String(packet.getData(), 0, packet.getLength());
                    Log.d("Pan", "Received broadcast message: " + res);
                    Message message = new Message();
                    message.what = 1;
                    message.obj = res;
                    mHandler.sendMessage(message);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 对广播信息处理，在主线程中
     */
    class MyHandler1 extends Handler {

        private WeakReference<Context> weakReference;

        MyHandler1(Context context) {
            weakReference = new WeakReference<>(context);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case 1:
                    //完成主界面更新,拿到数据
                    String data = (String) msg.obj;
                    if(data.equals("e") ||data.equals("E")){
                        isRun = false;
                        card_state.setCardBackgroundColor(Color.WHITE);
                        tv_state.setText("Stop");
                        tv_state.setTextColor(Color.BLACK);
                        /*关闭文件流*/
                        try {
                            out.close();
                            fstream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }else{
//                        Toast.makeText(weakReference.get(), data, Toast.LENGTH_SHORT).show();
                        writeFile(data);
                        card_state.setCardBackgroundColor(Color.BLUE);
                        tv_state.setText(data);
                        tv_state.setTextColor(Color.WHITE);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 写文件方法
     */
    void writeFile(String order) {
        if(order == null){
            order="NULL";
        }

        /*打开文件流，并打印表头*/
        try {
            fstream = new FileWriter(getActivity().getExternalFilesDir("") + "/" + order + '-' + LocalDateTime.now().toString() + ".csv");
        } catch (IOException e) {
            e.printStackTrace();
        }
        out = new BufferedWriter(fstream);
        try {
            out.write("latitude,longitude,altitude,date,speed,z_gri,tag");  //打印表头
            out.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        isRun = true;
    }

    /**
     * 写csv线程子类，子线程
     */
    class WriteCSV implements Runnable {
        // 步骤2：复写run（），内容 = 定义线程行为
        @Override
        public void run() {
            while(true){
                while(isRun){
                    write(accValues, locationData,gri);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 写方法
     */
    // 写入文件
    private void write(float[] accData, Location locationData,float gri) {
//        String acc_x, acc_y, acc_z;
//        if (accData != null) {
//            acc_x = String.valueOf(accData[0]);
//            acc_y = String.valueOf(accData[1]);
//            acc_z = String.valueOf(accData[2]);
//        } else {
//            acc_x = "NULL";
//            acc_y = "NULL";
//            acc_z = "NULL";
//        }
        String latitude, longitude, altitude, speed;
        if (locationData != null) {
            latitude = String.valueOf(locationData.getLatitude());
            longitude = String.valueOf(locationData.getLongitude());
            altitude = String.valueOf(locationData.getAltitude());
            speed = String.valueOf(locationData.getSpeed());
        } else {
            latitude = "NULL";
            longitude = "NULL";
            altitude = "NULL";
            speed = "NULL";
        }
        Button temp = root.findViewById(rg_tag.getCheckedRadioButtonId());
        String tag;
        if(temp.getText().toString().equals("NULL")){
            tag = "0";
        }
        else if(temp.getText().toString().equals("平整")){
            tag = "1";
        }
        else if(temp.getText().toString().equals("小颠簸")){
            tag = "2";
        }
        else if(temp.getText().toString().equals("凹井盖")){
            tag = "3";
        }
        else if(temp.getText().toString().equals("凸井盖")){
            tag = "4";
        }
        else if(temp.getText().toString().equals("减速带")){
            tag = "5";
        }else{
            tag = "6";
        }

        try {
            out.write(latitude + ","
                    + longitude + ","
                    + altitude + ","
                    + Instant.now().toString() + ","
                    + speed + ","
                    + gri + ","
                    + tag);
            out.newLine();
            out.flush();
            //Log.d("PAN","out");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}