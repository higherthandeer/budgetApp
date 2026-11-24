package com.example.budgetapp.data;
import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.stats.StatsRepository;
import com.example.budgetapp.ui.common.CategoryIcons;
import com.example.budgetapp.R;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class) public class CategoryIconStorageTest {
 @Rule public MigrationTestHelper helper=new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),LedgerDatabase.class.getCanonicalName(),new FrameworkSQLiteOpenHelperFactory());
 @Test public void selectedIconSurvivesRenameAndUpdatesHistoricalRows() {
  var context=ApplicationProvider.getApplicationContext();var db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase.class).addCallback(LedgerDatabase.CALLBACK).allowMainThreadQueries().build();
  try {
   var repo=new CategoryRepository(db,Clock.systemUTC());
   var category=repo.createCategory("咖啡支出",Kind.EXPENSE,"local_cafe");
   var income=repo.createCategory("收入类别",Kind.INCOME,"work");assertEquals("work",db.categoryDao().find(income.id).iconKey);
   var saved=new ManualLedgerRepository(db,Clock.systemUTC()).save("icon-ledger",new TransactionDraft(Kind.EXPENSE,1500,1000,TimePrecision.SECOND,"咖啡店","OTHER","scope",category.id,"原备注"),true);
   var stats=new StatsRepository(db,Runnable::run);var filter=new LedgerFilter(0,2000,LedgerFilter.Metric.EXPENSE,null,null);
   assertEquals("local_cafe",stats.load(filter).entries.get(0).categoryIconKey);
   repo.renameCategory(category.id,"新的名字");assertEquals("local_cafe",db.categoryDao().find(category.id).iconKey);
   repo.setCategoryIcon(category.id,"restaurant");assertEquals("restaurant",stats.load(filter).entries.get(0).categoryIconKey);
   assertEquals(1500,db.ledgerDao().find(saved.transactionId).amountMinor);assertEquals("原备注",db.ledgerDao().find(saved.transactionId).note);assertEquals(1,db.ledgerDao().find(saved.transactionId).revision);
   assertThrows(IllegalArgumentException.class,()->repo.setCategoryIcon(category.id,"missing-icon"));assertEquals("restaurant",db.categoryDao().find(category.id).iconKey);
   assertThrows(IllegalArgumentException.class,()->repo.createCategory("无效",Kind.INCOME,"missing-icon"));
   assertEquals(R.drawable.msr_category,CategoryIcons.forKey("unavailable"));
  } finally {db.close();}
 }
 @Test public void twoToThreeMigrationPreservesOldCategoriesAndPersistsNewChoices() throws Exception {
  String file="icons-migration-test";
  var old=helper.createDatabase(file,2);LedgerDatabase.seedDefaults(old);
  old.execSQL("INSERT INTO category (id,name,flow_scope,sort_order,archived) VALUES ('custom','自定义旧类','EXPENSE',6,1)");old.close();
  var upgraded=helper.runMigrationsAndValidate(file,3,true,LedgerDatabase.MIGRATION_2_3);
  try(var cursor=upgraded.query("SELECT icon_key FROM category WHERE name='餐饮'")){assertTrue(cursor.moveToFirst());assertEquals("restaurant",cursor.getString(0));}
  try(var cursor=upgraded.query("SELECT name,archived,icon_key FROM category WHERE id='custom'")){assertTrue(cursor.moveToFirst());assertEquals("自定义旧类",cursor.getString(0));assertEquals(1,cursor.getInt(1));assertEquals("category",cursor.getString(2));}
  upgraded.execSQL("UPDATE category SET icon_key='local_cafe' WHERE id='custom'");upgraded.close();
  var reopened=helper.runMigrationsAndValidate(file,3,true);
  try(var cursor=reopened.query("SELECT icon_key FROM category WHERE id='custom'")){assertTrue(cursor.moveToFirst());assertEquals("local_cafe",cursor.getString(0));}finally{reopened.close();}
 }
}
