package com.example.budgetapp.ui.settings;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.content.pm.*;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.example.budgetapp.*;
import com.example.budgetapp.capture.notification.ObservationSession;
import com.example.budgetapp.health.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class SettingsFragment extends Fragment {
 private AppContainer app;
 private android.view.View advancedView;
 private long renderGeneration;
 public SettingsFragment(){super(R.layout.fragment_settings);}
 @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){app=((BudgetApplication)requireActivity().getApplication()).container();render();app.notificationReplay.observe(getViewLifecycleOwner(),report->{if(advancedView!=null)updateReplayControls(advancedView);});app.notificationHealth.observe(getViewLifecycleOwner(),health->{if(getView()!=null&&health!=null)updateHealthControls(getView(),health);});}
 @Override public void onResume(){super.onResume();if(app!=null)render();}
 private String time(Long millis){return millis==null?getString(R.string.settings_no_time):DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(com.example.budgetapp.processing.TimeWindows.LEDGER_ZONE).format(Instant.ofEpochMilli(millis));}
 private void render(){View page=getView();if(page==null)return;long generation=++renderGeneration;app.health.onAuthorization(ObservationSession.isAuthorized(requireContext()));app.io.execute(()->{int pending=app.db.sourceEventDao().pendingCount();var rawItems=app.retention.listAvailable();int retentionDays=app.settings.retentionDays();app.health.onBacklog(pending,app.health.snapshot().errorCode);app.main.execute(()->{if(getView()!=page||generation!=renderGeneration)return;NotificationHealth h=app.health.snapshot();LinearLayout content=page.findViewById(R.id.settings_content);content.removeAllViews();
  text(content,"采集与设置",25);
  LinearLayout privacy=card(content);privacy.setBackgroundResource(R.drawable.obs_mint_tile);text(privacy,"安心记在本机",18);text(privacy,"只记录需要的收支信息\n不上传账单，不需要支付密码",12);
  text(content,"自动记账",12);LinearLayout capture=card(content);healthControls(capture,h);
  capture.findViewById(R.id.settings_listener_permission).setOnClickListener(v->startActivity(new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
  button(capture,R.string.settings_refresh,()->{app.requestRebind(requireContext());app.recover();render();});
  text(content,"通知采集可能遗漏部分交易，漏记可手动添加。\n关闭采集后，已有账单和统计仍可正常使用。",12);
  text(content,"记账习惯",12);LinearLayout habits=card(content);button(habits,R.string.category_manage,()->((MainActivity)requireActivity()).showCategoryRules());
  button(habits,R.string.opening_balance_title,this::editOpeningBalance);text(content,"采集状态",12);LinearLayout status=card(content);text(status,"最近收到交易通知\n"+time(h.lastEventAtMs),13);text(status,"本地账本                     本机保存\n人民币 · 中国标准时间",13);
  Button advanced=new com.google.android.material.button.MaterialButton(requireContext(),null,com.google.android.material.R.attr.borderlessButtonStyle);advanced.setTextSize(13);advanced.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);advanced.setBackgroundResource(R.drawable.obs_card);advanced.setId(R.id.settings_advanced);advanced.setText("高级设置  ›");content.addView(advanced);advanced.setOnClickListener(v->{
   com.google.android.material.bottomsheet.BottomSheetDialog sheet=new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());ScrollView scroll=new ScrollView(requireContext());LinearLayout details=new LinearLayout(requireContext());details.setOrientation(LinearLayout.VERTICAL);details.setPadding(dp(21),dp(21),dp(21),dp(32));scroll.addView(details);sheet.setContentView(scroll);
   text(details,"高级设置",22);advancedView=details;sheet.setOnDismissListener(d->advancedView=null);
replayControls(details);button(details,R.string.settings_observe,()->{sheet.dismiss();((MainActivity)requireActivity()).showObservation();});button(details,R.string.category_manage,()->{sheet.dismiss();((MainActivity)requireActivity()).showCategoryRules();});for(String provider:new String[]{"WECHAT","ALIPAY","OTHER"}){String label=getString(provider.equals("WECHAT")?R.string.platform_wechat:provider.equals("ALIPAY")?R.string.platform_alipay:R.string.entry_other);String scope=app.settings.accountScope(provider);text(details,getString(R.string.settings_scope,label,scope.substring(0,8)),14);button(details,R.string.settings_scope_change,()->{EditText name=new EditText(requireContext());name.setText(label);new AlertDialog.Builder(requireContext()).setMessage(R.string.settings_scope_explain).setView(name).setNegativeButton(R.string.entry_cancel,null).setPositiveButton(R.string.category_apply,(dialog,which)->app.io.execute(()->{try{app.settings.replaceAccountScope(provider,name.getText().toString());app.main.execute(()->{if(isAdded())render();});}catch(IllegalArgumentException invalid){app.main.execute(()->{if(isAdded())Toast.makeText(requireContext(),R.string.review_error,Toast.LENGTH_LONG).show();});}})).show();});if(!provider.equals("OTHER")){String version;try{version=requireContext().getPackageManager().getPackageInfo(provider.equals("WECHAT")?"com.tencent.mm":"com.eg.android.AlipayGphone",0).versionName;}catch(PackageManager.NameNotFoundException missing){version=getString(R.string.settings_missing_app);}text(details,getString(R.string.settings_version,label,version),14);}}text(details,getString(R.string.settings_manual_notice),14);TextView retention=new TextView(requireContext());retention.setText(getString(R.string.retention_current,app.settings.retentionDays()));details.addView(retention);text(details,getString(R.string.retention_explanation),14);button(details,R.string.retention_seven,()->retentionAction(()->{app.retention.purgeExpired();app.settings.setRetentionDays(7);app.main.execute(()->retention.setText(getString(R.string.retention_current,7)));}));button(details,R.string.retention_thirty,()->retentionAction(()->{app.retention.purgeExpired();app.settings.setRetentionDays(30);app.main.execute(()->retention.setText(getString(R.string.retention_current,30)));}));for(var item:rawItems){String eventId=item.eventId;text(details,getString(R.string.retention_item,time(item.capturedAtMs),time(item.expiresAtMs)),14);button(details,R.string.retention_extend,()->retentionAction(()->app.retention.extendEvent(eventId,30)));button(details,R.string.retention_clear,()->retentionAction(()->app.retention.clearRaw(eventId)));}
   sheet.show();
  });
 });});}
 private void editOpeningBalance(){
  EditText input=new EditText(requireContext());input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);input.setText(com.example.budgetapp.processing.Money.formatFen(app.settings.openingBalanceFen()));input.setSelectAllOnFocus(true);
  AlertDialog dialog=new AlertDialog.Builder(requireContext()).setTitle(R.string.opening_balance_title).setMessage(R.string.opening_balance_explanation).setView(input).setNegativeButton(R.string.entry_cancel,null).setPositiveButton(R.string.category_apply,null).create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   try{String value=input.getText().toString().trim();if(!value.matches("-?[0-9]+(?:\\.[0-9]{1,2})?"))throw new IllegalArgumentException();long amount=new java.math.BigDecimal(value).movePointRight(2).longValueExact();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    app.io.execute(()->{try{app.settings.setOpeningBalanceFen(amount);app.main.execute(()->{if(isAdded()){dialog.dismiss();render();}});}catch(RuntimeException failure){app.main.execute(()->{if(isAdded()){dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);input.setError(getString(R.string.entry_failed));}});}});
   }catch(RuntimeException invalid){input.setError(getString(R.string.entry_invalid));}
  }));dialog.show();
 }
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private LinearLayout card(LinearLayout parent){LinearLayout card=new LinearLayout(requireContext());card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(6),dp(16),dp(6));card.setBackgroundResource(R.drawable.obs_card);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);parent.addView(card,lp);return card;}
 private void healthControls(LinearLayout content,NotificationHealth health){
  for(int id:new int[]{R.id.settings_listener_permission,R.id.settings_listener_connection,R.id.settings_listener_history,R.id.settings_listener_error}){TextView view=new TextView(requireContext());view.setId(id);view.setTextSize(13);view.setTextColor(requireContext().getColor(R.color.obs_ink));view.setPadding(0,dp(14),0,dp(14));view.setMinHeight(dp(48));content.addView(view);}
  content.findViewById(R.id.settings_listener_history).setVisibility(View.GONE);updateHealthControls(content,health);
 }
 private void updateHealthControls(View root,NotificationHealth health){
  TextView permission=root.findViewById(R.id.settings_listener_permission),connection=root.findViewById(R.id.settings_listener_connection),history=root.findViewById(R.id.settings_listener_history),error=root.findViewById(R.id.settings_listener_error);if(permission==null||connection==null||history==null||error==null)return;
  permission.setText(getString(R.string.settings_permission,getString(health.authorized?R.string.permission_on:R.string.permission_off)));
  int label=health.connection==NotificationHealth.Connection.CONNECTED?R.string.service_connected:health.connection==NotificationHealth.Connection.CONNECTING?R.string.settings_connecting:health.connection==NotificationHealth.Connection.DISCONNECTED?R.string.settings_disconnected:R.string.settings_unknown;connection.setText(getString(R.string.settings_connection,getString(label)));
  history.setText(getString(R.string.settings_history,time(health.lastConnectedAtMs),time(health.lastEventAtMs),time(health.lastParsedAtMs),health.pendingCount));
  error.setVisibility(health.errorCode==null?View.GONE:View.VISIBLE);error.setText("LISTENER_RECONNECT_EXHAUSTED".equals(health.errorCode)||"LISTENER_REBIND_FAILED".equals(health.errorCode)?R.string.settings_reconnect_failed:R.string.settings_error);
 }
 private void replayControls(LinearLayout content){
  Button replay=new Button(requireContext());replay.setId(R.id.notification_replay_button);replay.setText(R.string.notification_replay_button);replay.setOnClickListener(v->app.replayCurrentNotifications(requireContext()));content.addView(replay);
  TextView result=new TextView(requireContext());result.setId(R.id.notification_replay_result);result.setPadding(0,8,0,16);content.addView(result);updateReplayControls(content);
 }
 private void updateReplayControls(View root){
  Button replay=root.findViewById(R.id.notification_replay_button);TextView result=root.findViewById(R.id.notification_replay_result);if(replay==null||result==null)return;
  var report=app.notificationReplay.getValue();replay.setEnabled(report==null||report.state!=com.example.budgetapp.capture.notification.CurrentNotificationReplay.State.RUNNING);
  int message=R.string.notification_replay_help;
  if(report!=null)switch(report.state){case RUNNING:message=R.string.notification_replay_running;break;case NO_PERMISSION:message=R.string.notification_replay_permission;break;case NOT_CONNECTED:message=R.string.notification_replay_disconnected;break;case FAILED:message=R.string.notification_replay_failed;break;case COMPLETE:message=report.seen==0?R.string.notification_replay_empty:0;break;default:break;}
  result.setText(message==0?getString(R.string.notification_replay_summary,report.seen,report.posted,report.linked,report.review,report.ignored,report.unchanged,report.errors):getString(message));
 }
 private void retentionAction(Runnable action){app.io.execute(()->{try{action.run();app.main.execute(()->{if(isAdded())render();});}catch(RuntimeException failure){app.main.execute(()->{if(isAdded())Toast.makeText(requireContext(),R.string.retention_unavailable,Toast.LENGTH_LONG).show();});}});}
 private void text(LinearLayout parent,String value,int size){TextView label=new TextView(requireContext());label.setText(value);label.setTextSize(size);label.setTextColor(requireContext().getColor(size>=18?R.color.obs_ink:R.color.obs_muted));label.setPadding(0,16,0,16);parent.addView(label);}
 private void button(LinearLayout parent,int label,Runnable action){Button button=new com.google.android.material.button.MaterialButton(requireContext(),null,com.google.android.material.R.attr.borderlessButtonStyle);button.setTextSize(13);button.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);button.setText(label);button.setOnClickListener(v->action.run());parent.addView(button);}
}
