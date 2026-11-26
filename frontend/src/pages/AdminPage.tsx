import { useCallback, useEffect, useState } from 'react';
import { api, describeError } from '../api';
import { useAuth } from '../auth';
import type { AdminUser, Page, Role } from '../types';

const STAFF_ROLES: { value: Role; label: string }[] = [
  { value: 'STUDENT', label: 'STUDENT（学生）' },
  { value: 'TA', label: 'TA（助教）' },
  { value: 'TEACHER', label: 'TEACHER（教师）' },
];

const EMPTY_FORM = {
  username: '', displayName: '', email: '', password: '', role: 'STAFF' as string, college: '',
};

/** #53：管理员页面——认证学生、创建教职工账号。 */
export function AdminPage() {
  const { me, loading: authLoading } = useAuth();
  const isAdmin = me?.role === 'ADMIN';

  const [tab, setTab] = useState<'pending' | 'accounts' | 'create'>('pending');

  // 待认证列表
  const [data, setData] = useState<Page<AdminUser> | null>(null);
  const [page, setPage] = useState(1);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState('');
  const [verifyingId, setVerifyingId] = useState<number | null>(null);
  const [reloadTick, setReloadTick] = useState(0);

  // 创建教职工
  const [form, setForm] = useState({ ...EMPTY_FORM, role: 'TEACHER' });
  const [createError, setCreateError] = useState('');
  const [createOk, setCreateOk] = useState('');
  const [creating, setCreating] = useState(false);

  const loadPending = useCallback(() => {
    // 非管理员不发请求，只提示无权
    if (!isAdmin) return;
    setListLoading(true);
    api.adminUsers(false, page, 20)
      .then((r) => { setData(r); setListError(''); })
      .catch((e) => setListError(describeError(e)))
      .finally(() => setListLoading(false));
  }, [isAdmin, page]);

  useEffect(() => { loadPending(); }, [loadPending, reloadTick]);

  const verify = (id: number) => {
    setVerifyingId(id);
    api.verifyUser(id)
      .then(() => {
        setListError('');
        setReloadTick((t) => t + 1);
      })
      .catch((e) => setListError(describeError(e)))
      .finally(() => setVerifyingId(null));
  };

  const submit = () => {
    setCreateError('');
    setCreateOk('');
    if (!form.username.trim() || !form.displayName.trim() || !form.email.trim() || !form.password) {
      setCreateError('工号、昵称、邮箱、密码均为必填项');
      return;
    }
    if (form.password.length < 8 || form.password.length > 64
        || !/[A-Za-z]/.test(form.password) || !/\d/.test(form.password)) {
      setCreateError('密码需 8–64 位，且同时包含字母和数字');
      return;
    }
    setCreating(true);
    api.createStaff({
      username: form.username.trim(),
      displayName: form.displayName.trim(),
      email: form.email.trim(),
      password: form.password,
      role: form.role as Role,
      college: form.college.trim() || undefined,
    })
      .then((r) => {
        setCreateOk(`账号已创建（用户 ID ${r.id}），可直接用该工号与密码登录。`);
        setForm({ ...EMPTY_FORM, role: 'TEACHER' });
      })
      .catch((e) => setCreateError(describeError(e)))
      .finally(() => setCreating(false));
  };

  // 账号管理（含已禁用）：#46 禁用账号 + 立即下线
  const [all, setAll] = useState<Page<AdminUser> | null>(null);
  const [allPage, setAllPage] = useState(1);
  const [allLoading, setAllLoading] = useState(false);
  const [allError, setAllError] = useState('');
  const [disablingId, setDisablingId] = useState<number | null>(null);
  const [notice, setNotice] = useState('');

  const loadAll = useCallback(() => {
    if (!isAdmin) return;
    setAllLoading(true);
    api.adminUsersAll(allPage, 20)
      .then((r) => { setAll(r); setAllError(''); })
      .catch((e) => setAllError(describeError(e)))
      .finally(() => setAllLoading(false));
  }, [isAdmin, allPage, reloadTick]);

  useEffect(() => { loadAll(); }, [loadAll]);

  const disable = (u: AdminUser) => {
    if (!window.confirm(`确定禁用「${u.displayName}」（${u.maskedUsername}）？该用户已登录的会话会立刻被终止。`)) {
      return;
    }
    setDisablingId(u.id);
    setNotice('');
    api.disableUser(u.id)
      .then((r) => {
        setAllError('');
        setNotice(`已禁用 ${u.displayName}，终止会话 ${r.sessionsTerminated} 个。`);
        setReloadTick((t) => t + 1);
      })
      .catch((e) => setAllError(describeError(e)))
      .finally(() => setDisablingId(null));
  };

  if (authLoading) return <p className="muted">加载中…</p>;  if (!me || !isAdmin) {
    return (
      <div className="form-page">
        <h1>无权访问</h1>
        <p className="muted">该页面仅管理员可访问。如需操作，请使用管理员账号登录。</p>
      </div>
    );
  }

  const totalPages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 0;

  return (
    <div className="form-page admin-page">
      <h1>用户管理</h1>

      <div className="tabs">
        <button className={tab === 'pending' ? 'active' : ''} onClick={() => setTab('pending')}>待认证学生</button>
        <button className={tab === 'accounts' ? 'active' : ''} onClick={() => setTab('accounts')}>账号管理</button>
        <button className={tab === 'create' ? 'active' : ''} onClick={() => setTab('create')}>创建教职工</button>
      </div>

      {tab === 'pending' && (
        <section>
          {listError && (
            <div className="error-box">
              <p className="error">{listError}</p>
              <button onClick={() => setReloadTick((t) => t + 1)}>重试</button>
            </div>
          )}

          {listLoading ? (
            <p className="muted">加载中…</p>
          ) : (
            <>
              <table className="board">
                <thead>
                  <tr><th>工号</th><th>昵称</th><th>角色</th><th>注册时间</th><th>操作</th></tr>
                </thead>
                <tbody>
                  {data?.items.map((u) => (
                    <tr key={u.id}>
                      <td>{u.maskedUsername}</td>
                      <td>{u.displayName}</td>
                      <td>{u.role}</td>
                      <td>{new Date(u.createdAt).toLocaleString('zh-CN')}</td>
                      <td>
                        <button className="primary" disabled={verifyingId === u.id} onClick={() => verify(u.id)}>
                          {verifyingId === u.id ? '处理中…' : '认证通过'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {data && data.items.length === 0 && <p className="muted">当前没有待认证的学生。</p>}
            </>
          )}

          {data && data.total > data.size && (
            <div className="pager">
              <button disabled={page <= 1} onClick={() => setPage(page - 1)}>上一页</button>
              <span>第 {page} / {totalPages} 页 · 共 {data.total} 条</span>
              <button disabled={page >= totalPages} onClick={() => setPage(page + 1)}>下一页</button>
            </div>
          )}
        </section>
      )}

      {tab === 'accounts' && (
        <section>
          {notice && <p className="success">{notice}</p>}
          {allError && (
            <div className="error-box">
              <p className="error">{allError}</p>
              <button onClick={() => setReloadTick((t) => t + 1)}>重试</button>
            </div>
          )}

          {allLoading ? (
            <p className="muted">加载中…</p>
          ) : (
            <>
              <table className="board">
                <thead>
                  <tr><th>学号/工号</th><th>昵称</th><th>角色</th><th>认证</th><th>状态</th><th>操作</th></tr>
                </thead>
                <tbody>
                  {all?.items.map((u) => (
                    <tr key={u.id}>
                      <td>{u.maskedUsername}</td>
                      <td>{u.displayName}</td>
                      <td>{u.role}</td>
                      <td>{u.verified ? '已认证' : '待认证'}</td>
                      <td>{u.status === 'DISABLED' ? '已禁用' : '正常'}</td>
                      <td>
                        <button
                          disabled={u.status === 'DISABLED' || disablingId === u.id}
                          onClick={() => disable(u)}
                        >
                          {disablingId === u.id ? '处理中…' : u.status === 'DISABLED' ? '已禁用' : '禁用'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {all && all.items.length === 0 && <p className="muted">还没有用户。</p>}
            </>
          )}

          {all && all.total > all.size && (
            <div className="pager">
              <button disabled={allPage <= 1} onClick={() => setAllPage(allPage - 1)}>上一页</button>
              <span>第 {allPage} / {Math.max(1, Math.ceil(all.total / all.size))} 页 · 共 {all.total} 条</span>
              <button disabled={allPage >= Math.ceil(all.total / all.size)} onClick={() => setAllPage(allPage + 1)}>下一页</button>
            </div>
          )}
        </section>
      )}

      {tab === 'create' && (
        <section className="admin-form">
          <label>工号 / 用户名（必填）
            <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
          </label>
          <label>昵称（必填）
            <input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
          </label>
          <label>邮箱（必填）
            <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </label>
          <label>初始密码（8–64 位，含字母和数字）
            <input type="password" value={form.password}
                   onChange={(e) => setForm({ ...form, password: e.target.value })} />
          </label>
          <label>角色
            <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
              {STAFF_ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
            </select>
          </label>
          <label>所属学院（可选）
            <input value={form.college} onChange={(e) => setForm({ ...form, college: e.target.value })} />
          </label>

          {createError && <p className="error">{createError}</p>}
          {createOk && <p className="success">{createOk}</p>}

          <button className="primary" disabled={creating} onClick={submit}>
            {creating ? '创建中…' : '创建账号'}
          </button>
        </section>
      )}
    </div>
  );
}
