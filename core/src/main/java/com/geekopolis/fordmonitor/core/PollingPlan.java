package com.geekopolis.fordmonitor.core;

/** A zero focus requests one complete sweep; positive focus retains the single-cylinder fast view. */
public final class PollingPlan {
    private PollingPlan() {}
    public static int[] cylinders(int count,int focus,int next) {
        if((count!=8 && count!=10) || focus<0 || focus>count || next<1 || next>count)throw new IllegalArgumentException("Invalid polling profile/focus");
        if(focus==0){int[] out=new int[count];for(int i=0;i<count;i++)out[i]=i+1;return out;}
        return focus==next?new int[]{next}:new int[]{next,focus};
    }
}
