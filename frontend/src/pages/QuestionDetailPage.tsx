import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, describeError } from '../api';
import { useAuth } from '../auth';
import type { Answer, QuestionDetail } from '../types';

export function QuestionDetailPage() {
  const { id } = useParams();
  const questionId = Number(id);
  const navigate = useNavigate();
  const { me } = useAuth();
  const [q, setQ] = useState<QuestionDetail | null>(null);
  const [related, setRelated] = useState<{ id: number; title: string }[]>([]);
  const [draft, setDraft] = useState('');
  const [error, setError] = useState('');

  // 问题编辑
  const [editingQ, setEditingQ] = useState(false);
  const [qTitle, setQTitle] = useState('');
  const [qBody, setQBody] = useState('');
  const [qTags, setQTags] = useState('');

  // 回答编辑
  const [editingA, setEditingA] = useState<number | null>(null);
  const [aBody, setABody] = useState('');

  const load = useCallback(() => {
    api.question(questionId).then(setQ).catch((e) => setError(describeError(e)));
  }, [questionId]);

  useEffect(() => {
    load();
    api.related(questionId).then(setRelated).catch(() => undefined);
  }, [load, questionId]);

  const act = (fn: () => Promise<unknown>) => fn().then(load).catch((e) => setError(describeError(e)));

  if (!q) return <p className="muted">{error || '加载中…'}</p>;

  const canManage = q.permissions.canManage;

  const startEditQ = () => {
    setQTitle(q.title);
    setQBody(q.body);
    setQTags(q.tags.join(', '));
    setEditingQ(true);
  };
  const saveEditQ = () => act(async () => {
    await api.editQuestion(q.id, {
      title: qTitle.trim(),
      body: qBody,
      tags: qTags.split(/[,，]/).map((s) => s.trim()).filter(Boolean),
    });
    setEditingQ(false);
  });
  const removeQ = () => {
    if (!window.confirm('确定删除这个问题吗？删除后不可恢复。')) return;
    act(async () => { await api.deleteQuestion(q.id); navigate('/'); });
  };

  const startEditA = (a: Answer) => { setABody(a.body); setEditingA(a.id); };
  const saveEditA = () => act(async () => {
    if (editingA == null) return;
    await api.editAnswer(editingA, aBody);
    setEditingA(null);
  });
  const removeA = (a: Answer) => {
    if (!window.confirm('确定删除这条回答吗？删除后不可恢复。')) return;
    act(async () => { await api.deleteAnswer(a.id); });
  };

  return (
    <div className="layout">
      <article>
        {editingQ ? (
          <section className="edit-form">
            <h2>编辑问题</h2>
            <input value={qTitle} onChange={(e) => setQTitle(e.target.value)} maxLength={150}
                   placeholder="标题（1–150 字符）" />
            <textarea rows={10} value={qBody} onChange={(e) => setQBody(e.target.value)}
                      placeholder="正文（支持 Markdown）" />
            <input value={qTags} onChange={(e) => setQTags(e.target.value)}
                   placeholder="标签，逗号分隔（1–5 个）" />
            <div className="actions">
              <button className="primary" onClick={saveEditQ}>保存</button>
              <button className="link" onClick={() => setEditingQ(false)}>取消</button>
            </div>
          </section>
        ) : (
          <>
            <h1>{q.title}</h1>
            <p className="muted">
              {q.author.displayName} · {new Date(q.createdAt).toLocaleString('zh-CN')} · {q.viewCount} 次浏览
              {q.course && <> · 课程 {q.course.name}</>}
            </p>
            <div className="post">
              <div className="votebox">
                <button disabled={!me} onClick={() => act(() => api.vote('QUESTION', q.id, q.myVote === 1 ? 0 : 1))}>▲</button>
                <b>{q.score}</b>
                <button disabled={!me} onClick={() => act(() => api.vote('QUESTION', q.id, q.myVote === -1 ? 0 : -1))}>▼</button>
              </div>
              <div className="body">
                {/* 正文由后端渲染并经 commonmark + OWASP Sanitizer 双重净化 */}
                <div dangerouslySetInnerHTML={{ __html: q.bodyHtml }} />
                <div className="tagrow">{q.tags.map((t) => <span key={t} className="tag">{t}</span>)}</div>
                {(q.permissions.canEdit || q.permissions.canDelete) && (
                  <div className="actions">
                    {q.permissions.canEdit && <button onClick={startEditQ}>编辑</button>}
                    {q.permissions.canDelete && <button className="danger" onClick={removeQ}>删除</button>}
                  </div>
                )}
                <CommentList comments={q.comments} targetType="QUESTION" targetId={q.id} onDone={load} canComment={!!me} />
                {canManage && (
                  <button className="link" onClick={() => {
                    const points = Number(prompt('悬赏积分（10–500）', '50'));
                    if (points) act(() => api.bounty(q.id, points, 7));
                  }}>发起悬赏
                  </button>
                )}
              </div>
            </div>
          </>
        )}

        <h2>{q.answerCount} 个回答</h2>
        {q.answers.map((a) => (
          <div className={`post ${q.acceptedAnswerId === a.id ? 'is-accepted' : ''}`} key={a.id} id={`answer-${a.id}`}>
            <div className="votebox">
              <button disabled={!me} onClick={() => act(() => api.vote('ANSWER', a.id, a.myVote === 1 ? 0 : 1))}>▲</button>
              <b>{a.score}</b>
              <button disabled={!me} onClick={() => act(() => api.vote('ANSWER', a.id, a.myVote === -1 ? 0 : -1))}>▼</button>
              {q.acceptedAnswerId === a.id && <span className="check-mark" title="已采纳">✔</span>}
            </div>
            <div className="body">
              {editingA === a.id ? (
                <div className="edit-form">
                  <textarea rows={8} value={aBody} onChange={(e) => setABody(e.target.value)} />
                  <div className="actions">
                    <button className="primary" onClick={saveEditA}>保存</button>
                    <button className="link" onClick={() => setEditingA(null)}>取消</button>
                  </div>
                </div>
              ) : (
                <>
                  {/* 回答正文同样由后端净化 */}
                  <div dangerouslySetInnerHTML={{ __html: a.bodyHtml }} />
                  <p className="muted">
                    {a.author.displayName}
                    {a.endorsedBy && <span className="endorsed">教师认证 · {a.endorsedBy.displayName}</span>}
                    · {new Date(a.createdAt).toLocaleString('zh-CN')}
                  </p>
                  <div className="actions">
                    {q.permissions.canAccept && !q.acceptedAnswerId &&
                      <button onClick={() => act(() => api.accept(q.id, a.id))}>采纳此回答</button>}
                    {canManage && !a.endorsedBy && <button onClick={() => act(() => api.endorse(a.id))}>教师认证</button>}
                    {a.canEdit && <button onClick={() => startEditA(a)}>编辑</button>}
                    {(a.canEdit || canManage) && <button className="danger" onClick={() => removeA(a)}>删除</button>}
                  </div>
                </>
              )}
              <CommentList comments={a.comments} targetType="ANSWER" targetId={a.id} onDone={load} canComment={!!me} />
            </div>
          </div>
        ))}

        {me ? (
          <section className="answer-form">
            <h3>写回答</h3>
            <textarea rows={8} value={draft} onChange={(e) => setDraft(e.target.value)}
                      placeholder="支持 Markdown，代码请使用 ``` 包裹" />
            <button className="primary" onClick={() => act(async () => {
              await api.answer(q.id, draft);
              setDraft('');
            })}>提交回答
            </button>
          </section>
        ) : <p className="muted"><Link to="/login">登录</Link>后可以回答与投票。</p>}
        {error && <p className="error">{error}</p>}
      </article>

      <aside>
        <h3>相关问题</h3>
        <ul className="plain">
          {related.map((r) => <li key={r.id}><Link to={`/questions/${r.id}`}>{r.title}</Link></li>)}
          {related.length === 0 && <li className="muted">暂无</li>}
        </ul>
      </aside>
    </div>
  );
}

