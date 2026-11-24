package com.example.budgetapp.processing;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.example.budgetapp.processing.LedgerTypes.*;
public class CategoryMatcherTest {
 private final CategoryMatcher matcher=new CategoryMatcher();
 private CategoryMatcher.Rule rule(String id,String category,String type,String pattern){CategoryMatcher.Rule r=new CategoryMatcher.Rule();r.id=id;r.categoryId=category;r.kind=Kind.EXPENSE;r.matchType=type;r.pattern=pattern;r.enabled=true;r.version=1;return r;}
 @Test public void matchingUsesDirectionSafeNormalizationAndScope(){
  var r=rule("one","food","EXACT","  午间面馆1店  ");r.provider="WECHAT";r.scopeFilter="scope";
  assertEquals("food",matcher.match(Kind.EXPENSE,"午间面馆1店","WECHAT","scope",Arrays.asList(r)).categoryId);
  assertEquals(UNCATEGORIZED_EXPENSE,matcher.match(Kind.EXPENSE,"午间面馆2店","WECHAT","scope",Arrays.asList(r)).categoryId);
  assertEquals(CategoryOrigin.DEFAULT,matcher.match(Kind.EXPENSE,"午间面馆1店","ALIPAY","scope",Arrays.asList(r)).origin);
  assertEquals(CategoryOrigin.DEFAULT,matcher.match(Kind.EXPENSE,"午间面馆1店","WECHAT","other",Arrays.asList(r)).origin);
  assertEquals(UNCATEGORIZED_INCOME,matcher.match(Kind.INCOME,"午间面馆1店","WECHAT","scope",Arrays.asList(r)).categoryId);
 }
 @Test public void prioritySpecificityLengthAndConflictAreDeterministic(){
  var exact=rule("one","food","EXACT","面馆");var contains=rule("two","travel","CONTAINS","面");
  assertEquals("food",matcher.match(Kind.EXPENSE,"面馆","OTHER","scope",Arrays.asList(contains,exact)).categoryId);
  contains.priority=2;assertEquals("travel",matcher.match(Kind.EXPENSE,"面馆","OTHER","scope",Arrays.asList(exact,contains)).categoryId);
  contains.priority=0;var collision=rule("three","travel","EXACT","面馆");assertTrue(matcher.match(Kind.EXPENSE,"面馆","OTHER","scope",Arrays.asList(collision,exact)).conflict);
  exact.enabled=false;assertEquals(CategoryOrigin.DEFAULT,matcher.match(Kind.EXPENSE,"面馆","OTHER","scope",Arrays.asList(exact)).origin);
  exact.enabled=true;exact.categoryArchived=true;assertEquals(CategoryOrigin.DEFAULT,matcher.match(Kind.EXPENSE,"面馆","OTHER","scope",Arrays.asList(exact)).origin);
 }
}
