package com.campusoverflow.bootstrap.outbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 暂停窗口内中继必须<strong>一条 SQL 都不发</strong>（Issue #96 的回归保护）。
 *
 * <p>用"一被调用就抛 AssertionError"的 JdbcTemplate 来证明这件事：暂停时 {@code relay()} 正常返回，
 * 未暂停时立刻炸——说明跳过确实来自开关，而不是因为库里恰好没数据。
 */
class OutboxRelayPausedTest {

    private final OutboxRelayGate gate = new OutboxRelayGate();

    @Test
    @DisplayName("暂停时 relay() 不访问数据库；恢复后照常认领（对照）")
    void pausedRelayDoesNotTouchDatabase() {
        OutboxRelay relay = relayOver(explodingJdbc());

        gate.pause();
        assertThatCode(relay::relay).as("暂停期间不得访问数据库").doesNotThrowAnyException();

        gate.resume();
        assertThatThrownBy(relay::relay)
                .as("恢复后应当真的去认领（这里用会炸的 JdbcTemplate 反证跳过来自开关）")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("不应访问数据库");
    }

    @Test
    @DisplayName("暂停期间调用方可以显式 relayOnce() 推进（播种线程给自己补投）")
    void relayOnceBypassesGate() {
        OutboxRelay relay = relayOver(explodingJdbc());
        gate.pause();
        assertThatThrownBy(relay::relayOnce)
                .as("relayOnce 是显式推进，不受暂停开关约束")
                .isInstanceOf(AssertionError.class);
    }

    private OutboxRelay relayOver(JdbcTemplate boom) {
        return new OutboxRelay(boom, new NamedParameterJdbcTemplate(boom), new TransactionTemplate(stubManager()),
                gate, (ApplicationEventPublisher) event -> { }, new ObjectMapper(), Clock.systemUTC(),
                new SimpleMeterRegistry(), 100, 10);
    }

    private static JdbcTemplate explodingJdbc() {
        return new JdbcTemplate() {
            @Override
            public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
                throw new AssertionError("暂停期间不应访问数据库: " + sql);
            }
        };
    }

    /** 只用来构造模板，不会被真正使用。 */
    private static PlatformTransactionManager stubManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
                // no-op
            }

            @Override
            public void rollback(TransactionStatus status) {
                // no-op
            }
        };
    }
}
