package com.example.budgetapp.stats;
import com.example.budgetapp.processing.*;
import java.math.*;
import java.util.*;
public final class SpendingComparison {
 private SpendingComparison(){}
 public static final class CategoryDelta {public final String categoryId,name;public final long deltaFen;CategoryDelta(String id,String name,long delta){categoryId=id;this.name=name;deltaFen=delta;}}
 public static final class Result {
  public final long deltaFen;public final BigDecimal percent;public final String label,explanation;public final List<CategoryDelta> categoryDeltas;
  Result(long delta,BigDecimal percent,String label,List<CategoryDelta> changes,String explanation){deltaFen=delta;this.percent=percent;this.label=label;categoryDeltas=Collections.unmodifiableList(new ArrayList<>(changes));this.explanation=explanation;}
 }
 public static Result calculate(StatsRepository.Snapshot current,StatsRepository.Snapshot previous){
  if(!current.valid||!previous.valid||current.filter.metric!=LedgerFilter.Metric.EXPENSE||previous.filter.metric!=LedgerFilter.Metric.EXPENSE||!Objects.equals(current.filter.categoryId,previous.filter.categoryId)||!Objects.equals(current.filter.provider,previous.filter.provider))throw new IllegalArgumentException("Incompatible comparison scopes");
  long delta=Math.subtractExact(current.expenseFen,previous.expenseFen);BigDecimal percent=previous.expenseFen==0?null:BigDecimal.valueOf(delta).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(previous.expenseFen),2,RoundingMode.HALF_UP);
  Map<String,Long> amounts=new HashMap<>();Map<String,String> names=new HashMap<>();for(var c:current.categories){amounts.merge(c.categoryId,c.amountFen,Math::addExact);names.put(c.categoryId,c.name);}for(var c:previous.categories){amounts.merge(c.categoryId,-c.amountFen,Math::addExact);names.putIfAbsent(c.categoryId,c.name);}List<CategoryDelta> up=new ArrayList<>(),down=new ArrayList<>();for(var e:amounts.entrySet()){var c=new CategoryDelta(e.getKey(),names.get(e.getKey()),e.getValue());if(c.deltaFen>0)up.add(c);else if(c.deltaFen<0)down.add(c);}up.sort(Comparator.comparingLong((CategoryDelta c)->c.deltaFen).reversed().thenComparing(c->c.categoryId));down.sort(Comparator.comparingLong((CategoryDelta c)->c.deltaFen).thenComparing(c->c.categoryId));List<CategoryDelta> changes=new ArrayList<>(up);changes.addAll(down);List<String> parts=new ArrayList<>();long other=0;boolean hasOther=false;for(List<CategoryDelta> group:Arrays.asList(up,down))for(int i=0;i<group.size();i++){var c=group.get(i);if(i<3)parts.add(c.name+" "+signed(c.deltaFen)+" 元");else{other=Math.addExact(other,c.deltaFen);hasOther=true;}}if(hasOther)parts.add("其他 "+signed(other)+" 元");parts.add("合计 "+signed(delta)+" 元");String label=delta==0?"无变化":previous.expenseFen==0?"新增 "+Money.formatFen(current.expenseFen)+" 元":(delta>0?"增加 ":"减少 ")+Money.formatFen(delta<0?-delta:delta)+" 元";return new Result(delta,percent,label,changes,String.join("；",parts));
 }
 private static String signed(long fen){return fen>0?"+"+Money.formatFen(fen):fen<0?"−"+Money.formatFen(-fen):"0.00";}
}
