package au.com.codeka.common.model;

import org.apache.commons.lang3.StringEscapeUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import au.com.codeka.common.protobuf.Messages;

public class BaseChatMessage {
    protected int mID;
    protected String mMessage;
    protected String mEmpireKey;
    protected String mAllianceKey;
    protected BaseEmpire mEmpire;
    protected DateTime mDatePosted;
    protected String mMessageEn;
    protected Integer mConversationID;

    public BaseChatMessage() {
        mDatePosted = new DateTime(DateTimeZone.UTC);
    }
    public BaseChatMessage(String message) {
        mMessage = message;
    }

    public int getID() {
        return mID;
    }
    public String getMessage() {
        return mMessage;
    }
    public void setMessage(String msg) {
        mMessage = msg;
    }
    public String getEmpireKey() {
        return mEmpireKey;
    }
    public BaseEmpire getEmpire() {
        return mEmpire;
    }
    public void setEmpire(BaseEmpire emp) {
        mEmpire = emp;
        if (emp != null) {
            mEmpireKey = emp.getKey();
        }
    }
    public void setAllianceChat(boolean isAllianceChat) {
        if (isAllianceChat && mEmpire != null && mEmpire.getAlliance() != null) {
            mAllianceKey = mEmpire.getAlliance().getKey();
        } else {
            mAllianceKey = null;
        }
    }
    public DateTime getDatePosted() {
        return mDatePosted;
    }
    public String getAllianceKey() {
        return mAllianceKey;
    }
    public String getEnglishMessage() {
        return mMessageEn;
    }
    public Integer getConversationID() {
        return mConversationID;
    }

    public void fromProtocolBuffer(Messages.ChatMessage pb) {
        if (pb.hasId()) {
            mID = pb.getId();
        }
        mMessage = pb.getMessage();
        if (pb.getEmpireKey() != null && !pb.getEmpireKey().equals("")) {
            mEmpireKey = pb.getEmpireKey();
        }
        if (pb.getAllianceKey() != null && !pb.getAllianceKey().equals("")) {
            mAllianceKey = pb.getAllianceKey();
        }
        mDatePosted = new DateTime(pb.getDatePosted() * 1000, DateTimeZone.UTC);
        if (pb.hasMessageEn() && !pb.getMessageEn().equals("")) {
            mMessageEn = pb.getMessageEn();
        }
        if (pb.hasConversationId()) {
            mConversationID = pb.getConversationId();
        }
    }

    public void toProtocolBuffer(Messages.ChatMessage.Builder pb, boolean encodeHtml) {
        pb.setId(mID);
        if (encodeHtml) {
            pb.setMessage(StringEscapeUtils.escapeHtml4(mMessage));
        } else {
            pb.setMessage(mMessage);
        }
        if (mEmpireKey != null) {
            pb.setEmpireKey(mEmpireKey);
        }
        if (mAllianceKey != null) {
            pb.setAllianceKey(mAllianceKey);
        }
        pb.setDatePosted(mDatePosted.getMillis() / 1000);
        if (mMessageEn != null && mMessageEn.length() > 0) {
            pb.setMessageEn(mMessageEn);
        }
        if (mConversationID != null) {
            pb.setConversationId(mConversationID);
        }
    }
}
