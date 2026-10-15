package com.geekopolis.fordmonitor;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import com.geekopolis.fordmonitor.core.MisfireHistory;
import java.util.*;

/** Shared time/scale, separate cylinder lanes: simultaneous visibility without overlapping traces. */
final class LoadMisfireView extends View {
    private final MisfireHistory history;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int cylinder=0,cylinderCount=8;
    private boolean running;
    private static final int TEAL=Color.rgb(109,227,203),ORANGE=Color.rgb(255,190,107),MUTED=Color.rgb(165,181,195),GRID=Color.rgb(51,66,79);
    private static final int[] COLORS={0xFF76B7FF,0xFFFFC170,0xFFDD96FF,0xFF8CDB8E,0xFFFF8787,0xFF7DD8F2,0xFFFF95D0,0xFFD9D47B,0xFF9BAAFF,0xFFAED3BC};
    LoadMisfireView(Context context,MisfireHistory history){super(context);this.history=history;setMinimumHeight(dp(245));}
    void update(int cylinder,boolean running){update(cylinder,cylinderCount,running);}
    void update(int cylinder,int count,boolean running){
        this.cylinder=cylinder;this.cylinderCount=count;this.running=running;
        setContentDescription(cylinder==0?"Engine load and all "+count+" cylinder misfire increase lanes":"Engine load and cylinder "+cylinder+" misfire increases");invalidate();
    }
    int heightDp(){return cylinder==0?175+28*cylinderCount:255;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void label(Canvas c,String text,float x,float y,int color){paint.setColor(color);paint.setTextSize(dp(11));paint.setStyle(Paint.Style.FILL);c.drawText(text,x,y,paint);}
    private float x(long time,long start,long end,float left,float width){return left+width*(time-start)/(float)(end-start);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);boolean all=cylinder==0;
        float left=dp(all?34:38),right=getWidth()-dp(all?37:12),width=right-left;if(width<=0)return;
        long last=history.latest();
        long end=last<0?0:running?Math.max(last,SystemClock.elapsedRealtime()):last;
        long duration=last<0?60000:Math.max(60000,Math.min(300000,end-history.first())),start=end-duration;
        float loadTop=dp(30),loadBottom=dp(all?100:125);
        label(canvas,"Load %",left,dp(17),TEAL);paint.setStrokeWidth(dp(1));
        for(int pct:new int[]{0,50,100}){float y=loadBottom-(loadBottom-loadTop)*pct/100;paint.setColor(GRID);canvas.drawLine(left,y,right,y,paint);label(canvas,Integer.toString(pct),dp(3),y+dp(4),MUTED);}
        int first=all?1:cylinder,lastCylinder=all?cylinderCount:cylinder,max=1;
        for(int c=first;c<=lastCylinder;c++)for(var p:history.changes(c))if(p.time()>=start && p.time()<=end && p.increase()!=null)max=Math.max(max,p.increase());
        label(canvas,all?"New increases per read · scale 0–"+max:"New count increases · cylinder "+cylinder,left,dp(all?125:149),all?MUTED:ORANGE);
        int save=canvas.save();canvas.clipRect(left,loadTop,right,loadBottom+dp(2));Path path=new Path();MisfireHistory.Load previous=null;
        for(var sample:history.loads()){
            if(sample.time()<start){previous=sample;continue;}if(sample.time()>end)break;
            if(sample.percent()==null){previous=null;continue;}
            float sx=x(sample.time(),start,end,left,width),sy=loadBottom-(loadBottom-loadTop)*sample.percent().floatValue()/100;
            if(previous!=null && previous.percent()!=null && sample.time()-previous.time()<=5000){path.moveTo(x(previous.time(),start,end,left,width),loadBottom-(loadBottom-loadTop)*previous.percent().floatValue()/100);path.lineTo(sx,sy);}
            else{paint.setColor(TEAL);canvas.drawCircle(sx,sy,dp(2),paint);}previous=sample;
        }
        paint.setColor(TEAL);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);canvas.restoreToCount(save);
        float finalBottom=0;
        for(int c=first;c<=lastCylinder;c++){
            float top=dp(all?145+(c-1)*28:162),bottom=dp(all?165+(c-1)*28:215);finalBottom=bottom;
            int color=all?COLORS[c-1]:ORANGE;List<MisfireHistory.Change> points=history.changes(c);
            paint.setColor(GRID);paint.setStrokeWidth(dp(1));canvas.drawLine(left,bottom,right,bottom,paint);
            if(all){label(canvas,"C"+c,dp(3),bottom-dp(4),color);String total=points.isEmpty()?"—":"+"+history.observedIncrease(c);label(canvas,total,right+dp(4),bottom-dp(4),MUTED);}
            else{label(canvas,Integer.toString(max),dp(3),top+dp(4),MUTED);label(canvas,"0",dp(3),bottom+dp(4),MUTED);}
            boolean baseline=false;int clipped=canvas.save();canvas.clipRect(left,top,right,bottom+dp(2));
            for(var point:points){
                if(point.time()<start || point.time()>end)continue;
                float a=x(Math.max(start,point.from()),start,end,left,width),b=x(point.time(),start,end,left,width);
                if(point.increase()==null){paint.setColor(MUTED);canvas.drawCircle(b,bottom,dp(2),paint);continue;}baseline=true;
                if(point.increase()>0){paint.setColor(color);paint.setAlpha(180);canvas.drawRect(a,bottom-(bottom-top)*point.increase()/max,Math.max(a+dp(2),b),bottom,paint);paint.setAlpha(255);}
                else{paint.setColor(color);canvas.drawCircle(b,bottom,dp(1),paint);}
            }
            canvas.restoreToCount(clipped);
            if(!baseline){label(canvas,points.isEmpty()?"Waiting for count readings":"Waiting for next count",left,top+dp(all?12:28),MUTED);}
        }
        label(canvas,"-"+(duration/1000)+"s",left,finalBottom+dp(20),MUTED);
        String current=running?"now":"last session";label(canvas,current,right-dp(running?25:67),finalBottom+dp(20),MUTED);
        if(last<0)label(canvas,"Connect to start the graph",left,loadTop+dp(35),MUTED);
    }
}
