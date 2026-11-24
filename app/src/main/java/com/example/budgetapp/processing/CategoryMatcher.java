package com.example.budgetapp.processing;
import java.util.*;
import com.example.budgetapp.processing.LedgerTypes.*;
/** Safe merchant matching; ties across categories are deliberately unresolved. */
public final class CategoryMatcher {
 public static final class Rule {
  public String id,categoryId,provider,scopeFilter,matchType,pattern;
  public long version;public Kind kind;public int priority;public boolean enabled,categoryArchived;
 }
 public static final class Match {
  public final String categoryId,ruleId;public final CategoryOrigin origin;public final boolean conflict;
  Match(String categoryId,CategoryOrigin origin,String ruleId,boolean conflict){this.categoryId=categoryId;this.origin=origin;this.ruleId=ruleId;this.conflict=conflict;}
 }
 public static String normalize(String text){return text==null?"":text.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
 public Match match(Kind kind,String merchant,String provider,String scopeId,List<Rule> rules){
  String value=normalize(merchant);List<Rule> matches=new ArrayList<>();
  for(Rule r:rules){if(!r.enabled||r.categoryArchived||r.kind!=kind||(r.provider!=null&&!r.provider.equals(provider))||(r.scopeFilter!=null&&!r.scopeFilter.equals(scopeId)))continue;String pattern=normalize(r.pattern);if(pattern.isEmpty())continue;boolean applies="EXACT".equals(r.matchType)?value.equals(pattern):"PREFIX".equals(r.matchType)?value.startsWith(pattern):"CONTAINS".equals(r.matchType)&&value.contains(pattern);if(applies)matches.add(r);}
  Comparator<Rule> order=Comparator.comparingInt((Rule r)->r.priority).thenComparingInt(r->rank(r.matchType)).thenComparingInt(r->normalize(r.pattern).length());matches.sort(order.reversed().thenComparing(r->r.id));
  if(matches.isEmpty())return fallback(kind,false);
  Rule best=matches.get(0);for(Rule r:matches){if(order.compare(best,r)!=0)break;if(!best.categoryId.equals(r.categoryId))return fallback(kind,true);}
  return new Match(best.categoryId,CategoryOrigin.RULE,best.id,false);
 }
 private static int rank(String type){return "EXACT".equals(type)?3:"PREFIX".equals(type)?2:1;}
 private static Match fallback(Kind kind,boolean conflict){return new Match(kind==Kind.EXPENSE?LedgerTypes.UNCATEGORIZED_EXPENSE:LedgerTypes.UNCATEGORIZED_INCOME,CategoryOrigin.DEFAULT,null,conflict);}
}
