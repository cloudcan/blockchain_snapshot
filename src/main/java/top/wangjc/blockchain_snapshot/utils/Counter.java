package top.wangjc.blockchain_snapshot.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class Counter {
    private AtomicInteger counter;
    private Timer timer;
    private String name;

    public Counter(String name) {
        timer = new Timer();
        timer.scheduleAtFixedRate(new CounterTimerTask(), 0, 1 * 1000);
        this.name = name;
        this.counter = new AtomicInteger(0);
    }

    public int getCounterValue() {
        return counter.get();
    }

    public void reset() {
        counter.set(0);
    }

    public void increse() {
        counter.incrementAndGet();
    }

    public void increse(int num) {
        counter.addAndGet(num);
    }

    private class CounterTimerTask extends TimerTask {
        private int lastValue;

        @Override
        public void run() {
            int counterValue = counter.get();
            int speed = counterValue - lastValue;
            lastValue = counterValue;
            log.info("============={} speed {}/second==========", name, speed);
        }
    }
}
