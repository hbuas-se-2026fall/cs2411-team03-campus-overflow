package com.campusoverflow.bootstrap.outbox;

import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 发件箱中继的暂停开关。
 *
 * <p>存在的理由只有一个（Issue #96）：<strong>演示数据播种与中继不能同时写 outbox_event</strong>。
 * 播种在启动期连续写入多条事件（每条一个短事务），中继在同一时间窗里认领并投递事件、且投递时
 * 订阅者还要写通知/声誉表；两者一旦在同一批行/同一张表上互相等锁，InnoDB 就会选一方当牺牲品回滚。
 * 实际观测到的是播种被回滚，{@code ApplicationRunner} 抛出，后端启动即退出。
 *
 * <p>与其去猜是哪一对锁形成了环路（锁图在 MySQL 里只在死锁那一刻存在，日志里也只剩一行结论），
 * 不如让这两件事<strong>不在同一时间窗里发生</strong>：启动期由播种方暂停中继，播种完成后恢复。
 * 这是结构性的保证，不依赖隔离级别、不依赖锁的粒度，也不需要"重试兜底"。
 *
 * <p>用计数器而不是布尔值，是为了任意嵌套的暂停窗口都安全；{@link #resume()} 多调一次会直接报错，
 * 而不是悄悄把中继永久停掉（后者在生产里是最坏的失败模式：事件不投递，但没有任何指标报警）。
 */
@Component
public class OutboxRelayGate {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayGate.class);

    private final AtomicInteger pauseDepth = new AtomicInteger();

    public boolean isPaused() {
        return pauseDepth.get() > 0;
    }

    /** 进入暂停窗口；必须与 {@link #resume()} 配对，推荐用 {@link #runPaused(Runnable)}。 */
    public void pause() {
        int depth = pauseDepth.incrementAndGet();
        if (depth == 1) {
            log.info("发件箱中继已暂停（启动期写入独占）");
        }
    }

    public void resume() {
        int depth = pauseDepth.decrementAndGet();
        if (depth < 0) {
            pauseDepth.set(0);
            throw new IllegalStateException("OutboxRelayGate.resume() 调用次数多于 pause()：中继状态可能已被破坏");
        }
        if (depth == 0) {
            log.info("发件箱中继已恢复");
        }
    }

    /** 在暂停窗口内执行 {@code action}，无论正常返回还是抛异常都会恢复中继。 */
    public void runPaused(Runnable action) {
        pause();
        try {
            action.run();
        } finally {
            resume();
        }
    }
}
