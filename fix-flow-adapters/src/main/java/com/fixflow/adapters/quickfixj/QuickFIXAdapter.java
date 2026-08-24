package com.fixflow.adapters.quickfixj;

import com.fixflow.core.domain.execution.FIXMessageData;
import com.fixflow.core.domain.session.FIXMode;
import com.fixflow.core.domain.session.FIXSessionConfig;
import com.fixflow.core.ports.outbound.FIXSessionPort;
import com.fixflow.core.ports.outbound.InboundMessageListener;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.Message;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QuickFIXAdapter implements FIXSessionPort {

    private final QuickFIXApplicationAdapter application;
    private final Map<UUID, Connector> connectors = new ConcurrentHashMap<>();
    private final Map<UUID, SessionID> sessions = new ConcurrentHashMap<>();

    public QuickFIXAdapter(QuickFIXApplicationAdapter application) {
        this.application = application;
    }

    @Override
    public void setInboundListener(InboundMessageListener listener) {
        application.setInboundListener(listener);
    }

    @PreDestroy
    void shutdown() {
        for (Connector c : connectors.values()) {
            try { c.stop(true); } catch (Exception ignored) {}
        }
        sessions.values().forEach(application::unregisterSession);
        connectors.clear();
        sessions.clear();
    }

    @Override
    public void connect(FIXSessionConfig config) {
        // Stop and remove any existing connector for this session before creating a new one.
        // Without this, reconnect orphans the old running connector and creates a duplicate QFJ session.
        Connector existing = connectors.remove(config.id());
        SessionID oldSid = sessions.remove(config.id());
        if (oldSid != null) application.unregisterSession(oldSid);
        if (existing != null) {
            try { existing.stop(true); } catch (Exception ignored) {}
        }

        try {
            SessionSettings settings = buildSettings(config);
            MessageStoreFactory storeFactory = new MemoryStoreFactory();
            LogFactory logFactory = new ScreenLogFactory(settings);
            MessageFactory messageFactory = new DefaultMessageFactory();

            Connector connector = (config.mode() == FIXMode.INITIATOR)
                    ? new SocketInitiator(application, storeFactory, settings, logFactory, messageFactory)
                    : new SocketAcceptor(application, storeFactory, settings, logFactory, messageFactory);

            // Register BEFORE start so onLogon callback finds the UUID mapping.
            SessionID sid = sessionIdFromConfig(config);
            application.registerSession(sid, config.id());
            try {
                connector.start();
            } catch (RuntimeException | ConfigError e) {
                // start() failed: undo the pre-start registration and stop the half-started connector.
                application.unregisterSession(sid);
                try { connector.stop(true); } catch (Exception ignored) {}
                throw e;
            }
            connectors.put(config.id(), connector);
            sessions.put(config.id(), sid);
        } catch (ConfigError e) {
            throw new IllegalStateException("Failed to start FIX session " + config.id(), e);
        }
    }

    @Override
    public void disconnect(UUID sessionId) {
        Connector c = connectors.remove(sessionId);
        SessionID sid = sessions.remove(sessionId);
        try {
            if (c != null) c.stop(true);
        } catch (Exception ignored) {}
        if (sid != null) application.unregisterSession(sid);
    }

    @Override
    public boolean isConnected(UUID sessionId) {
        SessionID sid = sessions.get(sessionId);
        if (sid == null) return false;
        Session s = Session.lookupSession(sid);
        return s != null && s.isLoggedOn();
    }

    @Override
    public void sendMessage(UUID sessionId, FIXMessageData message) {
        SessionID sid = sessions.get(sessionId);
        if (sid == null) throw new IllegalStateException("Unknown session: " + sessionId);
        try {
            Session.sendToTarget(buildMessage(message, applicationDictionary(sid)), sid);
        } catch (SessionNotFound e) {
            throw new IllegalStateException("Session not found: " + sessionId, e);
        }
    }

    /**
     * The application {@link DataDictionary} for a live session, or {@code null} if the session
     * can't be resolved. Read fresh from QuickFIX/J's own session/provider on every call rather
     * than cached on the adapter, so there is nothing to keep in sync by hand.
     */
    private static DataDictionary applicationDictionary(SessionID sid) {
        Session session = Session.lookupSession(sid);
        if (session == null) return null;
        DataDictionaryProvider provider = session.getDataDictionaryProvider();
        if (provider == null) return null;
        if (sid.isFIXT()) {
            var applVerID = session.getTargetDefaultApplicationVersionID();
            return applVerID == null ? null : provider.getApplicationDataDictionary(applVerID);
        }
        return provider.getSessionDataDictionary(sid.getBeginString());
    }

    /**
     * Builds a QuickFIX/J message from engine data with no {@link DataDictionary} available.
     * Package-visible so unit tests can build and inspect messages without a live session; the
     * repeating-group entries fall back to ascending-tag order (see {@link #buildMessage(FIXMessageData, DataDictionary)}).
     */
    static Message buildMessage(FIXMessageData data) {
        return buildMessage(data, null);
    }

    /**
     * Builds a QuickFIX/J message from engine data. Tag 35 goes to the header. Every repeating
     * group entry becomes a {@link Group}; when {@code dictionary} is available its dictionary
     * order is used, since the FIX wire format requires an entry's fields in dictionary-defined
     * order, not ascending-tag order, and QuickFIX/J's receiving parser ends an entry at the
     * first field whose group-order index is not increasing. Authors still write the delimiter
     * field first in an entry as the documented convention, and that first field is the
     * fallback delimiter when no dictionary is available; when a dictionary is available it
     * supersedes the convention for both the delimiter and the full field order.
     */
    static Message buildMessage(FIXMessageData data, DataDictionary dictionary) {
        Message msg = new Message();
        data.fields().forEach((tag, value) -> {
            if (tag == 35) msg.getHeader().setString(35, value);
            else msg.setString(tag, value);
        });
        String msgType = data.fields().get(35);
        applyGroups(msg, data, dictionary, msgType);
        return msg;
    }

    private static void applyGroups(FieldMap target, FIXMessageData data, DataDictionary dictionary, String msgType) {
        data.groups().forEach((counterTag, entries) -> {
            DataDictionary.GroupInfo groupInfo = groupInfo(dictionary, msgType, counterTag);
            for (FIXMessageData entry : entries) {
                if (entry.fields().isEmpty()) continue;
                Group group = groupInfo != null
                        ? new Group(counterTag, groupInfo.getDelimiterField(), groupInfo.getDataDictionary().getOrderedFields())
                        : new Group(counterTag, entry.fields().keySet().iterator().next());
                entry.fields().forEach(group::setString);
                DataDictionary nestedDictionary = groupInfo != null ? groupInfo.getDataDictionary() : null;
                applyGroups(group, entry, nestedDictionary, msgType);
                target.addGroup(group);
            }
        });
    }

    /** {@code null} when there is no dictionary, no message type, or the dictionary doesn't define this group. */
    private static DataDictionary.GroupInfo groupInfo(DataDictionary dictionary, String msgType, int counterTag) {
        if (dictionary == null || msgType == null || !dictionary.isGroup(msgType, counterTag)) return null;
        return dictionary.getGroup(msgType, counterTag);
    }

    private SessionSettings buildSettings(FIXSessionConfig cfg) throws ConfigError {
        SessionSettings settings = new SessionSettings();

        settings.setString("ConnectionType", cfg.mode() == FIXMode.INITIATOR ? "initiator" : "acceptor");
        settings.setLong("HeartBtInt", cfg.heartbeatInterval());
        settings.setLong("ReconnectInterval", 0);
        settings.setString("StartTime", "00:00:00");
        settings.setString("EndTime", "00:00:00");
        settings.setString("ResetOnLogon", cfg.resetOnLogon() ? "Y" : "N");
        settings.setString("ResetOnLogout", cfg.resetOnLogout() ? "Y" : "N");
        settings.setString("ValidateUserDefinedFields", "N");
        settings.setString("ValidateIncomingMessage", "N");

        if (cfg.mode() == FIXMode.INITIATOR) {
            settings.setString("SocketConnectHost", cfg.host());
            settings.setLong("SocketConnectPort", cfg.port());
        } else {
            settings.setLong("SocketAcceptPort", cfg.port());
        }

        String beginString = switch (cfg.fixVersion()) {
            case FIX_42 -> "FIX.4.2";
            case FIX_44 -> "FIX.4.4";
            case FIXT_11 -> "FIXT.1.1";
        };

        SessionID sid = sessionIdFromConfig(cfg);
        settings.setString(sid, "BeginString", beginString);
        settings.setString(sid, "SenderCompID", cfg.senderCompID());
        settings.setString(sid, "TargetCompID", cfg.targetCompID());

        if (cfg.fixVersion() == com.fixflow.core.domain.session.FIXVersion.FIXT_11) {
            settings.setString(sid, "DefaultApplVerID",
                    cfg.defaultApplVerID() == null ? "9" : cfg.defaultApplVerID());
            settings.setString(sid, "AppDataDictionary", "FIX50SP2.xml");
            settings.setString(sid, "TransportDataDictionary", "FIXT11.xml");
        } else {
            String dict = switch (cfg.fixVersion()) {
                case FIX_42 -> "FIX42.xml";
                case FIX_44 -> "FIX44.xml";
                default -> "FIX44.xml";
            };
            settings.setString(sid, "DataDictionary", dict);
        }
        return settings;
    }

    private SessionID sessionIdFromConfig(FIXSessionConfig cfg) {
        String beginString = switch (cfg.fixVersion()) {
            case FIX_42 -> "FIX.4.2";
            case FIX_44 -> "FIX.4.4";
            case FIXT_11 -> "FIXT.1.1";
        };
        return new SessionID(beginString, cfg.senderCompID(), cfg.targetCompID());
    }
}
