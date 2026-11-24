package com.example.budgetapp.ui.review;
import android.app.Application;
import androidx.lifecycle.*;
import com.example.budgetapp.*;
import com.example.budgetapp.data.ManualLedgerRepository.WriteResult;
import com.example.budgetapp.data.entity.*;
import java.util.*;
import java.util.function.*;
public final class ReviewViewModel extends AndroidViewModel {
 public final AppContainer app;private final MutableLiveData<Boolean> busy=new MutableLiveData<>(false);public final MutableLiveData<Integer> error=new MutableLiveData<>();
 private final LiveData<List<SourceEventEntity>> rows;
 public ReviewViewModel(Application application){super(application);app=((BudgetApplication)application).container();rows=app.db.sourceEventDao().observeReview();}
 public LiveData<List<SourceEventEntity>> rows(){return rows;}
 public LiveData<Boolean> busy(){return busy;}
 public void run(Supplier<WriteResult> action){if(Boolean.TRUE.equals(busy.getValue()))return;busy.setValue(true);app.io.execute(()->{try{WriteResult result=action.get();app.main.execute(()->{busy.setValue(false);error.setValue(result.status==WriteResult.Status.APPLIED?null:R.string.review_error);});}catch(RuntimeException e){app.main.execute(()->{busy.setValue(false);error.setValue(R.string.review_error);});}});}
 public void candidates(SourceEventEntity e,Consumer<List<LedgerTransactionEntity>> result){app.io.execute(()->{List<LedgerTransactionEntity> rows=app.db.ledgerDao().inScope(e.provider,e.accountScopeId);app.main.execute(()->result.accept(rows));});}
 public void posted(Consumer<List<LedgerTransactionEntity>> result){app.io.execute(()->{var rows=app.db.ledgerDao().listPosted(Long.MIN_VALUE,Long.MAX_VALUE,null,null,null);app.main.execute(()->result.accept(rows));});}
}
