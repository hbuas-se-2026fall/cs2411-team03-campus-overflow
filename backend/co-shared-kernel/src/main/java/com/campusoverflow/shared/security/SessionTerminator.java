package com.campusoverflow.shared.security;

/**
 * 会话终止端口：管理员禁用账号时，需要立刻踢掉该用户已经登录的会话。
 *
 * <p>放在共享内核，是因为 identity 应用层要用它、却不能依赖 Web 与 Redis 的实现细节；
 * 具体怎么「踢」（Redis 会话、内存会话、第三方 SSO）由部署侧的适配器决定。
 */
public interface SessionTerminator {

    /**
     * 终止该用户名对应的全部登录会话。
     *
     * @return 被删除的会话数；当前部署没有会话仓库时返回 0，调用方不应把它当作失败——
     *         「立即下线」由会话删除与 {@code canLogin} 双重保证，后者在任何部署下都生效。
     */
    int terminateAll(String username);
}
