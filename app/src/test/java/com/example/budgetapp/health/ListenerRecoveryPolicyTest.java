package com.example.budgetapp.health;
import org.junit.Test;
import static org.junit.Assert.*;
public class ListenerRecoveryPolicyTest {
 private static final class Port implements ListenerRecoveryPolicy.Port {boolean allowed=true,connected,throwsError;int calls,exhausted;public boolean authorized(){return allowed;}public boolean connected(){return connected;}public void request(){calls++;if(throwsError)throw new IllegalStateException();}public void exhausted(){exhausted++;}}
 @Test public void retriesAreFiniteAndExceptionsDoNotEscape(){var p=new Port();p.throwsError=true;for(int i=0;i<4;i++)assertEquals(ListenerRecoveryPolicy.Outcome.RETRY,ListenerRecoveryPolicy.attempt(p,i));assertEquals(ListenerRecoveryPolicy.Outcome.EXHAUSTED,ListenerRecoveryPolicy.attempt(p,4));assertEquals(5,p.calls);assertEquals(1,p.exhausted);}
 @Test public void permissionAndConnectionStopRequests(){var p=new Port();p.allowed=false;assertEquals(ListenerRecoveryPolicy.Outcome.SUCCESS,ListenerRecoveryPolicy.attempt(p,0));p.allowed=true;p.connected=true;assertEquals(ListenerRecoveryPolicy.Outcome.SUCCESS,ListenerRecoveryPolicy.attempt(p,0));assertEquals(0,p.calls);}
 @Test public void actualConnectionAfterRequestIsSuccess(){var p=new ListenerRecoveryPolicy.Port(){boolean connected;public boolean authorized(){return true;}public boolean connected(){return connected;}public void request(){connected=true;}public void exhausted(){fail();}};assertEquals(ListenerRecoveryPolicy.Outcome.SUCCESS,ListenerRecoveryPolicy.attempt(p,0));}
}
