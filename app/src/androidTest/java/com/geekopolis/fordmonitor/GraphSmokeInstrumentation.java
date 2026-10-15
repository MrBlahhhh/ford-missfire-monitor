package com.geekopolis.fordmonitor;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import com.geekopolis.fordmonitor.core.*;

/** Emulator-only fixtures, with no Bluetooth connection or shipped demo entry point. */
public final class GraphSmokeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private Object field(Activity a,String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private void render(Activity a)throws Exception{Method m=MainActivity.class.getDeclaredMethod("render");m.setAccessible(true);m.invoke(a);}
    private void snapshot(LoadMisfireView view,String name)throws IOException{
        int height=Math.round(view.heightDp()*getTargetContext().getResources().getDisplayMetrics().density);
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(height,android.view.View.MeasureSpec.EXACTLY));view.layout(0,0,1080,height);
        Bitmap b=Bitmap.createBitmap(1080,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);canvas.drawColor(Color.rgb(27,39,51));view.draw(canvas);
        try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    private void profile(Activity activity,int index,int count){
        runOnMainSync(()->{try{((Spinner)field(activity,"profileSpinner")).setSelection(index);((Spinner)field(activity,"graphCylinder")).setSelection(0);}catch(Exception e){throw new RuntimeException(e);}});
        waitForIdleSync();
        runOnMainSync(()->{
            try{
                MisfireHistory h=(MisfireHistory)field(activity,"history");h.clear();long end=SystemClock.elapsedRealtime();
                for(int i=0;i<=60;i++)h.load(end-60000+i*1000,20.0+55*Math.pow(Math.sin(i*Math.PI/60),2));
                for(int c=1;c<=count;c++){h.count(c,end-60000,c*10);h.count(c,end-45000,c*10);h.count(c,end-30000,c*10+(c+1)/2);h.count(c,end-15000,c*10+c);h.count(c,end,c*10+c);}
                render(activity);LoadMisfireView view=(LoadMisfireView)field(activity,"graph");view.update(0,count,false);snapshot(view,"graph-all"+count+".png");
                String summary=((TextView)field(activity,"graphSummary")).getText().toString();
                if(!summary.contains("C"+count+" +"+count)||!summary.contains("C5 +5"))throw new AssertionError("Missing all-cylinder totals: "+summary);
                if(h.observedIncrease(count)!=count)throw new AssertionError("Incorrect per-cylinder delta");
                view.update(5,count,false);snapshot(view,"graph-detail.png");
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Activity a=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            profile(a,0,8);profile(a,1,10);
            result.putString("result","PASS: V8/V10 all-cylinder graphs, C9/C10 totals, profile switch and single-cylinder detail");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}
    }
}
