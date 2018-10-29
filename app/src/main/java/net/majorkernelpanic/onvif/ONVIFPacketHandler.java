package net.majorkernelpanic.onvif;

import android.util.Log;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;


/**
 * 解析我们接收到的ONVIF数据包
 * 包的格式本身是xml格式的(这是SOAP协议要求的)
 * 如果ONVIF协议继续更新的话，希望更新成FlatBuffer协议，比xml要快到很多，同时数据
 * 也精简很多.
 * <p>
 * {@link SAXParser}会自动的将输入流绑定到当前的{@link DefaultHandler}当中。
 * 因此我们不需要处理数据源。
 * 输入源在某种程度上对我们是透明的.
 */
public class ONVIFPacketHandler extends DefaultHandler {
    private static final String TAG = "ONVIFPacketHandler";

    private static final String LOCAL_MSG_ID = "a:MessageID";
    private static final String LOCAL_ACTION = "a:Action";

    private boolean isMsgID = false;
    private boolean isAction = false;

    private ONVIFDevDiscoveryReqHeader reqHeader;

    public ONVIFPacketHandler() {
        reqHeader = new ONVIFDevDiscoveryReqHeader();
    }

    @Override
    public void startDocument() throws SAXException {
        Log.d(TAG, "start parsing the document");

    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        Log.d(TAG, "element : " + String.format("uri = %s, localName = %s, qName = %s", uri, localName, qName));
        switch (qName) {
            case LOCAL_MSG_ID:
                isMsgID = true;
                break;
            case LOCAL_ACTION:
                isAction = true;
                break;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (isMsgID) {
            String messageId = new String(ch, start, length);
            reqHeader.setMessageId(messageId.trim());
            Log.d(TAG, "the message ID we get are : " + messageId);
            isMsgID = false;
        } else if (isAction) {
            String action = new String(ch, start, length);
            reqHeader.setAction(action.trim());
            Log.d(TAG, "the action we get are : " + action);
            isAction = false;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        Log.d(TAG, String.format("end element of uri = %s, localName = %s, qName = %s", uri, localName, qName));
    }

    @Override
    public void endDocument() throws SAXException {
        Log.d(TAG, "End of parsing documents");
    }

    public ONVIFDevDiscoveryReqHeader getReqHeader() {
        return reqHeader;
    }
}
