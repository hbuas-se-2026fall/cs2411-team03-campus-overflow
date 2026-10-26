package com.campusoverflow.qa.application.command;

import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.AnswerRepository;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionRepository;
import com.campusoverflow.qa.domain.TargetType;
import com.campusoverflow.qa.domain.Vote;
import com.campusoverflow.qa.domain.VoteRepository;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例：投票 / 改投 / 撤销（value ∈ {-1, 0, 1}，0 表示撤销）。
 * 发布的事件同时携带旧值与新值，下游据此计算声誉增量。
 */
@Service
public class VoteService {

    private final VoteRepository votes;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public VoteService(VoteRepository votes, QuestionRepository questions, AnswerRepository answers,
                       IntegrationEventPublisher events, Clock clock) {
        this.votes = votes;
        this.questions = questions;
        this.answers = answers;
        this.events = events;
        this.clock = clock;
    }

    public record VoteResult(int myVote, int scoreDelta) {
    }

    private record Target(long authorId, long questionId) {
    }

    @Transactional
    public VoteResult cast(Actor actor, TargetType type, long targetId, int value) {
        Target target = resolve(type, targetId);
        if (actor.is(target.authorId())) {
            throw new BusinessRuleException("QA-1015", "不能给自己内容投票");
        }
        Optional<Vote> existing = votes.find(actor.userId(), type, targetId);
        if (existing.isPresent() && existing.get().value() == value) {
            return new VoteResult(value, 0); // 幂等：重复提交同一命令，不产生事件
        }
        int oldValue = 0;
        int newValue = value;
        if (value == 0) {
            if (existing.isEmpty()) {
                return new VoteResult(0, 0); // 撤销不存在的投票，无副作用
            }
            oldValue = existing.get().value();
            votes.delete(existing.get());
        } else if (existing.isPresent()) {
            Vote vote = existing.get();
            oldValue = vote.change(value, clock.instant());
            votes.save(vote);
        } else {
            votes.save(Vote.cast(actor.userId(), target.authorId(), type, targetId, value, clock.instant()));
        }
        int delta = newValue - oldValue;
        if (type == TargetType.QUESTION) {
            questions.adjustScore(targetId, delta);
        } else {
            answers.adjustScore(targetId, delta);
        }
        events.publish(new VoteCastEvent(EventIds.next(), clock.instant(), type.name(), targetId, target.authorId(),
                actor.userId(), target.questionId(), oldValue, newValue));
        return new VoteResult(newValue, delta);
    }

    private Target resolve(TargetType type, long targetId) {
        if (type == TargetType.QUESTION) {
            Question question = questions.findById(targetId)
                    .orElseThrow(() -> new NotFoundException("QA-4001", "问题不存在"));
            if (question.isDeleted()) {
                throw new NotFoundException("QA-4001", "问题不存在");
            }
            return new Target(question.authorId(), question.id());
        }
        Answer answer = answers.findById(targetId)
                .orElseThrow(() -> new NotFoundException("QA-4002", "回答不存在"));
        if (answer.isDeleted()) {
            throw new NotFoundException("QA-4002", "回答不存在");
        }
        return new Target(answer.authorId(), answer.questionId());
    }
}
