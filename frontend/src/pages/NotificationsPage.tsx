import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, describeError } from '../api';
import type { Notification, Page } from '../types';

/** 通知类型 → 中文标签，与后端 NotificationType 枚举一一对应。 */
const TYPE_LABELS: Record<string, string> = {
  ANSWER_RECEIVED: '新回答',
  ANSWER_ACCEPTED: '回答被采纳',
  ANSWER_ENDORSED: '教师认证',
  MENTIONED: '被提及',
  BOUNTY_AWARDED: '悬赏奖励',
  BADGE_AWARDED: '获得徽章',
};

/** 通知发生变化后通知顶栏刷新未读徽标。 */
function notifyUnreadChanged() {
  window.dispatchEvent(new Event('co:unread-changed'));
}

export function NotificationsPage() {
  const navigate = useNavigate();
  const [data, setData] = useState<Page<Notification> | null>(null);
  const [page, setPage] = useState(1);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api.notifications(page, 20)
      .then((p) => { setData(p); setError(''); })
      .catch((e) => setError(describeError(e)));
  }, [page]);

  useEffect(() => { load(); }, [load]);

  const open = (n: Notification) => {
    const go = () => { if (n.link) navigate(n.link); };
    if (n.read) { go(); return; }
    api.markRead(n.id)
      .then(() => {
        setData((d) => d && { ...d, items: d.items.map((i) => (i.id === n.id ? { ...i, read: true } : i)) });
        notifyUnreadChanged();
        go();
      })
      .catch((e) => setError(describeError(e)));
  };

  const readAll = () => {
    setBusy(true);
    api.readAll()
      .then(() => {
        setData((d) => d && { ...d, items: d.items.map((i) => ({ ...i, read: true })) });
        notifyUnreadChanged();
        setError('');
      })
      .catch((e) => setError(describeError(e)))
      .finally(() => setBusy(false));
  };

  const unread = data?.items.filter((i) => !i.read).length ?? 0;
  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 0;

  return (
    <div className="form-page">
      <h1>通知</h1>
      <div className="toolbar">
        <span className="muted">本页未读 {unread} 条 · 共 {data?.total ?? 0} 条</span>
        <button onClick={readAll} disabled={busy || unread === 0}>全部标记为已读</button>
      </div>

      {error && <div className="error-box"><p className="error">{error}</p><button onClick={load}>重试</button></div>}

      <ul className="plain notif-list">
        {data?.items.map((n) => (
          <li key={n.id} className={n.read ? 'notif read' : 'notif unread'}>
            <span className={n.read ? 'dot' : 'dot on'} aria-hidden="true" />
            <span className="notif-type">{TYPE_LABELS[n.type] ?? n.type}</span>
            {n.link
              ? <button className="link notif-title" onClick={() => open(n)}>{n.title}</button>
              : <span className="notif-title">{n.title}</span>}
            <span className="muted"> · {new Date(n.createdAt).toLocaleString('zh-CN')}</span>
          </li>
        ))}
        {data && data.items.length === 0 && <li className="muted">暂时没有通知。</li>}
      </ul>

      {data && data.total > data.size && (
        <div className="pager">
          <button disabled={page <= 1} onClick={() => setPage(page - 1)}>上一页</button>
          <span>第 {page} / {totalPages} 页</span>
          <button disabled={page >= totalPages} onClick={() => setPage(page + 1)}>下一页</button>
        </div>
      )}

      <p className="muted">
        <Link to="/">返回问题列表</Link>
      </p>
    </div>
  );
}
