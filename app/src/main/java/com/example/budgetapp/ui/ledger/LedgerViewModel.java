package com.example.budgetapp.ui.ledger;

import android.app.Application;
import androidx.lifecycle.*;
import com.example.budgetapp.*;
import com.example.budgetapp.data.ManualLedgerRepository;
import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.stats.StatsRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class LedgerViewModel extends AndroidViewModel {
    public static final class EditorCategories {
        public final List<CategoryEntity> items;
        public final String selectedId;
        public final boolean suggested;
        EditorCategories(List<CategoryEntity> items,String selectedId,boolean suggested) {
            this.items=Collections.unmodifiableList(new ArrayList<>(items));
            this.selectedId=selectedId;this.suggested=suggested;
        }
    }
    private final SavedStateHandle state;
    private final AppContainer app;
    private final MediatorLiveData<StatsRepository.Snapshot> snapshot=new MediatorLiveData<>();
    private LiveData<StatsRepository.Snapshot> source;
    private final LiveData<List<LedgerTransactionEntity>> rows=Transformations.map(snapshot,s->{
        List<LedgerTransactionEntity> values=new ArrayList<>();
        for(var entry:s.entries){var tx=entry.transaction;String query=search().toLowerCase(java.util.Locale.ROOT);if((tx.merchantRaw+" "+tx.note).toLowerCase(java.util.Locale.ROOT).contains(query))values.add(tx);}
        return values;
    });
    private final MutableLiveData<Boolean> busy=new MutableLiveData<>(false);
    private final MutableLiveData<EditorCategories> categoryChoices=new MutableLiveData<>();
    private final AtomicLong choicesGeneration=new AtomicLong();
    private long editorGeneration;
    public final MutableLiveData<Integer> error=new MutableLiveData<>();
    public final MutableLiveData<Boolean> saved=new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> closed=new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> duplicate=new MutableLiveData<>(false);
    public final MutableLiveData<LedgerTransactionEntity> editing=new MutableLiveData<>();

    public LedgerViewModel(Application application,SavedStateHandle state) {
        super(application);this.state=state;app=((BudgetApplication)application).container();refresh();
        if(editingId()!=null) {
            if(!isEditorReady())loadEditor(editingId(),true);
            else refreshCategoryChoices();
        }
    }
    public LiveData<List<SourceEventEntity>> reviews(){return app.db.sourceEventDao().observeReview();}
    public LiveData<List<LedgerTransactionEntity>> rows(){return rows;}
    public LiveData<Boolean> busy(){return busy;}
    public LiveData<EditorCategories> categoryChoices(){return categoryChoices;}
    public LiveData<StatsRepository.Snapshot> snapshot(){return snapshot;}
    public LedgerFilter filter() {
        Long start=state.get("start"),end=state.get("end");String metric=state.get("metric");
        return new LedgerFilter(start==null?Long.MIN_VALUE:start,end==null?Long.MAX_VALUE:end,
                metric==null?LedgerFilter.Metric.NET_OUTFLOW:LedgerFilter.Metric.valueOf(metric),
                state.get("categoryFilter"),state.get("providerFilter"));
    }
    public String search(){String value=state.get("search");return value==null?"":value;}
    public void setSearch(String value){if(search().equals(value))return;state.set("search",value);refresh();}
    public void setFilter(LedgerFilter f) {
        state.set("start",f.startMs);state.set("end",f.endMs);state.set("metric",f.metric.name());
        state.set("categoryFilter",f.categoryId);state.set("providerFilter",f.provider);refresh();
    }
    public void refresh() {
        if(source!=null)snapshot.removeSource(source);
        source=app.stats.observe(filter());snapshot.addSource(source,snapshot::setValue);
    }
    public String field(String key,String fallback) {
        String value=state.get("draft."+key);return value==null?fallback:value;
    }
    public void field(String key,String value,boolean write) {
        if(!write)return;
        String old=state.get("draft."+key);state.set("draft."+key,value);
        if(Objects.equals(old,value))return;
        if("kind".equals(key)){state.set("categoryChosen",false);state.remove("draft.category");}
        if("kind".equals(key)||"merchant".equals(key)||"provider".equals(key))refreshCategoryChoices();
    }
    public String editingId(){return state.get("editingId");}
    public String editorToken(){return state.get("submission");}
    public String today(){return LocalDate.now(app.clock.withZone(TimeWindows.LEDGER_ZONE)).toString();}
    public boolean isEditorReady() {
        if(editingId()==null)return true;
        Long revision=state.get("revision");
        return revision!=null&&revision>0&&state.get("originalAt")!=null&&state.get("originalScope")!=null;
    }
    public void beginEditor(String id) {
        editorGeneration++;choicesGeneration.incrementAndGet();categoryChoices.setValue(null);editing.setValue(null);
        for(String key:new String[]{"amount","date","kind","merchant","provider","category","note","makeRule"})state.remove("draft."+key);
        for(String key:new String[]{"originalAt","originalPrecision","originalProvider","originalScope","originalKind","originalMerchant","originalCategory","originalOrigin","originalCategoryLocked"})state.remove(key);
        state.set("editingId",id);state.set("submission",UUID.randomUUID().toString());state.set("revision",0L);
        state.set("categoryChosen",false);saved.setValue(false);closed.setValue(false);error.setValue(null);duplicate.setValue(false);
        busy.setValue(false);
        if(id==null)refreshCategoryChoices();else loadEditor(id,false);
    }
    private void seed(String key,String value,boolean preserveDraft) {
        if(!preserveDraft||state.get("draft."+key)==null)state.set("draft."+key,value);
    }
    private void loadEditor(String id,boolean preserveDraft) {
        long generation=editorGeneration;busy.setValue(true);
        app.io.execute(()->{
            try {
                LedgerTransactionEntity tx=app.db.ledgerDao().find(id);
                app.main.execute(()->{
                    if(generation!=editorGeneration||!id.equals(editingId()))return;
                    if(tx==null){busy.setValue(false);error.setValue(R.string.entry_stale);return;}
                    Long revision=state.get("revision");
                    if(!preserveDraft||revision==null||revision==0)state.set("revision",tx.revision);
                    state.set("originalAt",tx.occurredAtMs);state.set("originalPrecision",tx.timePrecision);
                    state.set("originalProvider",tx.provider);state.set("originalScope",tx.accountScopeId);
                    state.set("originalKind",tx.kind);state.set("originalMerchant",tx.merchantRaw);
                    state.set("originalCategory",tx.categoryId);state.set("originalOrigin",tx.categoryOrigin);
                    state.set("originalCategoryLocked",tx.userLockedFields.contains("\"category\""));
                    seed("amount",Money.formatFen(tx.amountMinor),preserveDraft);
                    seed("date",Instant.ofEpochMilli(tx.occurredAtMs).atZone(TimeWindows.LEDGER_ZONE).toLocalDate().toString(),preserveDraft);
                    seed("kind",tx.kind,preserveDraft);seed("merchant",tx.merchantRaw,preserveDraft);
                    seed("provider",tx.provider,preserveDraft);seed("category",tx.categoryId,preserveDraft);
                    seed("note",tx.note,preserveDraft);editing.setValue(tx);busy.setValue(false);refreshCategoryChoices();
                });
            } catch(RuntimeException failure) {
                app.main.execute(()->{if(generation==editorGeneration){busy.setValue(false);error.setValue(R.string.entry_failed);}});
            }
        });
    }
    private String accountScope(String provider) {
        String originalProvider=state.get("originalProvider"),originalScope=state.get("originalScope");
        return editingId()!=null&&provider.equals(originalProvider)&&originalScope!=null?originalScope:app.settings.accountScope(provider);
    }
    private boolean keepOriginalCategory(Kind kind,String merchant,String provider,String scope) {
        if(editingId()==null||!kind.name().equals(state.get("originalKind")))return false;
        if("USER".equals(state.get("originalOrigin"))||Boolean.TRUE.equals(state.get("originalCategoryLocked")))return true;
        return CategoryMatcher.normalize(merchant).equals(CategoryMatcher.normalize(state.get("originalMerchant")))
                &&provider.equals(state.get("originalProvider"))&&scope.equals(state.get("originalScope"));
    }
    public void chooseCategory(String id) {
        state.set("draft.category",id);state.set("categoryChosen",true);refreshCategoryChoices();
    }
    public void refreshCategoryChoices() {
        if(editingId()!=null&&!isEditorReady())return;
        long generation=choicesGeneration.incrementAndGet(),editor=editorGeneration;
        Kind kind=Kind.valueOf(field("kind","EXPENSE"));String merchant=field("merchant",""),provider=field("provider","OTHER");
        String scope=accountScope(provider),chosen=field("category",null),historical=state.get("originalCategory");
        boolean explicit=Boolean.TRUE.equals(state.get("categoryChosen")),hasExisting=editingId()!=null;
        boolean preserve=!explicit&&keepOriginalCategory(kind,merchant,provider,scope);
        app.io.execute(()->{
            if(generation!=choicesGeneration.get())return;
            try {
                var match=new CategoryMatcher().match(kind,merchant,provider,scope,app.db.categoryDao().ruleRows());
                String selected=explicit?chosen:preserve?historical:match.categoryId;
                List<CategoryEntity> categories=new ArrayList<>();
                for(var category:app.categories.categories()) {
                    boolean existingArchived=hasExisting&&category.id.equals(historical)&&category.id.equals(selected);
                    if(category.flowScope.equals(kind.name())&&(!category.archived||existingArchived))categories.add(category);
                }
                EditorCategories result=new EditorCategories(categories,selected,!explicit&&!preserve&&match.origin==CategoryOrigin.RULE);
                app.main.execute(()->{if(generation==choicesGeneration.get()&&editor==editorGeneration)categoryChoices.setValue(result);});
            } catch(RuntimeException failure) {
                app.main.execute(()->{if(generation==choicesGeneration.get()&&editor==editorGeneration)error.setValue(R.string.entry_failed);});
            }
        });
    }
    public void save(boolean distinct) {
        if(Boolean.TRUE.equals(busy.getValue()))return;
        if(!isEditorReady()){error.setValue(R.string.entry_stale);return;}
        final TransactionDraft draft;
        try {
            Kind kind=Kind.valueOf(field("kind","EXPENSE"));String provider=field("provider","OTHER"),merchant=field("merchant","");
            long occurredAt=app.clock.millis();TimePrecision precision=TimePrecision.SECOND;
            Long originalAt=state.get("originalAt");String originalPrecision=state.get("originalPrecision");
            if(editingId()!=null){LocalDate date=LocalDate.parse(field("date",today()));occurredAt=TimeWindows.dayAnchor(date);precision=TimePrecision.DAY;if(originalAt!=null&&date.equals(Instant.ofEpochMilli(originalAt).atZone(TimeWindows.LEDGER_ZONE).toLocalDate())){occurredAt=originalAt;precision=TimePrecision.valueOf(originalPrecision);}}
            String scope=accountScope(provider);boolean explicit=Boolean.TRUE.equals(state.get("categoryChosen"));
            String category=explicit?field("category",null):keepOriginalCategory(kind,merchant,provider,scope)?state.get("originalCategory"):null;
            draft=new TransactionDraft(kind,Money.parseFen(field("amount","")),occurredAt,precision,merchant,provider,scope,category,field("note",""),explicit);
        } catch(RuntimeException invalid){error.setValue(R.string.entry_invalid);return;}
        long generation=editorGeneration;String id=editingId(),submission=state.get("submission");Long revision=state.get("revision");
        boolean remember="true".equals(field("makeRule","false"));busy.setValue(true);error.setValue(null);
        app.io.execute(()->{
            try {
                boolean success,warning=false;
                if(id==null) {
                    var result=app.db.runInTransaction(()->{
                        TransactionDraft completed=new TransactionDraft(draft.kind,draft.amountMinor,app.clock.millis(),TimePrecision.SECOND,draft.merchant,draft.provider,draft.accountScopeId,draft.categoryId,draft.note,draft.categoryExplicit);
                        var saved=app.repository.save(submission,completed,distinct);if(saved.status==ManualLedgerRepository.SaveResult.Status.SAVED&&remember)rememberRule(completed,saved.transactionId);return saved;
                    });
                    success=result.status==ManualLedgerRepository.SaveResult.Status.SAVED;warning=result.status==ManualLedgerRepository.SaveResult.Status.DUPLICATE_WARNING;
                } else {
                    success=app.db.runInTransaction(()->{var written=app.repository.update(id,revision==null?0:revision,draft);if(written.status==ManualLedgerRepository.WriteResult.Status.APPLIED&&remember)rememberRule(draft,id);return written.status==ManualLedgerRepository.WriteResult.Status.APPLIED;});
                }
                boolean ok=success,warn=warning;
                app.main.execute(()->{
                    refresh();if(generation!=editorGeneration)return;
                    busy.setValue(false);
                    if(ok){state.set("submission",UUID.randomUUID().toString());saved.setValue(true);closed.setValue(true);}
                    else if(warn)duplicate.setValue(true);else error.setValue(R.string.entry_stale);
                });
            } catch(RuntimeException failure) {
                app.main.execute(()->{if(generation==editorGeneration){busy.setValue(false);error.setValue(failure instanceof IllegalArgumentException?R.string.entry_invalid:R.string.entry_failed);}});
            }
        });
    }
    private void rememberRule(TransactionDraft draft,String transactionId) {
        var transaction=app.db.ledgerDao().find(transactionId);var rule=new CategoryRuleEntity();
        rule.id=UUID.randomUUID().toString();rule.provider=draft.provider;rule.scopeFilter=draft.accountScopeId;
        rule.matchType="EXACT";rule.merchantPattern=draft.merchant;rule.categoryId=transaction.categoryId;rule.enabled=true;
        app.categories.upsertRule(rule);
    }
    public void delete() {
        if(Boolean.TRUE.equals(busy.getValue()))return;
        String id=editingId();Long revision=state.get("revision");if(id==null||revision==null)return;
        long generation=editorGeneration;busy.setValue(true);
        app.io.execute(()->{
            try {
                var result=app.repository.delete(id,revision);
                app.main.execute(()->{
                    if(result.status==ManualLedgerRepository.WriteResult.Status.APPLIED){state.set("undoId",id);state.set("undoRevision",result.revision);refresh();}
                    if(generation!=editorGeneration)return;busy.setValue(false);
                    if(result.status==ManualLedgerRepository.WriteResult.Status.APPLIED)closed.setValue(true);else error.setValue(R.string.entry_stale);
                });
            } catch(RuntimeException failure){app.main.execute(()->{if(generation==editorGeneration){busy.setValue(false);error.setValue(R.string.entry_failed);}});}
        });
    }
    public boolean canUndo(){return state.get("undoId")!=null;}
    public void undo() {
        String id=state.get("undoId");Long revision=state.get("undoRevision");if(id==null||revision==null)return;
        app.io.execute(()->{try{var result=app.repository.undoDelete(id,revision);app.main.execute(()->{state.remove("undoId");if(result.status!=ManualLedgerRepository.WriteResult.Status.APPLIED)error.setValue(R.string.entry_stale);refresh();});}catch(RuntimeException failure){app.main.execute(()->error.setValue(R.string.entry_failed));}});
    }
    @Override protected void onCleared(){editorGeneration++;choicesGeneration.incrementAndGet();}
}