function CommentList({ comments, targetType, targetId, onDone, canComment }: {
  comments: { id: number; author: { displayName: string }; body: string; parentId?: number; createdAt: string }[];
  targetType: 'QUESTION' | 'ANSWER';
  targetId: number;
  onDone: () => void;
  canComment: boolean;
}) {
  const [text, setText] = useState('');
  const [open, setOpen] = useState(false);
  const [replyTo, setReplyTo] = useState<number | null>(null);
  const [replyText, setReplyText] = useState('');
  const [err, setErr] = useState('');

  const submit = (body: string, parentId?: number) => {
    if (!body.trim()) return;
    api.comment(targetType, targetId, body.trim(), parentId)
      .then(() => { setText(''); setReplyText(''); setOpen(false); setReplyTo(null); setErr(''); onDone(); })
      .catch((e) => setErr(describeError(e)));
  };

  const top = comments.filter((c) => c.parentId == null);
  const repliesOf = (parentId: number) => comments.filter((c) => c.parentId === parentId);

  return (
    <div className="comments">
      {top.map((c) => (
        <div key={c.id}>
          <div className="comment">
            <span>{c.body}</span>
            <span className="muted"> — {c.author.displayName}</span>
            {canComment && <button className="link" onClick={() => setReplyTo(replyTo === c.id ? null : c.id)}>回复</button>}
          </div>
          {replyTo === c.id && (
            <div className="comment-form reply">
              <input value={replyText} onChange={(e) => setReplyText(e.target.value)}
                     placeholder="回复此评论，可用 @昵称 提醒" />
              <button onClick={() => submit(replyText, c.id)}>回复</button>
            </div>
          )}
          {repliesOf(c.id).map((re) => (
            <div className="comment reply" key={re.id}>
              <span>{re.body}</span>
              <span className="muted"> — {re.author.displayName}</span>
            </div>
          ))}
        </div>
      ))}
      {canComment && (open ? (
        <div className="comment-form">
          <input value={text} onChange={(e) => setText(e.target.value)} placeholder="评论，可用 @昵称 提醒他人" />
          <button onClick={() => submit(text)}>发表</button>
        </div>
      ) : <button className="link" onClick={() => setOpen(true)}>添加评论</button>)}
      {err && <p className="error">{err}</p>}
    </div>
  );
}
