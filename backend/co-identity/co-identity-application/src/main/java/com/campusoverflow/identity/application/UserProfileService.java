package com.campusoverflow.identity.application;

import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例：本人查看与修改个人资料（PB-41）。
 *
 * <p>「查看」复用 {@link UserQueryService#me}（GET /api/v1/auth/me 早已存在，登录接口也返回同一份视图）；
 * 这里只补写路径。唯一性（昵称、邮箱全局唯一）是仓储才能回答的问题，因此放在这一层，
 * 格式校验仍由领域对象在 {@code updateProfile} 里完成——同一套规则，不另造一份。
 */
@Service
public class UserProfileService {

    private final UserRepository users;
    private final UserQueryService query;
    private final AuditTrail audit;

    public UserProfileService(UserRepository users, UserQueryService query, AuditTrail audit) {
        this.users = users;
        this.query = query;
        this.audit = audit;
    }

    @Transactional
    public MeView update(Actor actor, UpdateProfileCommand cmd) {
        if (actor == null) {
            throw new NotFoundException("ID-4001", "用户不存在");
        }
        User user = users.findById(actor.userId())
                .orElseThrow(() -> new NotFoundException("ID-4001", "用户不存在"));

        // 只在「值真的变了」的时候查唯一性：把没动过的昵称原样提交，不该撞到自己那条记录。
        if (!user.displayName().equals(cmd.displayName()) && users.existsByDisplayName(cmd.displayName())) {
            throw new ConflictException("ID-2002", "该昵称已被使用");
        }
        if (!user.email().equalsIgnoreCase(cmd.email()) && users.existsByEmail(cmd.email())) {
            throw new ConflictException("ID-2003", "该邮箱已被使用");
        }

        user.updateProfile(actor, cmd.displayName(), cmd.email(), cmd.college(), cmd.className());
        users.save(user);

        // 只记字段名，不把邮箱写进审计表——审计表比业务表更难清理，不该顺手多存一份 PII。
        audit.record("USER_PROFILE_UPDATED", user.id(), "USER", String.valueOf(user.id()),
                "displayName,email,college,className");
        return query.me(user.id());
    }
}
