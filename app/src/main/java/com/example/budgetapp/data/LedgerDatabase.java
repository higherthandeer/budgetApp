package com.example.budgetapp.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.*;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.budgetapp.data.dao.*;
import com.example.budgetapp.data.entity.*;

@Database(entities = {SourceEventEntity.class, LedgerTransactionEntity.class, CategoryEntity.class,
        CategoryRuleEntity.class, TransactionIdentityEntity.class}, version = 3, exportSchema = true)
public abstract class LedgerDatabase extends RoomDatabase {
    public abstract SourceEventDao sourceEventDao();

    public abstract LedgerDao ledgerDao();

    public abstract CategoryDao categoryDao();

    public abstract StatsDao statsDao();

    public static LedgerDatabase open(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), LedgerDatabase.class, "budget.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(CALLBACK).build();
    }

    public static final androidx.room.migration.Migration MIGRATION_1_2 = new androidx.room.migration.Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE source_event ADD COLUMN raw_expires_at_ms INTEGER");
            installGuards(db);
        }
    };
    public static final androidx.room.migration.Migration MIGRATION_2_3 = new androidx.room.migration.Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE category ADD COLUMN icon_key TEXT NOT NULL DEFAULT 'category'");
            String[] names = {"餐饮", "交通", "工资", "居住", "健康", "娱乐", "日常"};
            for (String name : names)
                db.execSQL("UPDATE category SET icon_key=? WHERE name=?", new Object[]{CategoryIconCatalog.defaultForName(name), name});
        }
    };
    public static final Callback CALLBACK = new Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            seedDefaults(db);
            installGuards(db);
        }

        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("UPDATE category SET name='待归类支出' WHERE id='00000000-0000-0000-0000-000000000001' AND name='未分类支出'");
        }
    };

    private static void installGuards(SupportSQLiteDatabase db) {
        for (String action : new String[]{"INSERT", "UPDATE"}) {
            String suffix = action.toLowerCase(java.util.Locale.ROOT);
            db.execSQL("CREATE TRIGGER IF NOT EXISTS ledger_validate_" + suffix + " BEFORE " + action + " ON ledger_transaction BEGIN "
                    + "SELECT CASE WHEN NEW.amount_minor<=0 OR NEW.currency<>'CNY' OR NEW.kind NOT IN ('EXPENSE','INCOME') OR NEW.booking_state NOT IN ('POSTED','DELETED') OR NEW.time_precision NOT IN ('DAY','MINUTE','SECOND') OR NEW.category_origin NOT IN ('USER','RULE','DEFAULT') THEN RAISE(ABORT,'Invalid ledger fact') END; "
                    + "SELECT CASE WHEN NEW.merged_into_id=NEW.id OR EXISTS (WITH RECURSIVE chain(id) AS (SELECT NEW.merged_into_id UNION SELECT t.merged_into_id FROM ledger_transaction t JOIN chain c ON t.id=c.id WHERE t.merged_into_id IS NOT NULL) SELECT 1 FROM chain WHERE id=NEW.id) THEN RAISE(ABORT,'Merge cycle') END; END");
        }
        db.execSQL("CREATE TRIGGER IF NOT EXISTS source_facts_immutable BEFORE UPDATE OF parsed_json ON source_event WHEN OLD.parsed_json IS NOT NULL AND NEW.parsed_json IS NOT OLD.parsed_json BEGIN SELECT RAISE(ABORT,'Accepted source facts are immutable'); END");
    }

    public static void seedDefaults(SupportSQLiteDatabase db) {
        boolean icons = false;
        try (android.database.Cursor columns = db.query("PRAGMA table_info(category)")) {
            while (columns.moveToNext())
                if ("icon_key".equals(columns.getString(columns.getColumnIndexOrThrow("name"))))
                    icons = true;
        }
        String[] names = {"待归类支出", "未分类收入", "餐饮", "交通", "工资"};
        for (int i = 0; i < names.length; i++) {
            Object[] values = {String.format(java.util.Locale.ROOT, "00000000-0000-0000-0000-%012d", i + 1), names[i], i == 1 || i == 4 ? "INCOME" : "EXPENSE", i};
            if (icons)
                db.execSQL("INSERT OR IGNORE INTO category (id,name,flow_scope,sort_order,archived,icon_key) VALUES (?,?,?,?,0,?)", new Object[]{values[0], values[1], values[2], values[3], CategoryIconCatalog.defaultForName(names[i])});
            else
                db.execSQL("INSERT OR IGNORE INTO category (id,name,flow_scope,sort_order,archived) VALUES (?,?,?,?,0)", values);
        }
    }
}
