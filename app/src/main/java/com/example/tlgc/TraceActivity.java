package com.example.tlgc;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.baidu.trace.LBSTraceClient;
import com.baidu.trace.Trace;
import com.baidu.trace.api.entity.OnEntityListener;
import com.baidu.trace.api.track.OnTrackListener;
import com.baidu.trace.model.OnTraceListener;
import com.baidu.trace.model.PushMessage;
import com.example.tlgc.utils.Constants;

public class TraceActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "TraceActivity";

    Button traceBtn;

    LBSTraceClient mClient;

    boolean isTraceStarted;


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
     * 打包周期
     */
    public int packInterval = Constants.DEFAULT_PACK_INTERVAL;

    App app =null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        app = (App) getApplicationContext();
        init();

    }

    private void init() {
        initListener();
        traceBtn = (Button) findViewById(R.id.btn_trace);
        traceBtn.setOnClickListener(this);

                mClient = new LBSTraceClient(getApplicationContext());
        mClient.setOnTraceListener(traceListener);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_trace:
                if (isTraceStarted) {
                    mClient.stopTrace(app.mTrace, null);
                } else {
                    mClient.startTrace(app.mTrace, null);
                }
                break;
            default:
                break;
        }
    }


    private void initListener() {

        traceListener = new OnTraceListener() {
            @Override
            public void onBindServiceCallback(int errorNo, String message) {
                Log.e(TAG, String.format("onBindServiceCallback, errorNo:%d, message:%s ", errorNo, message));
            }

            @Override
            public void onStartTraceCallback(int errorNo, String message) {
                Log.e(TAG, String.format("onStartTraceCallback, errorNo:%d, message:%s ", errorNo, message));

            }

            @Override
            public void onStopTraceCallback(int errorNo, String message) {
                Log.e(TAG, "onStopTraceCallback: " + String.format("onStopTraceCallback, errorNo:%d, message:%s ", errorNo, message));

            }

            @Override
            public void onStartGatherCallback(int errorNo, String message) {
                Log.e(TAG, String.format("onStartGatherCallback, errorNo:%d, message:%s ", errorNo, message));

            }

            @Override
            public void onStopGatherCallback(int errorNo, String message) {
                Log.e(TAG, String.format("onStopGatherCallback, errorNo:%d, message:%s ", errorNo, message));
            }

            @Override
            public void onPushCallback(byte messageType, PushMessage pushMessage) {
                Log.e(TAG, "onPushCallback, errorNo:%d, message:%s ");
            }

            @Override
            public void onInitBOSCallback(int errorNo, String message) {
                Log.e(TAG, String.format("onInitBOSCallback, errorNo:%d, message:%s ", errorNo, message));
            }
        };

    }
}
