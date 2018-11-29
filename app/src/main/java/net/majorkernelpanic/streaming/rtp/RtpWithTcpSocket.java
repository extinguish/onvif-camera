package net.majorkernelpanic.streaming.rtp;

/**
 * 当用户要求RTP层的数据传输通过TCP来进行时，我们不能再使用{@link RtpSocket}来完成了.
 * TCP本身的数据传输过程是在一个建立好的通道上进行传输的，即类似于流的方式进行传输.
 * 而UDP本身是以包为单位进行传输的，即以{@link java.net.DatagramPacket}为单元
 * 逐个进行传输的.
 */
public class RtpWithTcpSocket implements Runnable {
    private static final String TAG = "RtpWithTcpSocket";

    // 模仿RtpSocket当中的实现，使用一个单独的Thread来进行数据的发送
    private Thread mThread;
    private int mTransferChannel;

    public RtpWithTcpSocket() {

    }


    @Override
    public void run() {

    }
}
