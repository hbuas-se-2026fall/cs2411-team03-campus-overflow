package com.campusoverflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * demo profile 的验收测试（PBI #44）：演示数据必须能被完整加载，并且通知中心里
 * 「回答被采纳」「获得徽章」「悬赏获奖」三类通知都要真的产生，而不是只写进 seeder 代码。
 *
 * <p>与 {@link CampusOverflowFlowIT} 一样使用真实 MySQL 8 与 Redis 7（Testcontainers），
 * 因此同时验证了 Flyway 迁移、发件箱中继与 demo 数据之间的配合。
 */
@SpringBootTest
@ActiveProfiles("demo")
@Testcontainers
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "co.outbox.poll-interval-ms=200"
})
class DemoDataSeederIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.39"))
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci",
                    "--ngram-token-size=2");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void demoProfileLoadsFullDatasetAndAllNotificationKinds() {
        // 六个演示账号（管理员 / 教师 / 助教 / 三个学生）
        assertThat(count("SELECT COUNT(*) FROM id_user")).isEqualTo(6);

        // 一门课、三个问题
        assertThat(count("SELECT COUNT(*) FROM id_course")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM qa_question")).isEqualTo(3);

        // 回答、投票、评论、悬赏：演示时每个交互入口都有内容可点
        assertThat(count("SELECT COUNT(*) FROM qa_answer")).isGreaterThanOrEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM qa_vote")).isGreaterThanOrEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM qa_comment")).isGreaterThanOrEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM rep_bounty")).isGreaterThanOrEqualTo(1);

        // 徽章：小红应拿到「初次解答 / 初露锋芒 / 名师认可」至少三枚
        assertThat(count("SELECT COUNT(*) FROM rep_badge")).isGreaterThanOrEqualTo(3);

        // 三类通知都要产生（发件箱异步中继，需等待）
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<String> types = jdbc.queryForList("SELECT DISTINCT type FROM dis_notification", String.class);
            assertThat(types).contains("ANSWER_ACCEPTED", "BADGE_AWARDED", "BOUNTY_AWARDED");
        });
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
