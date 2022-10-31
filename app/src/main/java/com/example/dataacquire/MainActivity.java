package com.example.dataacquire;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "sensor-sample";
    private TextView tv_accelerometer_sensor;
    private TextView tv_magnetic_sensor;
    private TextView tv_rotation_sensor;
    private TextView tv_R;
    private TextView tv_location;

    private Button btn_file;
    private Switch switch_start;

    private SensorManager mSensorManager;
    private MySensorEventListener mMySensorEventListener;
    private MyLocationListerner myLocationListerner;

    private LocationManager locationManager;// 定义LocationManager对象


    float[] accValues = new float[3];//加速度数据
    float[] magneticValues = new float[3];//地磁数据
    float gri = 0; //z轴加速度数据
    Location locationData = null;//GPS数据

    //stream
    FileWriter fstream;
    BufferedWriter out;

    //位置控制器种类
    private String provider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv_accelerometer_sensor = findViewById(R.id.tv_accelerometer_sensor);
        tv_location = findViewById(R.id.location_tv);
        tv_magnetic_sensor = findViewById(R.id.tv_magnetic_sensor);
        tv_rotation_sensor = findViewById(R.id.tv_rotation_sensor);
        tv_R = findViewById(R.id.tv_R);
        btn_file = findViewById(R.id.btn_file);
        switch_start = findViewById(R.id.switch_start);

        btn_file.setOnClickListener(new MyBtnListener());   //设置按钮监听器，打开文件管理

        /*打开文件流，并打印表头*/
        try {
            fstream = new FileWriter(getExternalFilesDir("") + "/" + Instant.now().toString() + ".csv");
        } catch (IOException e) {
            e.printStackTrace();
        }
        out = new BufferedWriter(fstream);
        try {
            out.write("acc_x,acc_y,acc_z,latitude,longitude,altitude,date,speed,z_gri");  //打印表头
            out.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }

        /*申请权限*/
        //  操作将用户引导至一个系统设置页面，在该页面上，用户可以为您的应用启用以下选项：授予所有文件的管理权限。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
        } else {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
        }

        /*初始化*/
        this.mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE); //获取传感器服务
        this.locationManager = (LocationManager) getSystemService(LOCATION_SERVICE); //获取系统LocationManager服务

        this.mMySensorEventListener = new MySensorEventListener();
        this.myLocationListerner = new MyLocationListerner();

        //获取当前可用的位置控制器
        List<String> list = locationManager.getProviders(true);
        if (list.contains(LocationManager.GPS_PROVIDER)) {
            //是否为GPS位置控制器
            provider = LocationManager.GPS_PROVIDER;
            Toast.makeText(this, "GPS",
                    Toast.LENGTH_SHORT).show();
        } else if (list.contains(LocationManager.NETWORK_PROVIDER)) {
            //是否为网络位置控制器
            Toast.makeText(this, "NET",
                    Toast.LENGTH_SHORT).show();
            provider = LocationManager.NETWORK_PROVIDER;
        } else {
            Toast.makeText(this, "请检查网络或GPS是否打开",
                    Toast.LENGTH_LONG).show();
            return;
        }

        //判断权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        // 从GPS获取最近的定位信息
        Location location = locationManager.getLastKnownLocation(provider);
        // 将location里的位置信息显示在EditText中
        updateView(location);
        // 设置每2秒获取一次GPS的定位信息
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                500, 0, myLocationListerner);

        //开启写数据线程
        MyThread1 myThread1 = new MyThread1("start");
        myThread1.start();

    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mSensorManager == null) {
            return;
        }
        /*注册加速度传感器监听*/
        Sensor accelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometerSensor != null) {
            mSensorManager.registerListener(mMySensorEventListener, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            Log.d(TAG, "Accelerometer sensors are not supported on current devices.");
        }

        /*注册磁场传感器监听*/
        Sensor magSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magSensor != null) {
            mSensorManager.registerListener(mMySensorEventListener, magSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            Log.d(TAG, "magSensor sensors are not supported on current devices.");
        }

//        /*注册旋转矢量传感器监听*/
//        Sensor rotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
//        if (rotationSensor != null) {
//            mSensorManager.registerListener(mMySensorEventListener, rotationSensor, SensorManager.SENSOR_DELAY_UI);
//        } else {
//            Log.d(TAG, "rotationSensor sensors are not supported on current devices.");
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSensorManager == null) {
            return;
        }
        mSensorManager.unregisterListener(mMySensorEventListener);  //取消所有注册
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        /*解除GPS监听器*/
        if (locationManager != null) {
            locationManager.removeUpdates(myLocationListerner);
        }
        /*关闭文件流*/
        try {
            out.close();
            fstream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*传感器监听类*/
    private class MySensorEventListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            /*加速度传感器数据*/
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                //Log.d(TAG, "accelerometer data[x:" + event.values[0] + ", y:" + event.values[1] + ", z:" + event.values[2] + "]");
                tv_accelerometer_sensor.setText("x:" + event.values[0] + "\ny:" + event.values[1] + "\nz:" + event.values[2]);
                accValues = event.values.clone();    //有没有可能有读写安全问题
            }
            /*磁场传感器数据*/
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                //Log.d(TAG, "accelerometer data[x:" + event.values[0] + ", y:" + event.values[1] + ", z:" + event.values[2] + "]");
                tv_magnetic_sensor.setText("x:" + event.values[0] + "\ny:" + event.values[1] + "\nz:" + event.values[2]);
                magneticValues = event.values.clone();
            }
            /*旋转矢量传感器数据*/
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                //Log.d(TAG, "accelerometer data[x:" + event.values[0] + ", y:" + event.values[1] + ", z:" + event.values[2] + "]");
                tv_rotation_sensor.setText("x:" + event.values[0] + "\ny:" + event.values[1] + "\nz:" + event.values[2]);
                //magneticValues = event.values.clone();
            }
            /*获取屏幕方向*/
            float[] R = new float[9];
            float[] values = new float[3];
            mSensorManager.getRotationMatrix(R, null, accValues, magneticValues);
            mSensorManager.getOrientation(R, values);
            gri = accValues[0] * R[6] + accValues[1] * R[7] + accValues[2] * R[8];
            tv_R.setText(String.valueOf(gri));
            //Log.d("MainActivity", "value[0] is " + Math.toDegrees(values[0]));
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "onAccuracyChanged:" + sensor.getType() + "->" + accuracy);
        }
    }

    //GPS监听类
    private class MyLocationListerner implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            // 当GPS定位信息发生改变时，更新位置
            locationData = location;
            updateView(location);
        }

        @Override
        public void onProviderDisabled(String provider) {
            updateView(null);
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onProviderEnabled(String provider) {
            // 当GPS LocationProvider可用时，更新位置
            updateView(locationManager
                    .getLastKnownLocation(provider));
        }

        @Override
        public void onStatusChanged(String provider, int status,
                                    Bundle extras) {
        }
    }

    //线程子类
    private class MyThread1 extends Thread {

        private String name; //窗口名, 也即是线程的名字

        public MyThread1(String name) {
            this.name = name;
        }

        //循环打印数据
        @Override
        public void run() {
            while (true) {
                if (switch_start.isChecked()) {
                    write(accValues, locationData,gri);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    //按钮监听
    private class MyBtnListener implements View.OnClickListener {
        @Override
        public void onClick(View arg0) {
            if (arg0.getId() == R.id.btn_file) {
                String path = "%2fAndroid%2fdara%2f";
                Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/primary:" + path);
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");//想要展示的文件类型
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
                startActivityForResult(intent, 0);

            }
        }
    }


    //打印位置信息
    private void updateView(Location location) {
        if (location != null) {
            StringBuffer sb = new StringBuffer();
            sb.append("实时的位置信息：\n经度：");
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

    static {

    }

    // 写入文件
    private void write(float[] accData, Location locationData,float gri) {
        String acc_x, acc_y, acc_z;
        if (accData != null) {
            acc_x = String.valueOf(accData[0]);
            acc_y = String.valueOf(accData[1]);
            acc_z = String.valueOf(accData[2]);
        } else {
            acc_x = "NULL";
            acc_y = "NULL";
            acc_z = "NULL";
        }
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
        try {
            out.write(acc_x + ","
                    + acc_y + ","
                    + acc_z + ","
                    + latitude + ","
                    + longitude + ","
                    + altitude + ","
                    + Instant.now().toString() + ","
                    + speed + ","
                    + gri);
            out.newLine();
            out.flush();
            //Log.d("PAN","out");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}