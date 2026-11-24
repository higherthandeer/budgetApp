package com.example.budgetapp.data;

import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;

import java.time.Clock;
import java.util.*;

import org.json.*;

public final class CategoryRepository {
    public static final class Change {
        public final String transactionId, beforeCategoryId, afterCategoryId;
        public final long revision;

        Change(LedgerTransactionEntity tx, String after) {
            transactionId = tx.id;
            revision = tx.revision;
            beforeCategoryId = tx.categoryId;
            afterCategoryId = after;
        }
    }

    public static final class Preview {
        public final String ruleId;
        public final long ruleVersion;
        public final List<Change> changes;
        private final String ruleSignature;

        Preview(String id, long version, List<Change> changes, String signature) {
            ruleId = id;
            ruleVersion = version;
            this.changes = Collections.unmodifiableList(new ArrayList<>(changes));
            ruleSignature = signature;
        }
    }

    private final LedgerDatabase db;
    private final Clock clock;
    private final CategoryMatcher matcher = new CategoryMatcher();

    public CategoryRepository(LedgerDatabase db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public List<CategoryEntity> categories() {
        return db.categoryDao().all();
    }

    public static final class DuplicateCategoryName extends IllegalArgumentException {
        public DuplicateCategoryName() {
            super("Category name already exists for this flow");
        }
    }

    public static String trimCategoryName(String name) {
        if (name == null) return "";
        int start = 0, end = name.length();
        while (start < end) {
            int c = name.codePointAt(start);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) break;
            start += Character.charCount(c);
        }
        while (end > start) {
            int c = name.codePointBefore(end);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) break;
            end -= Character.charCount(c);
        }
        return name.substring(start, end);
    }

    public CategoryEntity createCategory(String name, Kind kind) {
        return createCategory(name, kind, CategoryIconCatalog.defaultForName(trimCategoryName(name)));
    }

    public void setCategoryIcon(String id, String iconKey) {
        if (!CategoryIconCatalog.contains(iconKey))
            throw new IllegalArgumentException("Unknown icon");
        db.runInTransaction(() -> {
            CategoryEntity c = requiredCategory(id);
            c.iconKey = iconKey;
            db.categoryDao().update(c);
        });
    }

    public CategoryEntity createCategory(String name, Kind kind, String iconKey) {
        if (!CategoryIconCatalog.contains(iconKey))
            throw new IllegalArgumentException("Unknown icon");
        if (trimCategoryName(name).isEmpty() || trimCategoryName(name).length() > 30 || kind == null)
            throw new IllegalArgumentException("Invalid category name or flow");
        String trimmed = trimCategoryName(name);
        return db.runInTransaction(() -> {
            int lastOrder = -1;
            for (CategoryEntity existing : db.categoryDao().all()) {
                if (!existing.archived && kind.name().equals(existing.flowScope)
                        && trimmed.equalsIgnoreCase(trimCategoryName(existing.name)))
                    throw new DuplicateCategoryName();
                lastOrder = Math.max(lastOrder, existing.sortOrder);
            }
            CategoryEntity category = new CategoryEntity();
            category.id = UUID.randomUUID().toString();
            category.name = trimmed;
            category.flowScope = kind.name();
            category.sortOrder = Math.incrementExact(lastOrder);
            category.archived = false;
            category.iconKey = iconKey;
            db.categoryDao().insert(category);
            return category;
        });
    }

    public List<CategoryRuleEntity> rules() {
        return db.categoryDao().rules();
    }

    public void upsertRule(CategoryRuleEntity r) {
        db.runInTransaction(() -> {
            CategoryEntity c = db.categoryDao().find(r.categoryId);
            if (c == null || c.archived || CategoryMatcher.normalize(r.merchantPattern).isEmpty() || !Arrays.asList("EXACT", "PREFIX", "CONTAINS").contains(r.matchType))
                throw new IllegalArgumentException("Invalid rule");
            CategoryRuleEntity old = db.categoryDao().findRule(r.id);
            r.version = old == null ? 1 : Math.incrementExact(old.version);
            if (old == null) db.categoryDao().insertRule(r);
            else db.categoryDao().updateRule(r);
        });
    }

    public void setRuleEnabled(String id, boolean enabled) {
        db.runInTransaction(() -> {
            CategoryRuleEntity r = requiredRule(id);
            if (enabled && db.categoryDao().find(r.categoryId).archived)
                throw new IllegalArgumentException("Archived category");
            r.enabled = enabled;
            r.version = Math.incrementExact(r.version);
            db.categoryDao().updateRule(r);
        });
    }

    public void renameCategory(String id, String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Empty name");
        db.runInTransaction(() -> {
            CategoryEntity c = requiredCategory(id);
            c.name = name.trim();
            db.categoryDao().update(c);
        });
    }

    public void archiveCategory(String id) {
        if (id.equals(LedgerTypes.UNCATEGORIZED_EXPENSE) || id.equals(LedgerTypes.UNCATEGORIZED_INCOME))
            throw new IllegalArgumentException("Default category cannot be archived");
        db.runInTransaction(() -> {
            CategoryEntity c = requiredCategory(id);
            c.archived = true;
            db.categoryDao().update(c);
            for (CategoryRuleEntity r : db.categoryDao().rules())
                if (r.categoryId.equals(id) && r.enabled) {
                    r.enabled = false;
                    r.version++;
                    db.categoryDao().updateRule(r);
                }
        });
    }

    public Preview previewReclassification(String ruleId) {
        return db.runInTransaction(() -> {
            CategoryRuleEntity rule = requiredRule(ruleId);
            List<CategoryMatcher.Rule> rules = db.categoryDao().ruleRows();
            List<Change> changes = new ArrayList<>();
            for (LedgerTransactionEntity tx : db.ledgerDao().listPosted(Long.MIN_VALUE, Long.MAX_VALUE, null, null, null)) {
                if (locked(tx)) continue;
                var match = matcher.match(Kind.valueOf(tx.kind), tx.merchantRaw, tx.provider, tx.accountScopeId, rules);
                if (ruleId.equals(match.ruleId) && !tx.categoryId.equals(match.categoryId))
                    changes.add(new Change(tx, match.categoryId));
            }
            return new Preview(ruleId, rule.version, changes, signature(rules));
        });
    }

    public ManualLedgerRepository.WriteResult applyReclassification(Preview preview) {
        return db.runInTransaction(() -> {
            CategoryRuleEntity rule = db.categoryDao().findRule(preview.ruleId);
            if (rule == null || rule.version != preview.ruleVersion || !signature(db.categoryDao().ruleRows()).equals(preview.ruleSignature))
                return result(false);
            List<LedgerTransactionEntity> rows = new ArrayList<>();
            for (Change change : preview.changes) {
                LedgerTransactionEntity tx = db.ledgerDao().find(change.transactionId);
                if (tx == null || tx.revision != change.revision || !"POSTED".equals(tx.bookingState) || locked(tx) || !tx.categoryId.equals(change.beforeCategoryId))
                    return result(false);
                rows.add(tx);
            }
            for (int i = 0; i < rows.size(); i++) {
                LedgerTransactionEntity tx = rows.get(i);
                try {
                    tx.recentPreviousValuesJson = new JSONObject().put("amountMinor", tx.amountMinor).put("occurredAtMs", tx.occurredAtMs).put("kind", tx.kind).put("merchant", tx.merchantRaw).put("categoryId", tx.categoryId).toString();
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
                tx.categoryId = preview.changes.get(i).afterCategoryId;
                tx.categoryOrigin = "RULE";
                tx.appliedRuleId = rule.id;
                tx.revision++;
                tx.updatedAt = clock.millis();
                db.ledgerDao().update(tx);
            }
            return result(true);
        });
    }

    private boolean locked(LedgerTransactionEntity tx) {
        if ("USER".equals(tx.categoryOrigin)) return true;
        try {
            JSONArray fields = new JSONArray(tx.userLockedFields);
            for (int i = 0; i < fields.length(); i++)
                if ("category".equals(fields.optString(i))) return true;
            return false;
        } catch (JSONException e) {
            return true;
        }
    }

    private String signature(List<CategoryMatcher.Rule> rules) {
        StringBuilder s = new StringBuilder();
        for (var r : rules)
            s.append(r.id).append(':').append(r.version).append(':').append(r.categoryArchived).append(';');
        return s.toString();
    }

    private CategoryEntity requiredCategory(String id) {
        CategoryEntity c = db.categoryDao().find(id);
        if (c == null) throw new IllegalArgumentException("Missing category");
        return c;
    }

    private CategoryRuleEntity requiredRule(String id) {
        CategoryRuleEntity r = db.categoryDao().findRule(id);
        if (r == null) throw new IllegalArgumentException("Missing rule");
        return r;
    }

    private ManualLedgerRepository.WriteResult result(boolean applied) {
        return new ManualLedgerRepository.WriteResult(applied ? ManualLedgerRepository.WriteResult.Status.APPLIED : ManualLedgerRepository.WriteResult.Status.STALE, 0);
    }
}
