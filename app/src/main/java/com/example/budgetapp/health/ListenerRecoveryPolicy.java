package com.example.budgetapp.health;

/** Finite, event-triggered recovery; no periodic keep-alive loop. */
public final class ListenerRecoveryPolicy {
    public interface Port { boolean authorized(); boolean connected(); void request(); void exhausted(); }
    public enum Outcome { SUCCESS, RETRY, EXHAUSTED }
    public static Outcome attempt(Port port,int attempt){
        if(!port.authorized()||port.connected())return Outcome.SUCCESS;
        try{port.request();}catch(RuntimeException unavailable){/* No payloads or exception messages logged. */}
        if(!port.authorized()||port.connected())return Outcome.SUCCESS;
        if(attempt>=4){port.exhausted();return Outcome.EXHAUSTED;}
        return Outcome.RETRY;
    }
    private ListenerRecoveryPolicy(){}
}
