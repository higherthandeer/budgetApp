package com.example.budgetapp.stats;
import com.example.budgetapp.processing.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SpendingComparisonTest {
 private StatsRepository.Snapshot snapshot(long food,long travel){return new StatsRepository.Snapshot(new LedgerFilter(0,1,LedgerFilter.Metric.EXPENSE,null,null),food+travel,0,Arrays.asList(new StatsRepository.CategoryAmount("food","餐饮",food,null),new StatsRepository.CategoryAmount("travel","交通",travel,null)),Collections.emptyList());}
 @Test public void amountsAndCategoryExplanationsReconcile(){var result=SpendingComparison.calculate(snapshot(80000,30000),snapshot(60000,40000));assertEquals(10000,result.deltaFen);assertEquals(new BigDecimal("10.00"),result.percent);assertTrue(result.explanation.contains("餐饮 +200.00"));assertTrue(result.explanation.contains("交通 −100.00"));assertTrue(result.explanation.contains("合计 +100.00"));assertEquals(10000,result.categoryDeltas.stream().mapToLong(c->c.deltaFen).sum());}
 @Test public void zeroBaseAndDisappearingCategoriesAreHandled(){var added=SpendingComparison.calculate(snapshot(100,0),snapshot(0,0));assertNull(added.percent);assertEquals("新增 1.00 元",added.label);assertEquals("无变化",SpendingComparison.calculate(snapshot(0,0),snapshot(0,0)).label);assertEquals(new BigDecimal("-100.00"),SpendingComparison.calculate(snapshot(0,0),snapshot(100,0)).percent);assertEquals(0,SpendingComparison.calculate(snapshot(0,100),snapshot(100,0)).deltaFen);}
 @Test public void incompatibleScopesAndNetPercentagesAreRejected(){var expense=snapshot(100,0);var net=new StatsRepository.Snapshot(new LedgerFilter(0,1,LedgerFilter.Metric.NET_OUTFLOW,null,null),100,200,Collections.emptyList(),Collections.emptyList());assertThrows(IllegalArgumentException.class,()->SpendingComparison.calculate(net,expense));}
}
