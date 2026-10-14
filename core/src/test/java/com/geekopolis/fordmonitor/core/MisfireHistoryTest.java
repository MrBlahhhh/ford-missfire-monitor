package com.geekopolis.fordmonitor.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class MisfireHistoryTest {
    @Test public void initialHistoricalCountNotNewEvents(){var h=new MisfireHistory();assertNull(h.count(5,1000,18).increase());assertEquals(0,h.observedIncrease(5));}
    @Test public void onlyChangesAreNewEvents(){var h=new MisfireHistory();h.count(5,1000,18);assertEquals(Integer.valueOf(3),h.count(5,2000,21).increase());assertEquals(3,h.observedIncrease(5));}
    @Test public void unchangedCountIsZeroNew(){var h=new MisfireHistory();h.count(5,1000,18);assertEquals(Integer.valueOf(0),h.count(5,2000,18).increase());}
    @Test public void counterDecreaseRebaselinesNotWrap(){var h=new MisfireHistory();h.count(5,1000,65535);var c=h.count(5,2000,0);assertNull(c.increase());assertTrue(c.reset());assertEquals(0,h.observedIncrease(5));}
    @Test public void newCycleIncreasesAfterReset(){var h=new MisfireHistory();h.count(5,1000,20);h.count(5,2000,0);assertEquals(Integer.valueOf(2),h.count(5,3000,2).increase());}
    @Test public void staleGapNotAttributedToNewRead(){var h=new MisfireHistory();h.count(5,1000,20);assertNull(h.count(5,32001,25).increase());}
    @Test public void missingResultBreaksBaseline(){var h=new MisfireHistory();h.count(5,1000,18);h.unavailable(5,2000);assertNull(h.count(5,3000,23).increase());}
    @Test public void disconnectBreaksBaseline(){var h=new MisfireHistory();h.count(5,1000,18);h.pause();assertNull(h.count(5,2000,22).increase());}
    @Test public void cylindersIndependent(){var h=new MisfireHistory();h.count(5,1000,18);h.count(6,1000,2);assertEquals(Integer.valueOf(1),h.count(6,2000,3).increase());assertEquals(Integer.valueOf(0),h.count(5,2000,18).increase());}
    @Test public void barsRepresentWholeObservedInterval(){var h=new MisfireHistory();h.count(5,1000,18);var c=h.count(5,9000,20);assertEquals(1000,c.from());assertEquals(9000,c.time());}
    @Test public void rejectsOlderCallback(){var h=new MisfireHistory();h.count(5,2000,18);assertNull(h.count(5,1000,22));assertEquals(Integer.valueOf(1),h.count(5,3000,19).increase());}
    @Test public void clearOnlyLocalGraph(){var h=new MisfireHistory();h.count(5,1000,18);h.load(1000,50.0);h.clear();assertEquals(-1,h.latest());assertTrue(h.loads().isEmpty());assertTrue(h.changes(5).isEmpty());assertNull(h.count(5,2000,18).increase());}
    @Test public void invalidLoadIsGapNotZero(){var h=new MisfireHistory();h.load(1,Double.NaN);h.load(2,101.0);assertNull(h.loads().get(0).percent());assertNull(h.loads().get(1).percent());}
    @Test public void boundedHistory(){var h=new MisfireHistory();for(int i=0;i<3000;i++){h.count(5,i,i);h.load(i,50.0);}assertTrue(h.loads().size()<=2048);assertTrue(h.changes(5).size()<=2048);}
    @Test public void oldPointsExpire(){var h=new MisfireHistory();h.count(5,0,0);h.load(0,50.0);h.count(5,300001,1);h.load(300001,50.0);assertEquals(1,h.loads().size());assertEquals(1,h.changes(5).size());}
    @Test public void stoppedCylinderEventsExpireWhileLoadContinues(){var h=new MisfireHistory();h.count(5,0,0);h.count(5,1000,1);h.unavailable(5,2000);h.load(400000,50.0);assertTrue(h.changes(5).isEmpty());assertEquals(0,h.observedIncrease(5));assertFalse(h.hasBaseline(5));}
    @Test(expected=IllegalArgumentException.class) public void invalidCylinderRejected(){new MisfireHistory().count(11,100,0);}
}
