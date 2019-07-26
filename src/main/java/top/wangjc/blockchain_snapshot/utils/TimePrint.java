package top.wangjc.blockchain_snapshot.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimePrint {
    private long start;

    public TimePrint() {
        start = System.currentTimeMillis();
    }

    public void markPoint(String lable) {
        log.info("arrive mark point {} cost:{}",lable, System.currentTimeMillis() - start);
    }

}
