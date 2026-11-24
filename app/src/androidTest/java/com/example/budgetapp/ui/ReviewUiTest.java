package com.example.budgetapp.ui;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.*;
import com.example.budgetapp.data.*;
import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.ui.review.ReviewViewModel;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class ReviewUiTest {
 @Test public void resolveWithoutDuplicateBooking() throws Exception {
  BudgetApplication app=ApplicationProvider.getApplicationContext();assertTrue(app.getPackageName().endsWith(".testing"));
  String tx=app.container().io.submit(()->{app.container().db.clearAllTables();LedgerDatabase.seedDefaults(app.container().db.getOpenHelper().getWritableDatabase());String id=app.container().repository.save("ui-existing",new TransactionDraft(Kind.EXPENSE,2880,1793376000000L,TimePrecision.DAY,"面馆","WECHAT","scope",null,""),false).transactionId;SourceEventEntity e=new SourceEventEntity();e.id="ui-review";e.sourceType="NOTIFICATION";e.eventKey="ui-review";e.provider="WECHAT";e.accountScopeId="scope";e.processState="REVIEW";e.reviewCandidatesJson="[\""+id+"\"]";e.reviewReasonCodesJson="[\"POSSIBLE_DUPLICATE\"]";ParsedTransaction p=new ParsedTransaction();p.kind=Kind.EXPENSE;p.amountMinor=2880L;p.currency="CNY";p.merchant="面馆";p.occurredAtMs=1793376000000L;p.timePrecision=TimePrecision.DAY;e.candidateJson=EventCodec.encodeParsed(new ParseResult(ParseResult.Disposition.REVIEW,p,"POSSIBLE_DUPLICATE","synthetic",1));app.container().db.sourceEventDao().insertIgnore(e);return id;}).get();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicReference<ReviewViewModel> vm=new AtomicReference<>();scenario.onActivity(a->{a.showReview();vm.set(a.reviewViewModel());});UiTestSupport.awaitValue(vm.get().rows(),r->r!=null&&r.size()==1);
   onView(withId(R.id.review_link)).perform(click());onView(withText(R.string.review_link_confirm)).perform(click());UiTestSupport.awaitValue(vm.get().rows(),r->r!=null&&r.isEmpty());assertEquals(1,(int)app.container().io.submit(()->app.container().db.ledgerDao().countPosted()).get());
   copyReview(app,"ui-new");UiTestSupport.awaitValue(vm.get().rows(),r->r!=null&&r.size()==1);UiTestSupport.awaitView(scenario,R.id.review_new);onView(withId(R.id.review_new)).perform(click());onView(withText(R.string.category_apply)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());UiTestSupport.awaitValue(vm.get().rows(),r->r!=null&&r.isEmpty());assertEquals(2,(int)app.container().io.submit(()->app.container().db.ledgerDao().countPosted()).get());
   copyReview(app,"ui-ignore");UiTestSupport.awaitValue(vm.get().rows(),r->r!=null&&r.size()==1);UiTestSupport.awaitView(scenario,R.id.review_ignore);onView(withId(R.id.review_ignore)).perform(click());onView(withText(R.string.review_ignore)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());UiTestSupport.awaitValue(vm.get().rows(),r->r!=null&&r.isEmpty());assertEquals(2,(int)app.container().io.submit(()->app.container().db.ledgerDao().countPosted()).get());

  }
 }
 private void copyReview(BudgetApplication app,String key) throws Exception {app.container().io.submit(()->{var e=app.container().db.sourceEventDao().find("ui-review");e.id=key;e.eventKey=key;e.transactionId=null;e.processState="REVIEW";e.parsedJson=null;e.correctedCandidateJson=null;e.reviewResolution=null;e.resolvedAt=null;app.container().db.sourceEventDao().insertIgnore(e);}).get();}
}
