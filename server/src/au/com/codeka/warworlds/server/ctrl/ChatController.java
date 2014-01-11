package au.com.codeka.warworlds.server.ctrl;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.data.DB;
import au.com.codeka.warworlds.server.data.SqlStmt;
import au.com.codeka.warworlds.server.data.Transaction;
import au.com.codeka.warworlds.server.model.ChatConversation;
import au.com.codeka.warworlds.server.model.ChatMessage;

public class ChatController {
    private final Logger log = LoggerFactory.getLogger(ChatController.class);
    private DataBase db;

    public ChatController() {
        db = new DataBase();
    }
    public ChatController(Transaction trans) {
        db = new DataBase(trans);
    }

    public ArrayList<ChatConversation> getConversationsForEmpire(int empireID) throws RequestException {
        try {
            return db.getConversationsForEmpire(empireID);
        } catch (Exception e) {
            throw new RequestException(e);
        }
    }

    public ChatConversation getConversation(int id) throws RequestException {
        try {
            return db.getConversation(id);
        } catch (Exception e) {
            throw new RequestException(e);
        }
    }

    public void addParticipant(ChatConversation conversation, int empireID) throws RequestException {
        try {
            db.addParticipant(conversation.getID(), empireID);
            conversation.addParticipant(empireID, false);
        } catch (Exception e) {
            throw new RequestException(e);
        }
    }

    public void removeParticipant(ChatConversation conversation, int empireID) throws RequestException {
        try {
            db.removeParticipant(conversation.getID(), empireID);
            int index = -1;
            for (int i = 0; i < conversation.getParticipants().size(); i++) {
                if (conversation.getParticipants().get(i).getEmpireID() == empireID) {
                    index = i;
                }
            }
            if (index >= 0) {
                conversation.getParticipants().remove(index);
            }
        } catch (Exception e) {
            throw new RequestException(e);
        }
    }

    public void postMessage(ChatMessage msg) throws RequestException {
        msg.setDatePosted(DateTime.now());
        String msg_native = msg.getMessage();
        String msg_en = new TranslateController().translate(msg_native);
        if (msg_en != null) {
            msg.setEnglishMessage(msg_en);
        }

        if (isInPenaltyBox(msg)) {
            return;
        }

        String sql = "INSERT INTO chat_messages (empire_id, alliance_id, message, message_en, posted_date, conversation_id, action)" +
                    " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (SqlStmt stmt = DB.prepare(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (msg.getEmpireKey() != null) {
                stmt.setInt(1, msg.getEmpireID());
                if (msg.getAllianceKey() != null) {
                    stmt.setInt(2, msg.getAllianceID());
                } else {
                    stmt.setNull(2);
                }
            } else {
                stmt.setNull(1);
                stmt.setNull(2);
            }

            stmt.setString(3, msg.getMessage());
            stmt.setString(4, msg.getEnglishMessage());

            stmt.setDateTime(5, msg.getDatePosted());

            if (msg.getConversationID() != null && msg.getConversationID() > 0) {
                stmt.setInt(6, msg.getConversationID());
            } else {
                stmt.setNull(6);
            }

            if (msg.getAction() == null || msg.getAction() == ChatMessage.MessageAction.Normal) {
                stmt.setNull(7);
            } else {
                stmt.setInt(7, msg.getAction().getValue());
            }

