package com.example.budgetapp.stats;
import com.example.budgetapp.processing.TimeWindows;
import java.time.*;
import java.util.Optional;
public final class ComparisonWindows {
 private ComparisonWindows(){}
 public static final class Pair {
  public final TimeWindows.Range current,previous;public final int comparedDays;
  public Pair(TimeWindows.Range current,TimeWindows.Range previous,int comparedDays){this.current=current;this.previous=previous;this.comparedDays=comparedDays;}
 }
 public static Pair completedMonth(YearMonth month){return new Pair(TimeWindows.month(month),TimeWindows.month(month.minusMonths(1)),month.lengthOfMonth());}
 public static Optional<Pair> monthToDate(Clock clock){LocalDate today=LocalDate.now(clock.withZone(TimeWindows.LEDGER_ZONE));if(today.getDayOfMonth()==1)return Optional.empty();YearMonth month=YearMonth.from(today),previous=month.minusMonths(1);int days=Math.min(today.getDayOfMonth()-1,previous.lengthOfMonth());return Optional.of(new Pair(TimeWindows.inclusiveDates(month.atDay(1),month.atDay(days)),TimeWindows.inclusiveDates(previous.atDay(1),previous.atDay(days)),days));}
}
