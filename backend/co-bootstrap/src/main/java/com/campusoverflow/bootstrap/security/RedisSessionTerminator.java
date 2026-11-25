package com.campusoverflow.bootstrap.security;

import com.campusoverflow.shared.security.SessionTerminator;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * 用 Spring Session 的 principal name 索引，把某个用户的全部会话从 Redis 里删掉。
 *
 * <p>索引由 Spring Session 自动维护（会话里带 SPRING_SECURITY_CONTEXT 时按 principal name 建索引），
 * 所以不需要手动登记。仓库 bean 是否在场取决于部署：测试剖面排除了 SessionAutoConfiguration，
 * 这时返回 0——账号状态已落库，登录会被 canLogin 拒绝，只是不额外踢一次人。
 */
@Component
public class RedisSessionTerminator implements SessionTerminator {

    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repository;

    public RedisSessionTerminator(ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repository) {
        this.repository = repository;
    }

    @Override
    public int terminateAll(String username) {
        FindByIndexNameSessionRepository<? extends Session> repo = repository.getIfAvailable();
        if (repo == null) {
            return 0;
        }
        Map<String, ? extends Session> sessions = repo.findByPrincipalName(username);
        sessions.keySet().forEach(repo::deleteById);
        return sessions.size();
    }
}
