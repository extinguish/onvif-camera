package com.adasplus.multicast_client;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "multicastListener";

    static {
        System.loadLibrary("multicast_listener");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_start_listen).setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start_listen:
                startListenMulticastPacket();
                break;
        }
    }

    private void startListenMulticastPacket() {
        Log.d(TAG, "start listen multicast packet");
        listenMulticastPacket();
    }

    public native void listenMulticastPacket();
}


