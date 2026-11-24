package com.example.budgetapp.capture.notification;

import com.example.budgetapp.data.*;
import com.example.budgetapp.data.entity.SourceEventEntity;
import com.example.budgetapp.parsing.NotificationParser;
import com.example.budgetapp.worker.RecoveryScheduler;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

public final class EventIntake {
    private final LedgerDatabase db;
    private final NotificationParser parser;
    private final LocalSettings settings;
    private final RecoveryScheduler scheduler;
    private final ExecutorService io;

    public EventIntake(LedgerDatabase db, NotificationParser parser, LocalSettings settings, RecoveryScheduler scheduler, ExecutorService io) {
        this.db = db;
        this.parser = parser;
        this.settings = settings;
        this.scheduler = scheduler;
        this.io = io;
    }

    public void accept(NotificationSnapshot snapshot) {
        trace("QUEUED paymentTitle="+"微信支付".equals(snapshot.title)+" channel="+"message_channel_new_id".equals(snapshot.channelId)+" compact="+snapshot.text.startsWith("已支付"));
        io.execute(() -> {
            SourceEventEntity stored=store(snapshot);
            if (stored != null && (stored.processState.equals("NEW") || stored.processState.equals("ERROR")))
                scheduler.schedule();
        });
    }

    /** Synchronous: caller supplies IO execution and, when needed, an outer transaction. */
    public SourceEventEntity store(NotificationSnapshot snapshot) {
            NotificationParser.IntakeDecision decision=parser.classify(snapshot);
            trace("CLASSIFIED "+decision);
            if (decision == NotificationParser.IntakeDecision.DROP) return null;
            String provider = "com.tencent.mm".equals(snapshot.packageName) ? "WECHAT" : "ALIPAY";
            String scope = settings.accountScope(provider);
            String normalized = String.join("\n", snapshot.title, snapshot.text, snapshot.bigText, String.join("\n", snapshot.textLines)).replace("\r\n", "\n").trim();
            String hash = hash(normalized + "\n" + snapshot.channelId + "\n" + snapshot.appVersion + "\n" + snapshot.groupSummary + "\n" + snapshot.truncated);
            String key = "v1:" + hash("NOTIFICATION\n" + provider + "\n" + scope + "\n" + snapshot.notificationKey + "\n" + snapshot.postTimeMs + "\n" + hash);
            SourceEventEntity e = new SourceEventEntity();
            e.id = UUID.randomUUID().toString();
            e.sourceType = "NOTIFICATION";
            e.provider = provider;
            e.accountScopeId = scope;
            e.packageName = snapshot.packageName;
            e.appVersion = snapshot.appVersion;
            e.capturedAtMs = snapshot.capturedAtMs;
            e.rawExpiresAtMs = RawRetention.expiryAt(snapshot.capturedAtMs, settings.retentionDays());
            e.rawPayload = EventCodec.encodeSnapshot(snapshot);
            e.payloadHash = hash;
            e.eventKey = key;
            e.processState = "NEW";
            db.sourceEventDao().insertIgnore(e);
            SourceEventEntity stored = db.sourceEventDao().findByKey("NOTIFICATION", key);
            trace("STORED "+(stored==null?"MISSING":stored.processState));
            return stored;
    }

    private static void trace(String stage){if(com.example.budgetapp.BuildConfig.DEBUG)android.util.Log.d("BudgetIntake",stage);}

    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            for (byte b : bytes) text.append(String.format(Locale.ROOT, "%02x", b & 255));
            return text.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
