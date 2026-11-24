package com.example.budgetapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public final class LocalSettings {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;

    public LocalSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("local_settings", Context.MODE_PRIVATE);
        for (String provider : new String[]{"WECHAT", "ALIPAY", "OTHER"}) accountScope(provider);
    }

    private void validateProvider(String provider) {
        if (!"WECHAT".equals(provider) && !"ALIPAY".equals(provider) && !"OTHER".equals(provider))
            throw new IllegalArgumentException("Unsupported provider");
    }

    public String accountScope(String provider) {
        validateProvider(provider);
        synchronized (LOCK) {
            String id = prefs.getString("scope." + provider, null);
            if (id == null) {
                id = UUID.randomUUID().toString();
                persist(prefs.edit().putString("scope." + provider, id).putString("alias." + id, provider));
            }
            return id;
        }
    }

    public String replaceAccountScope(String provider, String displayName) {
        validateProvider(provider);
        if (displayName == null || displayName.trim().isEmpty())
            throw new IllegalArgumentException("Missing alias");
        synchronized (LOCK) {
            String id = UUID.randomUUID().toString();
            persist(prefs.edit().putString("scope." + provider, id).putString("alias." + id, displayName));
            return id;
        }
    }

    public int retentionDays() {
        return prefs.getInt("retention_days", 7);
    }

    public void setRetentionDays(int days) {
        if (days != 7 && days != 30) throw new IllegalArgumentException("Unsupported retention");
        persist(prefs.edit().putInt("retention_days", days));
    }

    public long openingBalanceFen() {
        return prefs.getLong("opening_balance_fen", 0L);
    }

    public void setOpeningBalanceFen(long amount) {
        persist(prefs.edit().putLong("opening_balance_fen", amount));
    }

    public androidx.lifecycle.LiveData<Long> observeOpeningBalanceFen() {
        return new androidx.lifecycle.LiveData<Long>() {
            private final SharedPreferences.OnSharedPreferenceChangeListener listener = (preferences, key) -> {
                if (key == null || "opening_balance_fen".equals(key))
                    postValue(openingBalanceFen());
            };

            @Override
            protected void onActive() {
                prefs.registerOnSharedPreferenceChangeListener(listener);
                setValue(openingBalanceFen());
            }

            @Override
            protected void onInactive() {
                prefs.unregisterOnSharedPreferenceChangeListener(listener);
            }
        };
    }

    private void persist(SharedPreferences.Editor editor) {
        if (!editor.commit()) throw new IllegalStateException("Settings could not be persisted");
    }
}
