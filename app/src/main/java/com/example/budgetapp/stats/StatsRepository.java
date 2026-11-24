package com.example.budgetapp.stats;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.room.InvalidationTracker;
import com.example.budgetapp.data.*;
import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.processing.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public final class StatsRepository {
 public static final class StatisticsException extends IllegalStateException {public StatisticsException(Throwable cause){super("Statistics unavailable",cause);}}
 public static final class CategoryAmount {
  public final String categoryId,name;public final long amountFen;public final BigDecimal sharePercent;
  public CategoryAmount(String categoryId,String name,long amountFen,BigDecimal nullableSharePercent){this.categoryId=categoryId;this.name=name;this.amountFen=amountFen;sharePercent=nullableSharePercent;}
 }
 public static final class Entry {public final LedgerTransactionEntity transaction;public final long contributionFen;public final String categoryName,categoryIconKey;public Entry(LedgerTransactionEntity transaction,long contributionFen){this(transaction,contributionFen,"");}public Entry(LedgerTransactionEntity transaction,long contributionFen,String categoryName){this(transaction,contributionFen,categoryName,CategoryIconCatalog.defaultForName(categoryName));}public Entry(LedgerTransactionEntity transaction,long contributionFen,String categoryName,String iconKey){this.transaction=transaction;this.contributionFen=contributionFen;this.categoryName=categoryName;this.categoryIconKey=iconKey;}}
 public static final class Snapshot {
  public final LedgerFilter filter;public final long expenseFen,incomeFen,netOutflowFen,valueFen;public final List<CategoryAmount> categories;public final List<Entry> entries;public final boolean valid;
  public Snapshot(LedgerFilter filter,long expenseFen,long incomeFen,List<CategoryAmount> categories,List<Entry> entries){this(filter,expenseFen,incomeFen,categories,entries,true);}
  private Snapshot(LedgerFilter filter,long expenseFen,long incomeFen,List<CategoryAmount> categories,List<Entry> entries,boolean valid){this.filter=filter;this.expenseFen=expenseFen;this.incomeFen=incomeFen;netOutflowFen=Math.subtractExact(expenseFen,incomeFen);valueFen=filter.metric==LedgerFilter.Metric.EXPENSE?expenseFen:filter.metric==LedgerFilter.Metric.INCOME?incomeFen:netOutflowFen;this.categories=Collections.unmodifiableList(new ArrayList<>(categories));this.entries=Collections.unmodifiableList(new ArrayList<>(entries));this.valid=valid;}
 }
 private final LedgerDatabase db;private final Executor executor;
 public StatsRepository(LedgerDatabase db,Executor executor){this.db=db;this.executor=executor;}
 public Snapshot load(LedgerFilter filter){try{return db.runInTransaction(()->{
  var totals=db.statsDao().totals(filter.startMs,filter.endMs,filter.categoryId,filter.provider);List<CategoryAmount> categories=new ArrayList<>();for(var row:db.statsDao().expenseCategories(filter.startMs,filter.endMs,filter.categoryId,filter.provider)){BigDecimal percent=totals.expenseFen==0?null:BigDecimal.valueOf(row.amountFen).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totals.expenseFen),2,RoundingMode.HALF_UP);categories.add(new CategoryAmount(row.categoryId,row.name,row.amountFen,percent));}
  Map<String,String> categoryNames=new HashMap<>(),categoryIcons=new HashMap<>();for(var category:db.categoryDao().all()){categoryNames.put(category.id,category.name);categoryIcons.put(category.id,category.iconKey);}List<Entry> entries=new ArrayList<>();for(var tx:db.ledgerDao().listPosted(filter.startMs,filter.endMs,filter.metric==LedgerFilter.Metric.NET_OUTFLOW?null:filter.metric.name(),filter.categoryId,filter.provider))entries.add(new Entry(tx,filter.metric==LedgerFilter.Metric.NET_OUTFLOW&&"INCOME".equals(tx.kind)?Math.negateExact(tx.amountMinor):tx.amountMinor,categoryNames.getOrDefault(tx.categoryId,"未分类"),categoryIcons.getOrDefault(tx.categoryId,"category")));return new Snapshot(filter,totals.expenseFen,totals.incomeFen,categories,entries);
 });}catch(RuntimeException failure){throw failure instanceof StatisticsException?(StatisticsException)failure:new StatisticsException(failure);}}
 public SpendingComparison.Result compare(LedgerFilter scope,ComparisonWindows.Pair pair){return db.runInTransaction(()->{LedgerFilter current=new LedgerFilter(pair.current.startMs,pair.current.endMs,LedgerFilter.Metric.EXPENSE,scope.categoryId,scope.provider);LedgerFilter previous=new LedgerFilter(pair.previous.startMs,pair.previous.endMs,LedgerFilter.Metric.EXPENSE,scope.categoryId,scope.provider);return SpendingComparison.calculate(load(current),load(previous));});}
 public static final class PeriodComparison {public final SpendingComparison.Result spending;public final long incomeDeltaFen,netDeltaFen;PeriodComparison(SpendingComparison.Result spending,long incomeDelta,long netDelta){this.spending=spending;incomeDeltaFen=incomeDelta;netDeltaFen=netDelta;}}
 public PeriodComparison periodComparison(LedgerFilter scope,ComparisonWindows.Pair pair){return db.runInTransaction(()->{var current=load(new LedgerFilter(pair.current.startMs,pair.current.endMs,LedgerFilter.Metric.EXPENSE,scope.categoryId,scope.provider));var previous=load(new LedgerFilter(pair.previous.startMs,pair.previous.endMs,LedgerFilter.Metric.EXPENSE,scope.categoryId,scope.provider));return new PeriodComparison(SpendingComparison.calculate(current,previous),Math.subtractExact(current.incomeFen,previous.incomeFen),Math.subtractExact(current.netOutflowFen,previous.netOutflowFen));});}
 public static final class TrendPoint {public final java.time.YearMonth month;public final long valueFen;public final LedgerFilter filter;public final boolean inProgress;TrendPoint(java.time.YearMonth month,long value,LedgerFilter filter,boolean inProgress){this.month=month;valueFen=value;this.filter=filter;this.inProgress=inProgress;}}
 public List<TrendPoint> trend(LedgerFilter scope,java.time.YearMonth endMonth,int monthCount,java.time.Clock clock){long now=clock.millis();java.time.YearMonth current=java.time.YearMonth.from(java.time.Instant.ofEpochMilli(now).atZone(TimeWindows.LEDGER_ZONE));if((monthCount!=6&&monthCount!=12)||endMonth.isAfter(current))throw new IllegalArgumentException("Invalid trend window");return db.runInTransaction(()->{List<TrendPoint> points=new ArrayList<>();for(int offset=monthCount-1;offset>=0;offset--){java.time.YearMonth month=endMonth.minusMonths(offset);TimeWindows.Range range=TimeWindows.month(month);boolean inProgress=month.equals(current);long end=inProgress?Math.min(range.endMs,now):range.endMs;LedgerFilter filter=new LedgerFilter(range.startMs,end,scope.metric,scope.categoryId,scope.provider);points.add(new TrendPoint(month,load(filter).valueFen,filter,inProgress));}return Collections.unmodifiableList(points);});}
 public List<TrendPoint> trendRange(LedgerFilter scope){return db.runInTransaction(()->{List<TrendPoint> points=new ArrayList<>();java.time.YearMonth first=java.time.YearMonth.from(java.time.Instant.ofEpochMilli(scope.startMs).atZone(TimeWindows.LEDGER_ZONE));java.time.YearMonth last=java.time.YearMonth.from(java.time.Instant.ofEpochMilli(scope.endMs-1).atZone(TimeWindows.LEDGER_ZONE));for(java.time.YearMonth month=first;!month.isAfter(last);month=month.plusMonths(1)){TimeWindows.Range range=TimeWindows.month(month);LedgerFilter filter=new LedgerFilter(Math.max(range.startMs,scope.startMs),Math.min(range.endMs,scope.endMs),scope.metric,scope.categoryId,scope.provider);points.add(new TrendPoint(month,load(filter).valueFen,filter,false));}return Collections.unmodifiableList(points);});}
 public LiveData<Snapshot> observe(LedgerFilter filter){return observe(()->filter);}
 public LiveData<Snapshot> observe(java.util.function.Supplier<LedgerFilter> filters){return observeInternal(filters,false);}
 public LiveData<Snapshot> observeTotals(java.util.function.Supplier<LedgerFilter> filters){return observeInternal(filters,true);}
 private LiveData<Snapshot> observeInternal(java.util.function.Supplier<LedgerFilter> filters,boolean totalsOnly){return new LiveData<Snapshot>(){
  private final AtomicLong generation=new AtomicLong();private final Handler main=new Handler(Looper.getMainLooper());
  private final InvalidationTracker.Observer observer=new InvalidationTracker.Observer("ledger_transaction","category"){@Override public void onInvalidated(@NonNull Set<String> tables){request();}};
  private void request(){long current=generation.incrementAndGet();executor.execute(()->{LedgerFilter filter=filters.get();Snapshot snapshot;try{if(totalsOnly){var totals=db.statsDao().totals(filter.startMs,filter.endMs,filter.categoryId,filter.provider);snapshot=new Snapshot(filter,totals.expenseFen,totals.incomeFen,Collections.emptyList(),Collections.emptyList());}else snapshot=load(filter);}catch(RuntimeException error){snapshot=new Snapshot(filter,0,0,Collections.emptyList(),Collections.emptyList(),false);}Snapshot result=snapshot;main.post(()->{if(generation.get()==current&&hasActiveObservers())setValue(result);});});}
  @Override protected void onActive(){db.getInvalidationTracker().addObserver(observer);request();}
  @Override protected void onInactive(){generation.incrementAndGet();db.getInvalidationTracker().removeObserver(observer);}
 };}
}
