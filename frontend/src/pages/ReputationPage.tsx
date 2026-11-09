import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api, describeError } from '../api';
import type { LedgerEntry, ReputationProfile } from '../types';

const PAGE_SIZE = 20;

export function ReputationPage() {
  const { id } = useParams();
  const userId = Number(id);
  const [profile, setProfile] = useState<ReputationProfile | null>(null);
  const [ledger, setLedger] = useState<LedgerEntry[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState('');

  const loadProfile = useCallback(() => {
    api.reputation(userId).then(setProfile).catch((e) => setError(describeError(e)));
  }, [userId]);

  const loadLedger = useCallback(() => {
    api.ledger(userId, page, PAGE_SIZE).then((r) => {
      setLedger(r.items);
      setTotal(r.total);
    }).catch((e) => setError(describeError(e)));
  }, [userId, page]);

  useEffect(() => { loadProfile(); }, [loadProfile]);
  useEffect(() => { loadLedger(); }, [loadLedger]);

  if (!profile) return <p className="muted">{error || '加载中…'}</p>;

  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="form-page">
      <h1>{profile.displayName} 的声誉主页</h1>
      <p className="muted">当前声誉 <b>{profile.reputation}</b> 分</p>

      <h2>徽章墙</h2>
      {profile.badges.length === 0 ? <p className="muted">还没有获得徽章。</p> : (
        <div className="badge-wall">
          {profile.badges.map((b) => (
            <div className="badge-card" key={b.code}>
              <b>{b.name}</b>
              <p>{b.description}</p>
              <p className="muted">{new Date(b.awardedAt).toLocaleDateString('zh-CN')}</p>
            </div>
          ))}
        </div>
      )}

      <h2>声誉流水</h2>
      <table className="board">
        <thead><tr><th>变动</th><th>原因</th><th>时间</th></tr></thead>
        <tbody>
          {ledger.map((l) => (
            <tr key={l.id}>
              <td className={l.delta >= 0 ? 'pos' : 'neg'}>
                {l.delta >= 0 ? `+${l.delta}` : l.delta}
              </td>
              <td>{l.reasonLabel}</td>
              <td className="muted">{new Date(l.createdAt).toLocaleString('zh-CN')}</td>
            </tr>
          ))}
          {ledger.length === 0 && <tr><td colSpan={3} className="muted">暂无流水</td></tr>}
        </tbody>
      </table>
      <div className="actions">
        <button disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>上一页</button>
        <span className="muted">第 {page} / {pages} 页（共 {total} 条）</span>
        <button disabled={page >= pages} onClick={() => setPage((p) => p + 1)}>下一页</button>
      </div>
      {error && <p className="error">{error}</p>}
    </div>
  );
}