            stmt.update();
            msg.setID(stmt.getAutoGeneratedID());
        } catch(Exception e) {
            throw new RequestException(e);
        }

        // escape the HTML before sending the notification out
        Messages.ChatMessage.Builder chat_msg_pb = Messages.ChatMessage.newBuilder();
        msg.toProtocolBuffer(chat_msg_pb, true);

        String encoded = getEncodedMessage(msg);
        if (chat_msg_pb.hasConversationId()) {
            new NotificationController().sendNotificationToConversation(
                    chat_msg_pb.getConversationId(), "chat", encoded);
        } else if (chat_msg_pb.hasAllianceKey()) {
            new NotificationController().sendNotificationToOnlineAlliance(
                    Integer.parseInt(chat_msg_pb.getAllianceKey()), "chat", encoded);
        } else {
            new NotificationController().sendNotificationToAllOnline(
                    "chat", encoded);
        }
    }

    /**
     * Determines if the given empire is sinbinned. If they are, we send them a notification back as if their
     * message was successfully sent, otherwise the message is just dropped.
     */
    private boolean isInPenaltyBox(ChatMessage msg) throws RequestException {
        if (new ChatAbuseController().isInPenaltyBox(msg.getEmpireID())) {
            // send them a fake notification so they can't be quite sure if they're still banned or not.
            // give the msg a fake ID so that the client doesn't de-dupe it
            msg.setID(- new Random().nextInt());
            new NotificationController().sendNotificationToEmpire(msg.getEmpireID(), "chat", getEncodedMessage(msg));

            return true;
        } else {
            return false;
        }
    }

    /** Encodes the given message, ready to be used in a notification. */
    private String getEncodedMessage(ChatMessage msg) {
        Messages.ChatMessage.Builder chat_msg_pb = Messages.ChatMessage.newBuilder();
        msg.toProtocolBuffer(chat_msg_pb, true);

        return Base64.encodeBase64String(chat_msg_pb.build().toByteArray());
    }

    /**
     * Search for an existing conversation between the two given empires. An existing conversation is one
     * where the last message was sent within the last week and only the given two empires are participants.
     */
    public ChatConversation findExistingConversation(int empireID1, int empireID2) throws RequestException {
        // make sure empireID1 is the larger, since that's what our SQL query assumes
        if (empireID1 < empireID2) {
            int tmp = empireID1;
            empireID1 = empireID2;
            empireID2 = tmp;
        }

        try {
            return db.findExistingConversation(empireID1, empireID2);
        } catch (Exception e) {
            throw new RequestException(e);
        }
    }

    public ChatConversation createConversation(int empireID1, int empireID2) throws RequestException {
        ChatConversation conversation = findExistingConversation(empireID1, empireID2);
        if (conversation == null) {
            try {
                log.info(String.format("Creating new conversation between %1d and %2d", empireID1, empireID2));
                conversation = db.createConversation(empireID1, empireID2);
            } catch (Exception e) {
                throw new RequestException(e);
            }
        }
        return conversation;
    }

    private static class DataBase extends BaseDataBase {
        public DataBase() {
            super();
        }
        public DataBase(Transaction trans) {
            super(trans);
        }

        public ChatConversation findExistingConversation(int empireID1, int empireID2) throws Exception {
            Integer chatID = null;
            String sql = "SELECT chat_conversations.id, MAX(empire_id) AS empire_id_1, MIN(empire_id) AS empire_id_2, COUNT(*) AS num_empires" +
                    " FROM chat_conversations" +
                    " INNER JOIN chat_conversation_participants ON conversation_id = chat_conversations.id" +
                    " GROUP BY chat_conversations.id" +
                    " HAVING COUNT(*) = 2" +
                       " AND MAX(empire_id) = ?" +
                       " AND MIN(empire_id) = ?";
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, empireID1);
                stmt.setInt(2, empireID2);
                ResultSet rs = stmt.select();
                if (rs.next()) {
                    chatID = rs.getInt(1);
                }
            }

            if (chatID == null) {
                return null;
            }

            ArrayList<ChatConversation> conversations = getConversations("chat_conversations.id = "+chatID);
            return conversations.get(0);
        }

        public ArrayList<ChatConversation> getConversationsForEmpire(int empireID) throws Exception {
            String whereClause = "chat_conversations.id IN (" +
              "SELECT conversation_id FROM chat_conversation_participants WHERE empire_id = " + empireID +")";
            return getConversations(whereClause);
        }

        public ArrayList<ChatConversation> getConversations(String whereClause) throws Exception {
            Map<Integer, ChatConversation> conversations = new HashMap<Integer, ChatConversation>();
            String sql = "SELECT chat_conversations.id, chat_conversation_participants.empire_id, chat_conversation_participants.is_muted" +
                        " FROM chat_conversations" +
                        " INNER JOIN chat_conversation_participants ON conversation_id = chat_conversations.id" +
                        " WHERE " + whereClause;
            try (SqlStmt stmt = prepare(sql)) {
                ResultSet rs = stmt.select();
                while (rs.next()) {
                    int conversationID = rs.getInt(1);
                    ChatConversation conversation = conversations.get(conversationID);
                    if (conversation == null) {
                        conversation = new ChatConversation(conversationID);
                        conversations.put(conversationID, conversation);
                    }
                    conversation.addParticipant(rs.getInt(2), rs.getBoolean(3));
                }
            }
            return new ArrayList<ChatConversation>(conversations.values());
        }

        public ChatConversation getConversation(int id) throws Exception {
            ArrayList<ChatConversation> conversations = getConversations("chat_conversations.id = "+id);
            if (conversations.size() == 1) {
                return conversations.get(0);
            }
            return null;
        }

        public void addParticipant(int conversationID, int empireID) throws Exception {
            String sql = "INSERT INTO chat_conversation_participants (conversation_id, empire_id, is_muted) VALUES" +
                            " (?, ?, 0)";
               try (SqlStmt stmt = prepare(sql)) {
                   stmt.setInt(1, conversationID);
                   stmt.setInt(2, empireID);
                   stmt.update();
               }
        }

        public void removeParticipant(int conversationID, int empireID) throws Exception {
            String sql = "DELETE FROM chat_conversation_participants WHERE conversation_id = ? AND empire_id = ?";
               try (SqlStmt stmt = prepare(sql)) {
                   stmt.setInt(1, conversationID);
                   stmt.setInt(2, empireID);
                   stmt.update();
               }
        }

        public ChatConversation createConversation(int empireID1, int empireID2) throws Exception {
            ChatConversation conversation;

            String sql = "INSERT INTO chat_conversations () VALUES ()";
            try (SqlStmt stmt = prepare(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.update();
                conversation = new ChatConversation(stmt.getAutoGeneratedID());
            }

            sql = "INSERT INTO chat_conversation_participants (conversation_id, empire_id, is_muted) VALUES" +
                 " (?, ?, 0)";
            if (empireID1 != empireID2) {
                sql += ", (?, ?, 0)";
            }
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setInt(1, conversation.getID());
                stmt.setInt(2, empireID1);
                if (empireID1 != empireID2) {
                    stmt.setInt(3, conversation.getID());
                    stmt.setInt(4, empireID2);
                }
                stmt.update();
            }

            conversation.addParticipant(empireID1, false);
            if (empireID1 != empireID2) {
                conversation.addParticipant(empireID2, false);
            }
            return conversation;
        }
    }
}
