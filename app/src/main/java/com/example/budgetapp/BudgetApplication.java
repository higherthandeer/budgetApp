package com.example.budgetapp;

import android.app.Application;

import java.time.Clock;

public final class BudgetApplication extends Application {
    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this, Clock.systemUTC());
        container.recover();
        container.ensureNotificationConnection(this);
    }

    public AppContainer container() {
        return container;
    }
}
