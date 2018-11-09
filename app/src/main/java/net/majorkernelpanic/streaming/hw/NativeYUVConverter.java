package net.majorkernelpanic.streaming.hw;

public class NativeYUVConverter {

    /**
     * @param src   相机预览的源数据
     * @param dst   被转换之后的数据
     * @param ySize 数据的大小(width * height)
     */
    public static native void nv21ToYUV420SP(byte[] src, byte[] dst, int ySize);

    static public native void nv21ToYUV420P(byte[] src, byte[] dst, int ySize);

    static public native void yuv420SPToYUV420P(byte[] src, byte[] dst, int ySize);

    static public native void nv21TOARGB(byte[] src, int[] dst, int width, int height);

    static public native void fixGLPixel(int[] src, int[] dst, int width, int height);

    static public native void nv21Transform(byte[] src, byte[] dst, int srcWidth, int srcHeight, int directionFlag);
}
