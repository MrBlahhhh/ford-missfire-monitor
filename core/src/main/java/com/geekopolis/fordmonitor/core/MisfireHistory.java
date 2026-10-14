package com.geekopolis.fordmonitor.core;

import java.util.*;

/** Counts observed after a baseline, not lifetime totals or estimated instant misfire timestamps. */
public final class MisfireHistory {
    public record Load(long time, Double percent) {}
    public record Change(long from, long time, int cycleCount, Integer increase, boolean reset) {}
    private record Baseline(int count, long time) {}
    private final Deque<Load> loads = new ArrayDeque<>();
    private final Map<Integer, Deque<Change>> changes = new HashMap<>();
    private final Map<Integer, Baseline> baselines = new HashMap<>();
    private final Map<Integer, Long> lastRead = new HashMap<>();
    private static final long WINDOW = 300_000;
    private static final int CAP = 2048;
    private long latest = -1, first = -1;
    public synchronized void clear() { loads.clear(); changes.clear(); baselines.clear(); lastRead.clear(); latest=-1; first=-1; }
    public synchronized void pause() { baselines.clear(); }
    public synchronized void load(long time, Double value) {
        if (!loads.isEmpty() && time < loads.peekLast().time()) return;
        if (value != null && (!Double.isFinite(value) || value < 0 || value > 100)) value=null;
        loads.addLast(new Load(time,value)); tick(time); prune(loads, time);
    }
    public synchronized void unavailable(int cylinder, long time) {
        if (time < lastRead.getOrDefault(cylinder,-1L)) return;
        lastRead.put(cylinder,time); baselines.remove(cylinder); tick(time);
    }
    public synchronized Change count(int cylinder, long time, int count) {
        if (cylinder < 1 || cylinder > 10 || count < 0 || count > 65535) throw new IllegalArgumentException("Invalid cylinder/count");
        if (time < lastRead.getOrDefault(cylinder,-1L)) return null;
        Baseline old=baselines.get(cylinder);
        boolean gap=old!=null && time-old.time()>30_000;
        boolean reset=old!=null && count<old.count();
        Integer delta=old==null || reset || gap ? null : count-old.count();
        Change sample=new Change(old==null || gap ? time : old.time(),time,count,delta,reset);
        baselines.put(cylinder,new Baseline(count,time)); lastRead.put(cylinder,time);
        Deque<Change> q=changes.computeIfAbsent(cylinder,k->new ArrayDeque<>());q.addLast(sample);
        tick(time);while(q.size()>CAP || (!q.isEmpty() && q.peekFirst().time()<latest-WINDOW))q.removeFirst();
        return sample;
    }
    private void tick(long time) { if(first<0)first=time;latest=Math.max(latest,time); }
    private void prune(Deque<Load> q,long time) {while(q.size()>CAP || (!q.isEmpty() && q.peekFirst().time()<time-WINDOW))q.removeFirst();}
    public synchronized List<Load> loads() { return Collections.unmodifiableList(new ArrayList<>(loads)); }
    public synchronized List<Change> changes(int cylinder) {
        List<Change> out=new ArrayList<>();
        for(Change c:changes.getOrDefault(cylinder,new ArrayDeque<>()))if(c.time()>=latest-WINDOW)out.add(c);
        return Collections.unmodifiableList(out);
    }
    public synchronized boolean hasBaseline(int cylinder) {return baselines.containsKey(cylinder);}
    public synchronized long latest() {return latest;}
    public synchronized long first() {return first;}
    public synchronized long observedIncrease(int cylinder) {long n=0;for(Change c:changes(cylinder))if(c.increase()!=null)n+=c.increase();return n;}
}
