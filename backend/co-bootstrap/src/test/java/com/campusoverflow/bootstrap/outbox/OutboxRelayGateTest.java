package com.campusoverflow.bootstrap.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link OutboxRelayGate} 的语义：暂停窗口可嵌套、异常也必须恢复、多调一次 resume 必须报错。 */
class OutboxRelayGateTest {

    private final OutboxRelayGate gate = new OutboxRelayGate();

    @Test
    @DisplayName("默认不暂停；pause/resume 成对时回到未暂停")
    void pauseAndResume() {
        assertThat(gate.isPaused()).isFalse();
        gate.pause();
        assertThat(gate.isPaused()).isTrue();
        gate.resume();
        assertThat(gate.isPaused()).isFalse();
    }

    @Test
    @DisplayName("runPaused 在异常路径上也会恢复中继")
    void runPausedRestoresOnException() {
        assertThatThrownBy(() -> gate.runPaused(() -> {
            throw new IllegalStateException("播种失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(gate.isPaused()).as("播种抛异常时中继也必须恢复，否则事件永远不投递").isFalse();
    }

    @Test
    @DisplayName("暂停窗口可嵌套：内层恢复不解除外层的暂停")
    void nestedPauseKeepsOuterWindow() {
        gate.pause();
        gate.runPaused(() -> assertThat(gate.isPaused()).isTrue());
        assertThat(gate.isPaused()).as("内层结束后外层窗口仍然有效").isTrue();
        gate.resume();
        assertThat(gate.isPaused()).isFalse();
    }

    @Test
    @DisplayName("resume 多于 pause 时直接报错，而不是悄悄把中继永久停掉")
    void excessResumeFailsLoudly() {
        assertThatThrownBy(gate::resume).isInstanceOf(IllegalStateException.class);
        assertThat(gate.isPaused()).isFalse();
    }
}
