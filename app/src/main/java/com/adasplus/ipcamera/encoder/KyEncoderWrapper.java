package com.adasplus.ipcamera.encoder;


public class KyEncoderWrapper {
    private static final boolean DEBUG = true;

    private long mNativeCodecObjAddress;
    private EncodeParam mEncodeParams = null;

    public KyEncoderWrapper() {
        mNativeCodecObjAddress = create();
    }

    public void setEncodeParams(EncodeParam encodeParam) {
        this.mEncodeParams = encodeParam;
    }

    public boolean prepare() {
        checkNativeCodecObjAddressValid(mNativeCodecObjAddress);
        if (mEncodeParams == null) {
            if (DEBUG) {
                throw new RuntimeException("must setup the encode format before calling prepare method");
            }
            return false;
        }
        return prepare(mNativeCodecObjAddress, mEncodeParams);
    }

    public boolean encodeFrame(byte[] frame, int frameWidth, int frameHeight) {
        checkNativeCodecObjAddressValid(mNativeCodecObjAddress);
        return encodeFrame(mNativeCodecObjAddress, frame, frameWidth, frameHeight);
    }

    private void checkNativeCodecObjAddressValid(long objAddress) {
        if (objAddress == -1) {
            throw new IllegalStateException("native codec object address are invalid");
        }
    }

    /**
     * @return 创建的native层的KyEncoder的对象的指针地址。这里的创建的地址值会用于之后的{@link #prepare(long, EncodeParam)}和
     */
    public native long create();

    /**
     * @param objAddress  native层的KyEncoder的对象的指针地址
     * @param encodeParam 编码参数
     */
    public native boolean prepare(long objAddress, EncodeParam encodeParam);

    public native boolean encodeFrame(long objAddress, byte[] frame, int length, int flag);

    public static final class EncodeParam {
        private final int width;
        private final int height;
        private final int frameRate;
        private final int colorFormat;
        private final long bitRate;

        public EncodeParam(int width, int height, int frameRate, int colorFormat, long bitrate) {
            this.width = width;
            this.height = height;
            this.frameRate = frameRate;
            this.colorFormat = colorFormat;
            this.bitRate = bitrate;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getFrameRate() {
            return frameRate;
        }

        public int getColorFormat() {
            return colorFormat;
        }

        public long getBitRate() {
            return bitRate;
        }
    }
}
