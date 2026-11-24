package com.example.budgetapp.ui.stats;
import android.app.Application;
import androidx.lifecycle.*;
import com.example.budgetapp.*;
import com.example.budgetapp.data.entity.CategoryEntity;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.stats.*;
import java.time.*;
import java.util.*;
public final class StatsViewModel extends AndroidViewModel {
 public static final class Display {
  public final StatsRepository.Snapshot snapshot;public final List<StatsRepository.TrendPoint> trend;public final List<CategoryEntity> categories;public final StatsRepository.PeriodComparison comparison;
  Display(StatsRepository.Snapshot snapshot,List<StatsRepository.TrendPoint> trend,List<CategoryEntity> categories,StatsRepository.PeriodComparison comparison){this.snapshot=snapshot;this.trend=Collections.unmodifiableList(new ArrayList<>(trend));this.categories=Collections.unmodifiableList(new ArrayList<>(categories));this.comparison=comparison;}
 }
 private final SavedStateHandle state;public final AppContainer app;private final MediatorLiveData<Display> display=new MediatorLiveData<>();private LiveData<StatsRepository.Snapshot> source;public final MutableLiveData<Boolean> error=new MutableLiveData<>(false);private long generation;
 public StatsViewModel(Application application,SavedStateHandle state){super(application);this.state=state;app=((BudgetApplication)application).container();if(state.get("month")==null)state.set("month",YearMonth.now(app.clock.withZone(TimeWindows.LEDGER_ZONE)).toString());refresh();}
 public LiveData<Display> display(){return display;}
 public YearMonth month(){String value=state.get("month");return value==null?YearMonth.now(app.clock.withZone(TimeWindows.LEDGER_ZONE)):YearMonth.parse(value);}
 public int monthCount(){Integer value=state.get("count");return value==null?6:value;}
 public LedgerFilter.Metric metric(){String value=state.get("metric");return value==null?LedgerFilter.Metric.EXPENSE:LedgerFilter.Metric.valueOf(value);}
 public String category(){return state.get("category");}public String provider(){return state.get("provider");}
 public String startDate(){String value=state.get("startDate");return value==null?month().atDay(1).toString():value;}
 public String endDate(){String value=state.get("endDate");return value==null?LocalDate.now(app.clock.withZone(TimeWindows.LEDGER_ZONE)).toString():value;}
 public boolean custom(){return Boolean.TRUE.equals(state.get("custom"));}
 public void setMonth(YearMonth month){if(month.isAfter(YearMonth.now(app.clock.withZone(TimeWindows.LEDGER_ZONE))))throw new IllegalArgumentException("Future month");state.set("month",month.toString());state.set("custom",false);refresh();}
 public void setMonthCount(int count){if(count!=6&&count!=12)throw new IllegalArgumentException("Month count");if(monthCount()==count)return;state.set("count",count);refresh();}
 public void setMetric(LedgerFilter.Metric metric){if(metric()==metric)return;state.set("metric",metric.name());refresh();}
 public void setScope(String category,String provider){if(Objects.equals(category(),category)&&Objects.equals(provider(),provider))return;state.set("category",category);state.set("provider",provider);refresh();}
 public void setCustomDates(LocalDate start,LocalDate end){TimeWindows.inclusiveDates(start,end);state.set("startDate",start.toString());state.set("endDate",end.toString());state.set("custom",true);refresh();}
 public void clearCustomRange(){state.set("custom",false);refresh();}
 public void refresh(){generation++;Clock clock=Clock.fixed(app.clock.instant(),TimeWindows.LEDGER_ZONE);YearMonth month=month();int count=monthCount();TimeWindows.Range range=custom()?TimeWindows.inclusiveDates(LocalDate.parse(startDate()),LocalDate.parse(endDate())):TimeWindows.month(month);long end=!custom()&&month.equals(YearMonth.now(clock))?Math.min(range.endMs,clock.millis()):range.endMs;LedgerFilter filter=new LedgerFilter(range.startMs,end,metric(),null,provider());boolean custom=custom();String trendCategory=category();if(source!=null)display.removeSource(source);source=app.stats.observe(()->{Clock liveClock=Clock.fixed(app.clock.instant(),TimeWindows.LEDGER_ZONE);long liveEnd=!custom&&month.equals(YearMonth.now(liveClock))?Math.min(range.endMs,liveClock.millis()):range.endMs;return new LedgerFilter(range.startMs,liveEnd,filter.metric,filter.categoryId,filter.provider);});display.addSource(source,snapshot->{long current=++generation;if(!snapshot.valid){error.setValue(true);return;}Clock queryClock=Clock.fixed(Instant.ofEpochMilli(!custom&&month.equals(YearMonth.now(app.clock.withZone(TimeWindows.LEDGER_ZONE)))?snapshot.filter.endMs:app.clock.millis()),TimeWindows.LEDGER_ZONE);LedgerFilter queryScope=snapshot.filter;app.io.execute(()->{try{Display result=app.db.runInTransaction(()->{var points=custom?app.stats.trendRange(new LedgerFilter(queryScope.startMs,queryScope.endMs,queryScope.metric,trendCategory,queryScope.provider)):app.stats.trend(new LedgerFilter(queryScope.startMs,queryScope.endMs,queryScope.metric,trendCategory,queryScope.provider),month,count,queryClock);java.util.Optional<ComparisonWindows.Pair> pair=custom?java.util.Optional.empty():month.equals(YearMonth.now(queryClock))?ComparisonWindows.monthToDate(queryClock):java.util.Optional.of(ComparisonWindows.completedMonth(month));return new Display(app.stats.load(queryScope),points,app.categories.categories(),pair.isPresent()?app.stats.periodComparison(queryScope,pair.get()):null);});app.main.execute(()->{if(current==generation){error.setValue(false);display.setValue(result);}});}catch(RuntimeException failure){app.main.execute(()->{if(current==generation)error.setValue(true);});}});});}
 @Override protected void onCleared(){generation++;}
}
