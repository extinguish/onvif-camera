package com.adasplus.onvif;

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

    // 在实际情况当中，我们接收到的Probe Packet的内容并不是固定的，
    // 例如对于ONVIF Device Test Tool这种工具，我们收到的数据包
    // 当中的MessageID字段就不是a:MessageID作为节点标识，而是通过wsa:MessageID作为标识，因此
    // 我们需要都处理
    private static final String LOCAL_MSG_ID = "a:MessageID";
    private static final String LOCAL_ACTION = "a:Action";

    private static final String LOCAL_MSG_ID_1 = "wsa:MessageID";
    private static final String LOCAL_ACTION_1 = "wsa:Action";

    // the following tag are using to parse the YiJiaWen's GetCapabilities request
    private static final String USER_NAME = "wsse:Username";
    private static final String PASSWORD = "wsse:Password";
    private static final String GET_CAPABILITIES_NONCE = "wsse:Nonce";


    // the following are using to parse the YiJiaWen's SetSystemDateAndTime request
    private static final String HOUR = "tt:Hour";
    private static final String MINUTE = "tt:Minute";
    private static final String SECOND = "tt:Second";

    private static final String YEAR = "tt:Year";
    private static final String MONTH = "tt:Month";
    private static final String DAY = "tt:Day";

    private boolean isMsgID = false;
    private boolean isAction = false;
    private boolean isUserName = false;
    private boolean isPsw = false;
    private boolean isGetCapabilitiesNonce = false;

    private boolean isHour = false;
    private boolean isMinute = false;
    private boolean isSecond = false;

    private boolean isYear = false;
    private boolean isMonth = false;
    private boolean isDay = false;

    private ONVIFReqPacketHeader reqHeader;

    public ONVIFPacketHandler() {
        reqHeader = new ONVIFReqPacketHeader();
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
            case LOCAL_MSG_ID_1:
                isMsgID = true;
                break;
            case LOCAL_ACTION:
            case LOCAL_ACTION_1:
                isAction = true;
                break;
            case USER_NAME:
                isUserName = true;
                break;
            case PASSWORD:
                isPsw = true;
                break;
            case GET_CAPABILITIES_NONCE:
                isGetCapabilitiesNonce = true;
                break;
            case HOUR:
                isHour = true;
                break;
            case MINUTE:
                isMinute = true;
                break;
            case SECOND:
                isSecond = true;
                break;
            case YEAR:
                isYear = true;
                break;
            case MONTH:
                isMonth = true;
                break;
            case DAY:
                isDay = true;
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
        } else if (isGetCapabilitiesNonce) {
            String nonce = new String(ch, start, length);
            reqHeader.setCapabilitiesNonce(nonce.trim());
            isGetCapabilitiesNonce = false;
        } else if (isUserName) {
            String userName = new String(ch, start, length);
            reqHeader.setUserName(userName.trim());
            isUserName = false;
        } else if (isPsw) {
            String password = new String(ch, start, length);
            reqHeader.setUserPsw(password.trim());
            isPsw = false;
        } else if (isHour) {
            String hour = new String(ch, start, length);
            reqHeader.setHour(hour.trim());
            isHour = false;
        } else if (isMinute) {
            String minute = new String(ch, start, length);
            reqHeader.setMinute(minute.trim());
            isMinute = false;
        } else if (isSecond) {
            String second = new String(ch, start, length);
            reqHeader.setSecond(second.trim());
            isSecond = false;
        } else if (isYear) {
            String year = new String(ch, start, length);
            reqHeader.setYear(year);
            isYear = false;
        } else if (isMonth) {
            String month = new String(ch, start, length);
            reqHeader.setMonth(month);
            isMonth = false;
        } else if (isDay) {
            String day = new String(ch, start, length);
            reqHeader.setDay(day);
            isDay = false;
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

    public ONVIFReqPacketHeader getReqHeader() {
        return reqHeader;
    }
}
