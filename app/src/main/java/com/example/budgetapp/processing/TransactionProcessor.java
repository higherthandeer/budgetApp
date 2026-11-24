package com.example.budgetapp.processing;
import com.example.budgetapp.data.*;
import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.parsing.ParsedTransaction.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import java.time.*;
import java.util.*;
import org.json.*;

public final class TransactionProcessor {
 public static final class ProcessResult {
  public enum Status { POSTED,LINKED,REVIEW,IGNORED,ALREADY_HANDLED }
  public final Status status;public final String transactionId,reviewEventId,reasonCode;
  ProcessResult(Status status,String tx,String review,String reason){this.status=status;transactionId=tx;reviewEventId=review;reasonCode=reason;}
 }
 private final LedgerDatabase db;private final NotificationParser parser;private final CategoryMatcher matcher;private final Clock clock;private final RawRetention retention;
 public TransactionProcessor(LedgerDatabase db,NotificationParser parser,CategoryMatcher matcher,Clock clock){this(db,parser,matcher,clock,new RawRetention(db,clock));}
 public TransactionProcessor(LedgerDatabase db,NotificationParser parser,CategoryMatcher matcher,Clock clock,RawRetention retention){this.db=db;this.parser=parser;this.matcher=matcher;this.clock=clock;this.retention=retention;}
 public ProcessResult process(String eventId){return db.runInTransaction(()->{
  SourceEventEntity e=db.sourceEventDao().find(eventId);if(e==null)throw new IllegalArgumentException("Missing event");
  if(Arrays.asList("DONE","IGNORED","REVIEW").contains(e.processState))return new ProcessResult(ProcessResult.Status.ALREADY_HANDLED,e.transactionId,"REVIEW".equals(e.processState)?e.id:null,e.errorCode);
  if(!"NOTIFICATION".equals(e.sourceType))return review(e,"MANUAL_REQUIRES_USER",Collections.emptyList());
  ParseResult parsed;
  try{if(e.candidateJson!=null)parsed=EventCodec.decodeParsed(e.candidateJson);else if(retention.isExpired(e))return review(e,"RAW_EXPIRED",Collections.emptyList());else if(e.rawPayload!=null)parsed=parser.parse(EventCodec.decodeSnapshot(retention.readRaw(e)));else return review(e,"RAW_UNAVAILABLE",Collections.emptyList());}catch(IllegalArgumentException invalid){return review(e,"INVALID_PAYLOAD",Collections.emptyList());}
  e.candidateJson=EventCodec.encodeParsed(parsed);e.parserId=parsed.parserId;e.parserVersion=Integer.toString(parsed.version);
  if(parsed.disposition==ParseResult.Disposition.IGNORED){e.processState="IGNORED";e.errorCode=parsed.reasonCode;db.sourceEventDao().update(e);return new ProcessResult(ProcessResult.Status.IGNORED,null,null,parsed.reasonCode);}
  ParsedTransaction p=parsed.candidate;
  if(parsed.disposition!=ParseResult.Disposition.CANDIDATE||!complete(p)||e.accountScopeId.isEmpty()||!Arrays.asList("WECHAT","ALIPAY","OTHER").contains(e.provider))return review(e,parsed.reasonCode==null?"MISSING_FACTS":parsed.reasonCode,Collections.emptyList());
  Set<String> exact=new LinkedHashSet<>();for(Identity id:p.identities){if(id.idKind==null||id.idKind.isEmpty()||id.namespace==null||id.namespace.isEmpty()||id.externalId==null||id.externalId.isEmpty())return review(e,"INVALID_IDENTITY",Collections.emptyList());var identity=db.ledgerDao().identity(e.provider,e.accountScopeId,id.idKind,id.namespace,id.externalId);if(identity!=null)exact.add(identity.transactionId);}
  if(exact.size()>1)return review(e,"IDENTITIES_DISAGREE",new ArrayList<>(exact));
  if(exact.size()==1){String id=exact.iterator().next();LedgerTransactionEntity tx=db.ledgerDao().find(id);if(!sameAcceptedFact(db,tx,p))return review(e,"IDENTITY_FACT_CONFLICT",Collections.singletonList(id));addIdentities(tx.id,e,p);link(e,tx.id,"EXACT_IDENTITY",parsed);return new ProcessResult(ProcessResult.Status.LINKED,tx.id,null,null);}
  List<String> similar=similarTransactions(db,p,e.provider,e.accountScopeId);
  if(!similar.isEmpty())return review(e,"POSSIBLE_DUPLICATE",similar);
  LedgerTransactionEntity tx=new LedgerTransactionEntity();tx.id=UUID.randomUUID().toString();tx.kind=p.kind.name();tx.amountMinor=p.amountMinor;tx.currency="CNY";tx.occurredAtMs=p.occurredAtMs;tx.timePrecision=p.timePrecision.name();tx.sourceZoneId="Asia/Shanghai";tx.merchantRaw=p.merchant==null?"":p.merchant;tx.merchantNormalized=CategoryMatcher.normalize(p.merchant);tx.provider=e.provider;tx.accountScopeId=e.accountScopeId;tx.payMethodRaw=p.payMethod;var category=matcher.match(p.kind,p.merchant,e.provider,e.accountScopeId,db.categoryDao().ruleRows());tx.categoryId=category.categoryId;tx.categoryOrigin=category.origin.name();tx.appliedRuleId=category.ruleId;tx.bookingState="POSTED";tx.userLockedFields="[]";tx.note=p.dateBasis==DateBasis.NOTIFICATION_RECEIVED_DAY?"日期按通知接收日推定":p.dateBasis==DateBasis.NOTIFICATION_CAPTURE_TIME?"时间按通知接收时间记录":"";tx.revision=1;tx.createdAt=clock.millis();tx.updatedAt=tx.createdAt;db.ledgerDao().insert(tx);addIdentities(tx.id,e,p);link(e,tx.id,"NEW_TRANSACTION",parsed);return new ProcessResult(ProcessResult.Status.POSTED,tx.id,null,null);
 });}
 public static List<String> similarTransactions(LedgerDatabase db,ParsedTransaction p,String provider,String scope){List<String> ids=new ArrayList<>();for(LedgerTransactionEntity tx:db.ledgerDao().inScope(provider,scope)){if(!distinctIdentity(db,tx,p)&&sameAcceptedFact(db,tx,p))ids.add(tx.id);}return ids;}
 private boolean complete(ParsedTransaction p){return p!=null&&p.kind!=null&&p.amountMinor!=null&&p.amountMinor>0&&"CNY".equals(p.currency)&&p.occurredAtMs!=null&&p.timePrecision!=null&&p.tradeStatus==TradeStatus.SUCCESS&&p.proofs.containsAll(EnumSet.allOf(Proof.class));}
 private static boolean distinctIdentity(LedgerDatabase db,LedgerTransactionEntity tx,ParsedTransaction p){List<TransactionIdentityEntity> existing=db.ledgerDao().identities(tx.id);for(Identity incoming:p.identities)for(var old:existing)if(old.idKind.equals(incoming.idKind)&&old.idNamespace.equals(incoming.namespace)&&!old.externalId.equals(incoming.externalId))return true;return false;}
 private static boolean sameAcceptedFact(LedgerDatabase db,LedgerTransactionEntity tx,ParsedTransaction p){
  if(matches(p,tx.kind,tx.amountMinor,tx.occurredAtMs,tx.timePrecision,tx.merchantRaw))return true;
  if(matchesJson(p,tx.recentPreviousValuesJson))return true;
  for(SourceEventEntity evidence:db.sourceEventDao().evidence(tx.id)){if(evidence.parsedJson==null)continue;try{ParsedTransaction accepted=EventCodec.decodeParsed(evidence.parsedJson).candidate;if(accepted!=null&&matches(p,accepted.kind==null?null:accepted.kind.name(),accepted.amountMinor,accepted.occurredAtMs,accepted.timePrecision==null?null:accepted.timePrecision.name(),accepted.merchant))return true;}catch(IllegalArgumentException oldFormat){if(matchesJson(p,evidence.parsedJson))return true;}}
  return false;
 }
 private static boolean matchesJson(ParsedTransaction p,String text){if(text==null)return false;try{JSONObject o=new JSONObject(text);return matches(p,o.getString("kind"),o.getLong("amountMinor"),o.getLong("occurredAtMs"),o.optString("timePrecision","DAY"),o.optString("merchant",""));}catch(JSONException e){return false;}}
 private static boolean matches(ParsedTransaction p,String kind,Long amount,Long at,String precision,String merchant){if(!p.kind.name().equals(kind)||!p.amountMinor.equals(amount)||at==null)return false;String a=CategoryMatcher.normalize(p.merchant),b=CategoryMatcher.normalize(merchant);if(!a.isEmpty()&&!b.isEmpty()&&!a.equals(b))return false;if(p.timePrecision==TimePrecision.DAY||"DAY".equals(precision))return Instant.ofEpochMilli(p.occurredAtMs).atZone(TimeWindows.LEDGER_ZONE).toLocalDate().equals(Instant.ofEpochMilli(at).atZone(TimeWindows.LEDGER_ZONE).toLocalDate());try{long delta=Math.subtractExact(p.occurredAtMs,at);return delta>=-120000L&&delta<=120000L;}catch(ArithmeticException overflow){return false;}}
 private void addIdentities(String txId,SourceEventEntity e,ParsedTransaction p){for(Identity id:p.identities){var old=db.ledgerDao().identity(e.provider,e.accountScopeId,id.idKind,id.namespace,id.externalId);if(old!=null){if(!old.transactionId.equals(txId))throw new IllegalStateException("Identity conflict");continue;}TransactionIdentityEntity row=new TransactionIdentityEntity();row.id=UUID.randomUUID().toString();row.transactionId=txId;row.provider=e.provider;row.accountScopeId=e.accountScopeId;row.idKind=id.idKind;row.idNamespace=id.namespace;row.externalId=id.externalId;db.ledgerDao().insertIdentity(row);}}
 private void link(SourceEventEntity e,String txId,String method,ParseResult parsed){e.transactionId=txId;e.matchMethod=method;e.linkedAt=clock.millis();if(e.parsedJson==null)e.parsedJson=EventCodec.encodeParsed(parsed);e.processState="DONE";e.errorCode=null;db.sourceEventDao().update(e);}
 private ProcessResult review(SourceEventEntity e,String reason,List<String> candidates){e.processState="REVIEW";e.errorCode=reason;e.reviewReasonCodesJson=new JSONArray(Collections.singletonList(reason)).toString();e.reviewCandidatesJson=new JSONArray(candidates).toString();db.sourceEventDao().update(e);return new ProcessResult(ProcessResult.Status.REVIEW,e.transactionId,e.id,reason);}
}
