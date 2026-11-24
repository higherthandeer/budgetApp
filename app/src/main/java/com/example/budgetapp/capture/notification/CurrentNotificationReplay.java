package com.example.budgetapp.capture.notification;

import com.example.budgetapp.data.*;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.processing.TransactionProcessor;
import java.util.*;

/** Explicit user retry of current notifications, with the original event identity and date. */
public final class CurrentNotificationReplay {
    public enum State { IDLE, RUNNING, COMPLETE, NO_PERMISSION, NOT_CONNECTED, FAILED }
    public static final class Report {
        public final State state;
        public int seen,posted,linked,review,ignored,unchanged,errors;
        public Report(State state){this.state=state;}
    }
    private final LedgerDatabase db;
    private final EventIntake intake;
    private final TransactionProcessor processor;
    private final NotificationParser parser;
    private final RawRetention retention;
    public CurrentNotificationReplay(LedgerDatabase db,EventIntake intake,TransactionProcessor processor,NotificationParser parser,RawRetention retention){this.db=db;this.intake=intake;this.processor=processor;this.parser=parser;this.retention=retention;}

    public Report process(List<NotificationSnapshot> snapshots){
        Report report=new Report(State.COMPLETE);
        for(NotificationSnapshot snapshot:snapshots){
            report.seen++;
            try {
                String outcome=db.runInTransaction(()->{
                    var event=intake.store(snapshot);
                    if(event==null)return "IGNORED";
                    if("REVIEW".equals(event.processState)){
                        boolean retry=event.transactionId==null&&event.parsedJson==null&&event.reviewResolution==null&&event.correctedCandidateJson==null
                                &&event.rawPayload!=null&&!retention.isExpired(event)
                                &&Arrays.asList("INVALID_OR_CONFLICTING_FACTS","MISSING_FACTS","UNVERIFIED_OR_INCOMPLETE","AMBIGUOUS_TEMPLATE").contains(event.errorCode);
                        if(!retry)return "REVIEW";
                        NotificationSnapshot originalDay=new NotificationSnapshot(snapshot.packageName,snapshot.notificationKey,snapshot.postTimeMs,event.capturedAtMs,snapshot.channelId,snapshot.title,snapshot.text,snapshot.bigText,snapshot.textLines,snapshot.groupSummary,snapshot.truncated,snapshot.appVersion);
                        event.candidateJson=EventCodec.encodeParsed(parser.parse(originalDay));
                        event.processState="NEW";event.errorCode=null;event.reviewReasonCodesJson=null;event.reviewCandidatesJson=null;
                        db.sourceEventDao().update(event);
                    }
                    return processor.process(event.id).status.name();
                });
                switch(outcome){
                    case "POSTED":report.posted++;break;
                    case "LINKED":report.linked++;break;
                    case "REVIEW":report.review++;break;
                    case "IGNORED":report.ignored++;break;
                    default:report.unchanged++;
                }
            }catch(RuntimeException failure){report.errors++;}
        }
        return report;
    }
}
