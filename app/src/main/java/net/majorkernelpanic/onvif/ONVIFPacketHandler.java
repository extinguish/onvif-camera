package net.majorkernelpanic.onvif;

import android.util.Log;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


/**
 * 解析我们接收到的ONVIF数据包
 * 包的格式本身是xml格式的(这是SOAP协议要求的)
 * 如果ONVIF协议继续更新的话，希望更新成FlatBuffer协议，比xml要快到很多，同时数据
 * 也精简很多.
 *
 * 位于项目根目录下的
 */
public class ONVIFPacketHandler extends DefaultHandler {
    private static final String TAG = "ONVIFPacketHandler";

    private static final String TAG_MSG_ID = "MessageID";
    private static final String TAG_ACTION = "Action";

    private String rawPacketData;
    private ONVIFDevDiscoveryReqHeader reqHeader;
    /**
     * 当前解析到的tag
     */
    private String preTag;

    public ONVIFPacketHandler(String packet) {
        Log.d(TAG, "the data need to parse out are " + packet);
        this.rawPacketData = packet;
    }

    @Override
    public void startDocument() throws SAXException {
        Log.d(TAG, "start parsing the document");
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        Log.d(TAG, String.format("uri = %s, localName = %s, qName = %s", uri, localName, qName));

    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        String data = new String(ch, start, length);
        Log.d(TAG, "current data are " + data);

    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        Log.d(TAG, String.format("end element of uri = %s, localName = %s, qName = %s", uri, localName, qName));
        switch (localName) {

        }
    }

    @Override
    public void endDocument() throws SAXException {
        Log.d(TAG, "End of parsing documents");
    }

    public ONVIFDevDiscoveryReqHeader getReqHeader() {
        return reqHeader;
    }
}
