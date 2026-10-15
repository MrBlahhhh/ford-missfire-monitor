package com.geekopolis.fordmonitor.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class AllCylinderTest {
    @Test public void allV8PolledOncePerSweep(){assertArrayEquals(new int[]{1,2,3,4,5,6,7,8},PollingPlan.cylinders(8,0,1));}
    @Test public void allV10Including9And10(){assertArrayEquals(new int[]{1,2,3,4,5,6,7,8,9,10},PollingPlan.cylinders(10,0,10));}
    @Test public void selectedRetainsFastPoll(){assertArrayEquals(new int[]{1,5},PollingPlan.cylinders(8,5,1));}
    @Test public void focusDoesNotDoublePoll(){assertArrayEquals(new int[]{5},PollingPlan.cylinders(8,5,5));}
    @Test(expected=IllegalArgumentException.class) public void v8DoesNotPollCylinder10(){PollingPlan.cylinders(8,10,1);}
    @Test(expected=IllegalArgumentException.class) public void badProfileRejected(){PollingPlan.cylinders(6,0,1);}
    @Test public void allTenBaselinesIndependent(){var h=new MisfireHistory();for(int c=1;c<=10;c++){h.count(c,1000,c*10);h.count(c,2000,c*10+c);}for(int c=1;c<=10;c++)assertEquals(c,h.observedIncrease(c));}
    @Test public void resetOneCylinderDoesNotResetOthers(){var h=new MisfireHistory();for(int c=1;c<=10;c++)h.count(c,1000,20);h.count(5,2000,0);h.count(10,2000,23);assertEquals(0,h.observedIncrease(5));assertEquals(3,h.observedIncrease(10));}
    @Test public void missingOneDoesNotHideOtherChanges(){var h=new MisfireHistory();h.count(1,1000,0);h.count(2,1000,0);h.unavailable(1,2000);h.count(2,2000,4);assertFalse(h.hasBaseline(1));assertTrue(h.hasBaseline(2));assertEquals(4,h.observedIncrease(2));}
}
