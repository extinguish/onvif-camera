// IAdasResultListener.aidl
package com.adasplus.kylauncher;

// Declare any non-default types here with import statements

import android.os.ParcelFileDescriptor;
import com.adasplus.data.CameraParameter;
import com.adasplus.data.AdasConfig;

interface IAdasResultListener {

    //Get BackFileDescriptor to render the back preview.
    ParcelFileDescriptor getBackFileDescriptor();

    //Get FrontFileDescriptor to render the front preview.
    ParcelFileDescriptor getFrontFileDescriptor();

    boolean adasRunning();

    void saveParams();

    void setAdasConfig(in AdasConfig config);

    AdasConfig getAdasConfig();

    //Prepare merchantId.
    void setMerchantId(String merchantId);

    //Prepare uuid.
    void setUuid(String uuid);

    //Prepare secretkey
    void setSecretKey(String key);

    boolean isSmokingEnable();

    boolean isCallphoneEnable();

    void setSmokingEnable(boolean enable);

    void setCallphoneEnable(boolean enable);

    void setLampEnable(boolean enable);

    boolean isLampEnable(boolean enable);

    void overrideCalirate(String msg);

    void setYawnEnable(boolean enable);

    boolean isYawnEnable();

}
