package com.example.tlgc;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.model.LatLng;
import com.baidu.trace.api.entity.OnEntityListener;
import com.baidu.trace.api.fence.FenceAlarmPushInfo;
import com.baidu.trace.api.fence.MonitoredAction;
import com.baidu.trace.api.track.DistanceRequest;
import com.baidu.trace.api.track.DistanceResponse;
import com.baidu.trace.api.track.HistoryTrackRequest;
import com.baidu.trace.api.track.HistoryTrackResponse;
import com.baidu.trace.api.track.LatestPoint;
import com.baidu.trace.api.track.LatestPointResponse;
import com.baidu.trace.api.track.OnTrackListener;
import com.baidu.trace.api.track.SupplementMode;
import com.baidu.trace.api.track.TrackPoint;
import com.baidu.trace.model.CoordType;
import com.baidu.trace.model.LocationMode;
import com.baidu.trace.model.OnTraceListener;
import com.baidu.trace.model.ProcessOption;
import com.baidu.trace.model.PushMessage;
import com.baidu.trace.model.SortType;
import com.baidu.trace.model.StatusCodes;
import com.baidu.trace.model.TraceLocation;
import com.baidu.trace.model.TransportMode;
import com.baidubce.util.StringUtils;
import com.example.tlgc.model.CurrentLocation;
import com.example.tlgc.receiver.TrackReceiver;
import com.example.tlgc.utils.CommonUtil;
import com.example.tlgc.utils.Constants;
import com.example.tlgc.utils.MapUtil;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity implements View.OnClickListener , SensorEventListener {

    private static final String TAG = "MapActivity";
    private App app = null;

    private Button traceBtn = null;

    private NotificationManager notificationManager = null;

    private PowerManager powerManager = null;

    private PowerManager.WakeLock wakeLock = null;

    private TrackReceiver trackReceiver = null;
    private SensorManager mSensorManager;

    private Double lastX = 0.0;
    private int mCurrentDirection = 0;

    /**
     * 地图工具
     */
    private MapUtil mapUtil = null;

    /**
     * 轨迹服务监听器
     */
    private OnTraceListener traceListener = null;

    /**
     * 轨迹监听器(用于接收纠偏后实时位置回调)
     */
    private OnTrackListener trackListener = null;

    /**
     * Entity监听器(用于接收实时定位回调)
     */
    private OnEntityListener entityListener = null;

    /**
     * 实时定位任务
     */
    private RealTimeHandler realTimeHandler = new RealTimeHandler();

    private RealTimeLocRunnable realTimeLocRunnable = null;

    private boolean isRealTimeRunning = true;

    private int notifyId = 0;

    private int pageIndex = 1;

    /**
     * 打包周期
     */
    public int packInterval = Constants.DEFAULT_PACK_INTERVAL;
    private boolean firstLocate = true;

    /**
     * 查询历史轨迹开始时间
     */
    public long startTime = CommonUtil.getCurrentTime();

    /**
     * 查询历史轨迹结束时间
     */
    public long endTime = CommonUtil.getCurrentTime();
    /**
     * 轨迹点集合
     */
    private List<LatLng> trackPoints;
    private HistoryTrackRequest historyTrackRequest;
    private SDKReceiver mReceiver;
    /**
     * 构造广播监听类，监听 SDK key 验证以及网络异常广播
     */
    public class SDKReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            String s = intent.getAction();

            if (s.equals(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_ERROR)) {
                Toast.makeText(MapActivity.this,"AK验证失败，地图功能无法正常使用", Toast.LENGTH_SHORT).show();
            } else if (s.equals(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_OK)) {
                Toast.makeText(MapActivity.this,"AK验证成功", Toast.LENGTH_SHORT).show();
            } else if (s.equals(SDKInitializer.SDK_BROADCAST_ACTION_STRING_NETWORK_ERROR)) {
                Toast.makeText(MapActivity.this,"网络错误", Toast.LENGTH_SHORT).show();
            }
        }
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        // AK的授权需要一定的时间，在授权成功之前地图相关操作会出现异常；AK授权成功后会发送广播通知，我们这里注册 SDK 广播监听者

        startTime = startTime-(18 *60*60);
        IntentFilter iFilter = new IntentFilter();
        iFilter.addAction(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_OK);
        iFilter.addAction(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_ERROR);
        iFilter.addAction(SDKInitializer.SDK_BROADCAST_ACTION_STRING_NETWORK_ERROR);
        mReceiver = new SDKReceiver();
        registerReceiver(mReceiver, iFilter);
        init();

    }

    private void init() {
        initListener();
        app = (App) getApplicationContext();
        mapUtil = MapUtil.getInstance();
        mapUtil.init((MapView) findViewById(R.id.mapView));
        mapUtil.setCenter(app);
        startRealTimeLoc(Constants.LOC_INTERVAL);
        powerManager = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        traceBtn = (Button) findViewById(R.id.btn_trace);
        traceBtn.setOnClickListener(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);// 获取传感器管理服务
        app.mClient.setOnTraceListener(traceListener);
        trackPoints = new ArrayList<>();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        //每次方向改变，重新给地图设置定位数据，用上一次的经纬度
        double x = sensorEvent.values[SensorManager.DATA_X];
        if (Math.abs(x - lastX) > 1.0) {// 方向改变大于1度才设置，以免地图上的箭头转动过于频繁
            mCurrentDirection = (int) x;
            if (!CommonUtil.isZeroPoint(CurrentLocation.latitude, CurrentLocation.longitude)) {
                mapUtil.updateMapLocation(new LatLng(CurrentLocation.latitude, CurrentLocation.longitude), (float) mCurrentDirection);
            }
        }
        lastX = x;

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_trace:
                if (app.isTraceStarted) {
                    app.mClient.stopTrace(app.mTrace, traceListener);
                    stopRealTimeLoc();
                } else {
                    app.mClient.startTrace(app.mTrace, traceListener);
                    if (Constants.DEFAULT_PACK_INTERVAL != packInterval) {
                        stopRealTimeLoc();
                        startRealTimeLoc(packInterval);
                    }
                }
                break;
            default:
                break;
        }
    }



    /**
     * 实时定位任务
     *
     * @author baidu
     */
    class RealTimeLocRunnable implements Runnable {

        private int interval = 0;

        public RealTimeLocRunnable(int interval) {
            this.interval = interval;
        }

        @Override
        public void run() {
            if (isRealTimeRunning) {
                app.getCurrentLocation(entityListener, trackListener);
                realTimeHandler.postDelayed(this, interval * 1000);
            }
        }
    }

    public void startRealTimeLoc(int interval) {
        isRealTimeRunning = true;
        realTimeLocRunnable = new RealTimeLocRunnable(interval);
        realTimeHandler.post(realTimeLocRunnable);
    }

    public void stopRealTimeLoc() {
        isRealTimeRunning = false;
        if (null != realTimeHandler && null != realTimeLocRunnable) {
            realTimeHandler.removeCallbacks(realTimeLocRunnable);
        }
        app.mClient.stopRealTimeLoc();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (null == data) {
            return;
        }

        if (data.hasExtra("locationMode")) {
            LocationMode locationMode = LocationMode.valueOf(data.getStringExtra("locationMode"));
            app.mClient.setLocationMode(locationMode);
        }

        if (data.hasExtra("isNeedObjectStorage")) {
            boolean isNeedObjectStorage = data.getBooleanExtra("isNeedObjectStorage", false);
            app.mTrace.setNeedObjectStorage(isNeedObjectStorage);
        }

        if (data.hasExtra("gatherInterval") || data.hasExtra("packInterval")) {
            int gatherInterval = data.getIntExtra("gatherInterval", Constants.DEFAULT_GATHER_INTERVAL);
            int packInterval = data.getIntExtra("packInterval", Constants.DEFAULT_PACK_INTERVAL);
            MapActivity.this.packInterval = packInterval;
            app.mClient.setInterval(gatherInterval, packInterval);
        }

        //        if (data.hasExtra("supplementMode")) {
        //            mSupplementMode = SupplementMode.valueOf(data.getStringExtra("supplementMode"));
        //        }
    }

    private void initListener() {

        trackListener = new OnTrackListener() {

            @Override
            public void onLatestPointCallback(LatestPointResponse response) {
                if (StatusCodes.SUCCESS != response.getStatus()) {
                    return;
                }

                LatestPoint point = response.getLatestPoint();
                if (null == point || CommonUtil.isZeroPoint(point.getLocation().getLatitude(), point.getLocation()
                        .getLongitude())) {
                    return;
                }

                LatLng currentLatLng = mapUtil.convertTrace2Map(point.getLocation());
                if (null == currentLatLng) {
                    return;
                }

                if (firstLocate) {
                    firstLocate = false;
                    Toast.makeText(MapActivity.this, "起点获取中，请稍后...", Toast.LENGTH_SHORT).show();
                    queryHistoryTrack();
                    return;
                }

                CurrentLocation.locTime = point.getLocTime();
                CurrentLocation.latitude = currentLatLng.latitude;
                CurrentLocation.longitude = currentLatLng.longitude;

                if (null != mapUtil) {
                    mapUtil.updateStatus(currentLatLng, true);
                }

                if (trackPoints == null) {
                    return;
                }
                trackPoints.add(currentLatLng);
                endTime = CommonUtil.getCurrentTime();
                endTime =endTime-(5*60*60);
                Log.e(TAG, "onLatestPointCallback: "+"差值：" + (endTime - startTime) );
//                ToastUtils.showToastBottom(TracingActivity.this, "差值：" + (endTime - startTime));

            }
            @Override
            public void onHistoryTrackCallback(HistoryTrackResponse response) {
                try {

                    int total = response.getTotal();
                    if (StatusCodes.SUCCESS != response.getStatus()) {
                        Toast.makeText(MapActivity.this,response.getMessage(),Toast.LENGTH_SHORT).show();

                    } else if (0 == total) {
                        Toast.makeText(MapActivity.this,"no_track_data",Toast.LENGTH_SHORT).show();
                    } else {
                        List<TrackPoint> points = response.getTrackPoints();
                        if (null != points) {
                            for (TrackPoint trackPoint : points) {
                                if (!CommonUtil.isZeroPoint(trackPoint.getLocation().getLatitude(),
                                        trackPoint.getLocation().getLongitude())) {
                                    trackPoints.add(MapUtil.convertTrace2Map(trackPoint.getLocation()));
                                    Toast.makeText(MapActivity.this,"轨迹：" + trackPoints.size(),Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }

                    //查找下一页数据
                    if (total > Constants.PAGE_SIZE * pageIndex) {
                        historyTrackRequest.setPageIndex(++pageIndex);
                        queryHistoryTrack();
                    } else {
                        mapUtil.drawHistoryTrack(trackPoints, true, mCurrentDirection);//画轨迹
                        queryDistance();// 查询里程
                    }

                } catch (Exception e) {

                }
            }

            @Override
            public void onDistanceCallback(DistanceResponse response) {
                Toast.makeText(MapActivity.this, "里程：" + (int)response.getDistance() + "米",Toast.LENGTH_SHORT).show();
                super.onDistanceCallback(response);
            }

        };

        entityListener = new OnEntityListener() {

            @Override
            public void onReceiveLocation(TraceLocation location) {

                if (StatusCodes.SUCCESS != location.getStatus() || CommonUtil.isZeroPoint(location.getLatitude(),
                        location.getLongitude())) {
                    return;
                }
                LatLng currentLatLng = mapUtil.convertTraceLocation2Map(location);
                if (null == currentLatLng) {
                    return;
                }
                CurrentLocation.locTime = CommonUtil.toTimeStamp(location.getTime());
                CurrentLocation.latitude = currentLatLng.latitude;
                CurrentLocation.longitude = currentLatLng.longitude;

                if (null != mapUtil) {
                    mapUtil.updateStatus(currentLatLng, true);
                }
            }

        };


        traceListener = new OnTraceListener() {
            @Override
            public void onBindServiceCallback(int errorNo, String message) {
                Toast.makeText(MapActivity.this, String.format("onBindServiceCallback, errorNo:%d, message:%s ", errorNo, message), Toast.LENGTH_LONG).show();
                Log.e(TAG,String.format("onBindServiceCallback, errorNo:%d, message:%s ", errorNo, message) );
            }
            @Override
            public void onStartTraceCallback(int errorNo, String message) {
                Log.e(TAG, String.format("onStartTraceCallback, errorNo:%d, message:%s ", errorNo, message) );
                Toast.makeText(MapActivity.this, String.format("onStartTraceCallback, errorNo:%d, message:%s ", errorNo, message), Toast.LENGTH_LONG).show();
                if (StatusCodes.SUCCESS == errorNo || StatusCodes.START_TRACE_NETWORK_CONNECT_FAILED <= errorNo) {
                    app.isTraceStarted = true;
                    traceBtn.setText("关闭");
                    SharedPreferences.Editor editor = app.trackConf.edit();
                    editor.putBoolean("is_trace_started", true);
                    editor.apply();
                    registerReceiver();

                }

                if (errorNo == 0) {
                    app.mClient.startGather(null);
                }
            }

            @Override
            public void onStopTraceCallback(int errorNo, String message) {
                Log.e(TAG, "onStopTraceCallback: "+String.format("onStopTraceCallback, errorNo:%d, message:%s ", errorNo, message));
                Toast.makeText(MapActivity.this, String.format("onStopTraceCallback, errorNo:%d, message:%s ", errorNo, message), Toast.LENGTH_LONG).show();
                if (StatusCodes.SUCCESS == errorNo || StatusCodes.CACHE_TRACK_NOT_UPLOAD == errorNo) {
                    app.isTraceStarted = false;
                    traceBtn.setText("开启");
                    app.isGatherStarted = false;
                    // 停止成功后，直接移除is_trace_started记录（便于区分用户没有停止服务，直接杀死进程的情况）
                    SharedPreferences.Editor editor = app.trackConf.edit();
                    editor.remove("is_trace_started");
                    editor.remove("is_gather_started");
                    editor.apply();
                    unregisterPowerReceiver();
                    firstLocate = true;


                }

            }
            @Override
            public void onStartGatherCallback(int errorNo, String message) {
                Log.e(TAG, String.format("onStartGatherCallback, errorNo:%d, message:%s ", errorNo, message));
                Toast.makeText(MapActivity.this, String.format("onStartGatherCallback, errorNo:%d, message:%s ", errorNo, message), Toast.LENGTH_LONG).show();
                if (StatusCodes.SUCCESS == errorNo || StatusCodes.GATHER_STARTED == errorNo) {
                    app.isGatherStarted = true;
                    SharedPreferences.Editor editor = app.trackConf.edit();
                    editor.putBoolean("is_gather_started", true);
                    editor.apply();
                    startRealTimeLoc(packInterval);
                }

            }
            @Override
            public void onStopGatherCallback(int errorNo, String message) {
                Log.e(TAG, String.format("onStopGatherCallback, errorNo:%d, message:%s ", errorNo, message));
                Toast.makeText(MapActivity.this, String.format("onStopGatherCallback, errorNo:%d, message:%s ", errorNo, message), Toast.LENGTH_LONG).show();
                if (StatusCodes.SUCCESS == errorNo || StatusCodes.GATHER_STOPPED == errorNo) {
                    app.isGatherStarted = false;
                    SharedPreferences.Editor editor = app.trackConf.edit();
                    editor.remove("is_gather_started");
                    editor.apply();
                    firstLocate = true;
                    startRealTimeLoc(Constants.LOC_INTERVAL);
                }

            }
            @Override
            public void onPushCallback(byte messageType, PushMessage pushMessage) {
                Log.e(TAG, "onPushCallback, errorNo:%d, message:%s ");
                if (messageType < 0x03 || messageType > 0x04) {
                    Toast.makeText(MapActivity.this, pushMessage.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
                FenceAlarmPushInfo alarmPushInfo = pushMessage.getFenceAlarmPushInfo();
                if (null == alarmPushInfo) {
                    Toast.makeText(MapActivity.this, String.format("onPushCallback, messageType:%d, messageContent:%s ", messageType, pushMessage), Toast.LENGTH_LONG).show();
                    return;
                }
                StringBuffer alarmInfo = new StringBuffer();
                alarmInfo.append("您于")
                        .append(CommonUtil.getHMS(alarmPushInfo.getCurrentPoint().getLocTime() * 1000))
                        .append(alarmPushInfo.getMonitoredAction() == MonitoredAction.enter ? "进入" : "离开")
                        .append(messageType == 0x03 ? "云端" : "本地")
                        .append("围栏：").append(alarmPushInfo.getFenceName());

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
                    Notification notification = new Notification.Builder(app)
                            .setContentTitle("报警推送")
                            .setContentText(alarmInfo.toString())
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setWhen(System.currentTimeMillis()).build();
                    notificationManager.notify(notifyId++, notification);
                }
            }

            @Override
            public void onInitBOSCallback(int errorNo, String message) {
                Log.e(TAG, String.format("onInitBOSCallback, errorNo:%d, message:%s ",errorNo,message));
                Toast.makeText(MapActivity.this, String.format("onInitBOSCallback, errorNo:%d, message:%s ", errorNo, message), Toast.LENGTH_LONG).show();
            }
        };

    }
    /**
     * 查询历史里程
     */
    private void queryDistance() {
        DistanceRequest distanceRequest = new DistanceRequest(app.getTag(), app.serviceId, app.entityName);
        distanceRequest.setStartTime(startTime); // 设置开始时间
        distanceRequest.setEndTime(endTime);     // 设置结束时间
        distanceRequest.setProcessed(true);      // 纠偏
        ProcessOption processOption = new ProcessOption();// 创建纠偏选项实例
        processOption.setNeedDenoise(true);// 去噪
        processOption.setNeedMapMatch(true);// 绑路
        processOption.setTransportMode(TransportMode.walking);// 交通方式为步行
        distanceRequest.setProcessOption(processOption);// 设置纠偏选项
        distanceRequest.setSupplementMode(SupplementMode.no_supplement);// 里程填充方式为无
        app.mClient.queryDistance(distanceRequest, trackListener);// 查询里程

    }
    /**
     * 查询历史轨迹
     */
    private void queryHistoryTrack() {

        historyTrackRequest = new HistoryTrackRequest();
        ProcessOption processOption = new ProcessOption();//纠偏选项
        processOption.setRadiusThreshold(100);//精度过滤
        processOption.setTransportMode(TransportMode.walking);//交通方式，默认为驾车
        processOption.setNeedDenoise(true);//去噪处理，默认为false，不处理
        processOption.setNeedVacuate(true);//设置抽稀，仅在查询历史轨迹时有效，默认需要false
        processOption.setNeedMapMatch(true);//绑路处理，将点移到路径上，默认不需要false
        historyTrackRequest.setProcessOption(processOption);
        app.initRequest(historyTrackRequest);
        /**
         * 设置里程补偿方式，当轨迹中断5分钟以上，会被认为是一段中断轨迹，默认不补充
         * 比如某些原因造成两点之间的距离过大，相距100米，那么在这两点之间的轨迹如何补偿
         * SupplementMode.driving：补偿轨迹为两点之间最短驾车路线
         * SupplementMode.riding：补偿轨迹为两点之间最短骑车路线
         * SupplementMode.walking：补偿轨迹为两点之间最短步行路线
         * SupplementMode.straight：补偿轨迹为两点之间直线
         */
        historyTrackRequest.setSupplementMode(SupplementMode.no_supplement);
        historyTrackRequest.setSortType(SortType.asc);//设置返回结果的排序规则，默认升序排序；升序：集合中index=0代表起始点；降序：结合中index=0代表终点。
        historyTrackRequest.setCoordTypeOutput(CoordType.bd09ll);//设置返回结果的坐标类型，默认为百度经纬度

        /**
         * 设置是否返回纠偏后轨迹，默认不纠偏
         * true：打开轨迹纠偏，返回纠偏后轨迹;
         * false：关闭轨迹纠偏，返回原始轨迹。
         * 打开纠偏时，请求时间段内轨迹点数量不能超过2万，否则将返回错误。
         */
        historyTrackRequest.setProcessed(true);

        historyTrackRequest.setServiceId(app.serviceId);//设置轨迹服务id，Trace中的id
        historyTrackRequest.setEntityName(app.entityName);//Trace中的entityName

        /**
         * 设置startTime和endTime，会请求这段时间内的轨迹数据;
         * 这里查询采集开始到采集结束之间的轨迹数据
         */
        historyTrackRequest.setStartTime(startTime);
        historyTrackRequest.setEndTime(endTime);
        historyTrackRequest.setPageIndex(pageIndex);
        historyTrackRequest.setPageSize(Constants.PAGE_SIZE);
        app.mClient.queryHistoryTrack(historyTrackRequest, trackListener);//发起请求，设置回调监听

    }


    static class RealTimeHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    /**
     * 注册广播（电源锁、GPS状态）
     */
    @SuppressLint("InvalidWakeLockTag")
    private void registerReceiver() {
        if (app.isRegisterReceiver) {
            return;
        }

        if (null == wakeLock) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "track upload");
        }
        if (null == trackReceiver) {
            trackReceiver = new TrackReceiver(wakeLock);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(StatusCodes.GPS_STATUS_ACTION);
        app.registerReceiver(trackReceiver, filter);
        app.isRegisterReceiver = true;

    }

    private void unregisterPowerReceiver() {
        if (!app.isRegisterReceiver) {
            return;
        }
        if (null != trackReceiver) {
            app.unregisterReceiver(trackReceiver);
        }
        app.isRegisterReceiver = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        startRealTimeLoc(packInterval);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapUtil.onResume();

        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_UI);

        // 在Android 6.0及以上系统，若定制手机使用到doze模式，请求将应用添加到白名单。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = app.getPackageName();
            boolean isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName);
            if (!isIgnoring) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                try {
                    startActivity(intent);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapUtil.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopRealTimeLoc();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapUtil.clear();
        stopRealTimeLoc();
    }
}
