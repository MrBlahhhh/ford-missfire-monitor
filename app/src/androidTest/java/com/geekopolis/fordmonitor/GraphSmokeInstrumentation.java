package com.geekopolis.fordmonitor;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import com.geekopolis.fordmonitor.core.*;

/** Emulator-only visual smoke harness; no Bluetooth connection and no shipped demo entry point. */
public final class GraphSmokeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            runOnMainSync(()->{
                try {
                    Field field=MainActivity.class.getDeclaredField("history");field.setAccessible(true);
                    MisfireHistory h=(MisfireHistory)field.get(activity);h.clear();long end=SystemClock.elapsedRealtime();
                    for(int i=0;i<=60;i++)h.load(end-60000+i*1000,20.0+55*Math.sin(i*Math.PI/60)*Math.sin(i*Math.PI/60));
                    h.count(5,end-60000,0);h.count(5,end-45000,0);h.count(5,end-30000,3);h.count(5,end-15000,5);h.count(5,end,5);
                    Field graphField=MainActivity.class.getDeclaredField("graph");graphField.setAccessible(true);
                    LoadMisfireView view=(LoadMisfireView)graphField.get(activity);view.update(5,false);
                    view.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(765,android.view.View.MeasureSpec.EXACTLY));
                    view.layout(0,0,1080,765);
                    Bitmap bitmap=Bitmap.createBitmap(1080,765,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.rgb(27,39,51));view.draw(canvas);
                    try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"graph-preview.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
                    if(h.observedIncrease(5)!=5)throw new AssertionError("Incorrect graph delta");
                    Field summaryField=MainActivity.class.getDeclaredField("graphSummary");summaryField.setAccessible(true);
                    Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
                    if(!((TextView)summaryField.get(activity)).getText().toString().contains("5"))throw new AssertionError("Graph summary missing count");
                }catch(Exception e){throw new RuntimeException(e);}
            });
            result.putString("result","PASS: graph draw, summary and baseline/delta smoke check");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}
    }
}
