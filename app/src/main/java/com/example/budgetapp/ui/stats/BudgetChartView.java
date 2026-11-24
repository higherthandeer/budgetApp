package com.example.budgetapp.ui.stats;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.*;
import com.example.budgetapp.R;
import com.example.budgetapp.processing.LedgerFilter;
import com.example.budgetapp.processing.Money;
import java.util.*;
/** Financial values arrive precomputed; floating point is used only for drawing. */
public final class BudgetChartView extends View {
 public static final class ChartItem {public final String label;public final long valueFen;public final LedgerFilter filter;public ChartItem(String label,long valueFen,LedgerFilter filter){this.label=label;this.valueFen=valueFen;this.filter=filter;}}
 public interface OnItemSelectedListener {void onSelect(LedgerFilter filter);}
 private List<ChartItem> items=Collections.emptyList();private final List<RectF> bars=new ArrayList<>();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private OnItemSelectedListener listener;private int pressed=-1,selected=-1;private float zero;
 public BudgetChartView(Context context){this(context,null);}
 public BudgetChartView(Context context,AttributeSet attrs){super(context,attrs);setClickable(true);setFocusable(true);setContentDescription(context.getString(R.string.stats_chart_description));}
 public void setItems(List<ChartItem> items,OnItemSelectedListener listener){this.items=Collections.unmodifiableList(new ArrayList<>(items));this.listener=listener;selected=-1;pressed=-1;geometry();invalidate();}
 private float dp(float value){return value*getResources().getDisplayMetrics().density;}
 private void geometry(){bars.clear();if(items.isEmpty()||getWidth()==0||getHeight()==0)return;float left=dp(16),right=getWidth()-dp(16),top=dp(24),bottom=getHeight()-dp(28);if(right<=left||bottom<=top)return;double maximum=0;boolean positive=false,negative=false;for(var item:items){maximum=Math.max(maximum,Math.abs((double)item.valueFen));positive|=item.valueFen>0;negative|=item.valueFen<0;}zero=negative?(positive?(top+bottom)/2:top):bottom;float available=positive&&negative?(bottom-top)/2:bottom-top;float cell=(right-left)/items.size();for(int i=0;i<items.size();i++){ChartItem item=items.get(i);float height=maximum==0?0:(float)(Math.abs((double)item.valueFen)/maximum*available);height=item.valueFen==0?0:Math.max(dp(2),height);float center=left+cell*(i+.5f);bars.add(item.valueFen<0?new RectF(center-cell*.22f,zero,center+cell*.22f,Math.min(bottom,zero+height)):new RectF(center-cell*.22f,Math.max(top,zero-height),center+cell*.22f,zero));}}
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){geometry();}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);paint.setColor(getContext().getColor(R.color.obs_green));paint.setStrokeWidth(dp(1));canvas.drawLine(dp(16),zero,getWidth()-dp(16),zero,paint);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);for(int i=0;i<bars.size();i++){RectF bar=bars.get(i);paint.setAlpha(i==selected?255:180);canvas.drawRoundRect(bar,dp(4),dp(4),paint);paint.setAlpha(255);canvas.drawText(items.get(i).label,bar.centerX(),getHeight()-dp(8),paint);if(i==selected)canvas.drawText(Money.formatFen(items.get(i).valueFen),bar.centerX(),Math.max(dp(16),bar.top-dp(5)),paint);}}
 int hitTest(float x,float y){for(int i=0;i<bars.size();i++)if(bars.get(i).contains(x,y))return i;return -1;}
 @Override public boolean onTouchEvent(MotionEvent event){if(!isEnabled())return false;if(event.getAction()==MotionEvent.ACTION_DOWN){pressed=hitTest(event.getX(),event.getY());return true;}if(event.getAction()==MotionEvent.ACTION_UP){int index=hitTest(event.getX(),event.getY());if(index>=0&&index==pressed){selected=index;performClick();if(listener!=null)listener.onSelect(items.get(index).filter);invalidate();}pressed=-1;return true;}if(event.getAction()==MotionEvent.ACTION_CANCEL){pressed=-1;return true;}return true;}
 @Override public boolean performClick(){super.performClick();return true;}
}
