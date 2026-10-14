package com.geekopolis.fordmonitor;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import com.geekopolis.fordmonitor.core.MisfireHistory;
import java.util.*;

/** Two aligned time lanes keep percent load and new event counts on distinct scales. */
final class LoadMisfireView extends View {
    private final MisfireHistory history;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int cylinder=5;
    private boolean running;
    private static final int TEAL=Color.rgb(109,227,203),ORANGE=Color.rgb(255,190,107),MUTED=Color.rgb(165,181,195);
    LoadMisfireView(Context context,MisfireHistory history) {super(context);this.history=history;setMinimumHeight(dp(245));setContentDescription("Engine load and selected-cylinder misfire increases over time");}
    void update(int cylinder,boolean running) {this.cylinder=cylinder;this.running=running;invalidate();}
    private int dp(int n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private void label(Canvas c,String text,float x,float y,int color) {paint.setColor(color);paint.setTextSize(dp(11));paint.setStyle(Paint.Style.FILL);c.drawText(text,x,y,paint);}
    private float x(long time,long start,long end,float left,float width){return left+width*(time-start)/(float)(end-start);}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left=dp(38), right=getWidth()-dp(12), width=right-left;
        if(width<=0)return;
        long last=history.latest();
        if(last<0){label(canvas,"Connect to collect load and cycle-count changes",left,dp(60),MUTED);return;}
        long end=running?Math.max(last,SystemClock.elapsedRealtime()):last;
        long duration=Math.max(60_000,Math.min(300_000,end-history.first()));long start=end-duration;
        float loadTop=dp(30),loadBottom=dp(125),misTop=dp(162),misBottom=dp(215);
        label(canvas,"Load %",left,dp(17),TEAL);
        label(canvas,"New count increases · cylinder "+cylinder,left,dp(149),ORANGE);
        paint.setStrokeWidth(dp(1));
        for(int pct:new int[]{0,50,100}) {
            float y=loadBottom-(loadBottom-loadTop)*pct/100;
            paint.setColor(Color.rgb(51,66,79));canvas.drawLine(left,y,right,y,paint);
            label(canvas,Integer.toString(pct),dp(5),y+dp(4),MUTED);
        }
        List<MisfireHistory.Change> points=history.changes(cylinder);
        int max=1;boolean baseline=false;
        for(var p:points)if(p.time()>=start && p.time()<=end){if(p.increase()!=null){baseline=true;max=Math.max(max,p.increase());}}
        label(canvas,Integer.toString(max),dp(5),misTop+dp(4),MUTED);label(canvas,"0",dp(5),misBottom+dp(4),MUTED);
        paint.setColor(Color.rgb(51,66,79));canvas.drawLine(left,misBottom,right,misBottom,paint);
        int save=canvas.save();canvas.clipRect(left,loadTop,right,misBottom+dp(1));
        Path path=new Path();MisfireHistory.Load previous=null;
        for(var sample:history.loads()) {
            if(sample.time()<start){previous=sample;continue;}if(sample.time()>end)break;
            if(sample.percent()==null){previous=null;continue;}
            float sx=x(sample.time(),start,end,left,width),sy=loadBottom-(loadBottom-loadTop)*sample.percent().floatValue()/100;
            if(previous!=null && previous.percent()!=null && sample.time()-previous.time()<=5000) {
                path.moveTo(x(previous.time(),start,end,left,width),loadBottom-(loadBottom-loadTop)*previous.percent().floatValue()/100);path.lineTo(sx,sy);
            } else {paint.setColor(TEAL);canvas.drawCircle(sx,sy,dp(2),paint);}
            previous=sample;
        }
        paint.setColor(TEAL);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        for(var point:points) {
            if(point.time()<start || point.time()>end)continue;
            float a=x(Math.max(start,point.from()),start,end,left,width),b=x(point.time(),start,end,left,width);
            if(point.increase()==null){paint.setColor(MUTED);canvas.drawCircle(b,misBottom,dp(2),paint);continue;}
            if(point.increase()>0) {
                paint.setColor(ORANGE);paint.setAlpha(150);
                float top=misBottom-(misBottom-misTop)*point.increase()/max;
                canvas.drawRect(a,top,Math.max(a+dp(2),b),misBottom,paint);paint.setAlpha(255);
            }
        }
        canvas.restoreToCount(save);
        label(canvas,"-"+(duration/1000)+"s",left,dp(235),MUTED);
        String latestText=running?"now":"last session";label(canvas,latestText,right-dp(running?25:67),dp(235),MUTED);
        if(!baseline)label(canvas,"Waiting for a second cycle-count reading",left,misTop+dp(28),MUTED);
    }
}
