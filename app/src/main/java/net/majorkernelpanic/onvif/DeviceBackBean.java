package net.majorkernelpanic.onvif;

public class DeviceBackBean {
    private String userName;

    private String psw;
    private String ipAddress;
    private String serviceUrl;
    private String uuid;
    private String mediaUrl;
    private String ptzUrl;
    private String imageUrl;
    private String eventUrl;

    private String videoEncodeFormat;

    private String sourceWidth = "800";
    private String sourceHeight = "400";
    private String encodeWidth = "480";
    private String encodeHeight = "320";

    private String frameRateLimit = "25";
    private String bitrateLimit = "10000";
    // the default port of which using to perform SOAP web service
    private String port = "8080";
    // the default port of which using to RTSP streaming action
    private String rtspPort = "8086";
    private String mediaTimeout = "PT30S";

    public DeviceBackBean() {
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPsw() {
        return psw;
    }

    public void setPsw(String psw) {
        this.psw = psw;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getPtzUrl() {
        return ptzUrl;
    }

    public void setPtzUrl(String ptzUrl) {
        this.ptzUrl = ptzUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getEventUrl() {
        return eventUrl;
    }

    public void setEventUrl(String eventUrl) {
        this.eventUrl = eventUrl;
    }

    public String getVideoEncodeFormat() {
        return videoEncodeFormat;
    }

    public void setVideoEncodeFormat(String videoEncodeFormat) {
        this.videoEncodeFormat = videoEncodeFormat;
    }

    public String getMediaTimeout() {
        return mediaTimeout;
    }

    public void setMediaTimeout(String mediaTimeout) {
        this.mediaTimeout = mediaTimeout;
    }

    public String getRtspPort() {
        return rtspPort;
    }

    public void setRtspPort(String rtspPort) {
        this.rtspPort = rtspPort;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getBitrateLimit() {
        return bitrateLimit;
    }

    public void setBitrateLimit(String bitrateLimit) {
        this.bitrateLimit = bitrateLimit;
    }

    public String getFrameRateLimit() {
        return frameRateLimit;
    }

    public void setFrameRateLimit(String frameRateLimit) {
        this.frameRateLimit = frameRateLimit;
    }

    public String getEncodeHeight() {
        return encodeHeight;
    }

    public void setEncodeHeight(String encodeHeight) {
        this.encodeHeight = encodeHeight;
    }

    public String getEncodeWidth() {
        return encodeWidth;
    }

    public void setEncodeWidth(String encodeWidth) {
        this.encodeWidth = encodeWidth;
    }

    public String getSourceHeight() {
        return sourceHeight;
    }

    public void setSourceHeight(String sourceHeight) {
        this.sourceHeight = sourceHeight;
    }

    public String getSourceWidth() {
        return sourceWidth;
    }

    public void setSourceWidth(String sourceWidth) {
        this.sourceWidth = sourceWidth;
    }

}
