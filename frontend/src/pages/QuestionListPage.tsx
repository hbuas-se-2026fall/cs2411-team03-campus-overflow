import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import type { Page, QuestionSummary, SearchResult } from '../types';

export function QuestionListPage() {
  const { me } = useAuth();
  const [data, setData] = useState<Page<QuestionSummary | SearchResult> | null>(null);
  const [tags, setTags] = useState<{ name: string; questionCount: number }[]>([]);
  const [keyword, setKeyword] = useState('');
  const [activeTag, setActiveTag] = useState<string | undefined>();
  const [sort, setSort] = useState('newest');
  const [unanswered, setUnanswered] = useState(false);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [refreshTick, setRefreshTick] = useState(0);

  const searching = keyword.trim() !== '' || !!activeTag || unanswered || sort !== 'newest';

  useEffect(() => {
    api.popularTags().then(setTags).catch(() => undefined);
  }, []);

  // 搜索条件一变，就回到第 1 页
  useEffect(() => { setPage(1); }, [keyword, activeTag, sort, unanswered]);

  // 请求数据（翻页或搜索条件变化时触发）
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    const promise = searching
      ? api.search(keyword.trim(), { tag: activeTag, sort, unanswered, page })
      : api.questions(page);
    promise
      .then((r) => { if (!cancelled) { setData(r); setLoading(false); } })
      .catch((e) => { if (!cancelled) { setError(e.message); setLoading(false); } });
    return () => { cancelled = true; };
  }, [page, searching, refreshTick]);

  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 0;

  return (
    <div className="layout">
      <section>
        <div className="toolbar">
          <input placeholder="搜索问题（支持中文全文检索）" value={keyword}
                 onChange={(e) => setKeyword(e.target.value)} />
          <select value={sort} onChange={(e) => setSort(e.target.value)}>
            <option value="newest">最新</option>
            <option value="active">最近活跃</option>
            <option value="hot">得分最高</option>
            <option value="relevance">相关度</option>
          </select>
          <label className="check">
            <input type="checkbox" checked={unanswered} onChange={(e) => setUnanswered(e.target.checked)} />
            仅看未回答
          </label>
          {me && <Link className="primary" to="/ask">我要提问</Link>}
        </div>

        {activeTag && (
          <p className="muted">
            标签筛选：<b>{activeTag}</b> <button onClick={() => setActiveTag(undefined)}>清除</button>
          </p>
        )}

        {error && (
          <div className="error-box">
            <p className="error">{error}</p>
            <button onClick={() => setRefreshTick((t) => t + 1)}>重试</button>
          </div>
        )}

        {loading ? (
          <ul className="qlist">
            {[1, 2, 3, 4].map((n) => (
              <li key={n} className="skeleton-card">
                <div className="skeleton skeleton-stats" />
                <div className="skeleton-main">
                  <div className="skeleton skeleton-title" />
                  <div className="skeleton skeleton-line" />
                  <div className="skeleton skeleton-line short" />
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <>
            <ul className="qlist">
              {data?.items.map((q) => (
                <li key={q.id}>
                  <div className="stats">
                    <span><b>{q.score}</b> 票</span>
                    <span className={q.accepted ? 'accepted' : ''}><b>{q.answerCount}</b> 回答</span>
                    <span>{q.viewCount} 浏览</span>
                  </div>
                  <div className="qmain">
                    <Link className="qtitle" to={`/questions/${q.id}`}>{q.title}</Link>
                    <p className="excerpt">{q.excerpt}</p>
                    <div className="tagrow">
                      {q.tags.map((t) => <button key={t} className="tag" onClick={() => setActiveTag(t)}>{t}</button>)}
                      <span className="muted">
                        {q.author.displayName} · {new Date(q.createdAt).toLocaleString('zh-CN')}
                      </span>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
            {data && data.items.length === 0 && <p className="muted">没有匹配的问题，换个关键词试试。</p>}
          </>
        )}

        {data && !loading && data.total > data.size && (
          <div className="pager">
            <button disabled={page <= 1} onClick={() => setPage(page - 1)}>上一页</button>
            <span>第 {page} / {totalPages} 页 · 共 {data.total} 条</span>
            <button disabled={page >= totalPages} onClick={() => setPage(page + 1)}>下一页</button>
          </div>
        )}
      </section>

      <aside>
        <h3>热门标签</h3>
        <div className="tagrow">
          {tags.map((t) => (
            <button key={t.name} className="tag" onClick={() => setActiveTag(t.name)}>
              {t.name}<span className="count">{t.questionCount}</span>
            </button>
          ))}
        </div>
      </aside>
    </div>
  );
}
