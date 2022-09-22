package com.example.dataacquire;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "sensor-sample";
    private TextView tv_accelerometer_sensor;
    private SensorManager mSensorManager;
    private MySensorEventListener mMySensorEventListener;
    private MyLocationListerner myLocationListerner;

    // 定义LocationManager对象
    private LocationManager locationManager;
    private TextView tv_location;

    private String provider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv_accelerometer_sensor = findViewById(R.id.tv_accelerometer_sensor);
        tv_location = (TextView) findViewById(R.id.location_tv);

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
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mSensorManager == null) {
            return;
        }

        Sensor accelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometerSensor != null) {
            //注册传感器的监听器
            mSensorManager.registerListener(mMySensorEventListener, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            Log.d(TAG, "Accelerometer sensors are not supported on current devices.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSensorManager == null) {
            return;
        }
        //unregister all listener
        mSensorManager.unregisterListener(mMySensorEventListener);
    }


    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        //关闭时解除GPS监听器
        if (locationManager != null) {
            locationManager.removeUpdates(myLocationListerner);
        }
    }

    //传感器监听类
    private class MySensorEventListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                Log.d(TAG, "accelerometer data[x:" + event.values[0] + ", y:" + event.values[1] + ", z:" + event.values[2] + "]");
                tv_accelerometer_sensor.setText("[x:" + event.values[0] + ", y:" + event.values[1] + ", z:" + event.values[2] + "]");
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "onAccuracyChanged:" + sensor.getType() + "->" + accuracy);
        }
    }

    //GPS监听类
    private class MyLocationListerner implements LocationListener{
        @Override
        public void onLocationChanged(Location location) {
            // 当GPS定位信息发生改变时，更新位置
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
}