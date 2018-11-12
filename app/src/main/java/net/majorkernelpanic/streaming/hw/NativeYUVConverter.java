package net.majorkernelpanic.streaming.hw;

public class NativeYUVConverter {

    /**
     * @param src   相机预览的源数据
     * @param dst   被转换之后的数据
     * @param ySize 数据的大小(width * height)
     */
    public static native void nv21ToYUV420SP(byte[] src, byte[] dst, int ySize);

    public static native void nv21ToYUV420P(byte[] src, byte[] dst, int ySize);

    public static native void yuv420SPToYUV420P(byte[] src, byte[] dst, int ySize);

    public static native void nv21TOARGB(byte[] src, int[] dst, int width, int height);

    public static native void fixGLPixel(int[] src, int[] dst, int width, int height);

    public static native void nv21Transform(byte[] src, byte[] dst, int srcWidth, int srcHeight, int directionFlag);

    /**
     * 按照RTMP Service当中的yuv转换
     *
     * @param src 需要被转换的原始的ShareBuffer数据
     * @return 被转换后的数据, 转换后的数据被MediaCodec进行编码处理
     */
    public static native byte[] yv12ToI420(byte[] src, int width, int height);

}
