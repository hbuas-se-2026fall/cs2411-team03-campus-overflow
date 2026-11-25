import { useState, type FormEvent } from 'react';
import { api, describeError } from '../api';
import { useAuth } from '../auth';

/**
 * 我的资料（PB-41 / #47）。
 *
 * 可改的只有昵称、邮箱、学院、班级——学号、角色、认证状态由后端按「自助范围」排除，
 * 页面这里不做二次隐藏，让用户看得见哪些是自己不能改的，比藏起来更好解释。
 */
export function ProfilePage() {
  const { me, refresh } = useAuth();
  const [displayName, setDisplayName] = useState(me?.displayName ?? '');
  const [email, setEmail] = useState(me?.email ?? '');
  const [college, setCollege] = useState(me?.college ?? '');
  const [className, setClassName] = useState(me?.className ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  if (!me) return null;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      await api.updateMe({
        displayName,
        email,
        college: college.trim() || undefined,
        className: className.trim() || undefined,
      });
      await refresh(); // 顶栏昵称、认证徽章跟着变，不用重新登录
      setSaved(true);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="profile-page">
      <h1>我的资料</h1>

      <div className="profile-meta">
        <span>学号/工号：<b>{me.username}</b><span className="muted">（登录凭据，不可修改）</span></span>
        <span>角色：<b>{me.role}</b></span>
        <span>校园认证：<b>{me.verified ? '已认证' : '待管理员认证'}</b></span>
        {me.courses?.length > 0 && (
          <span>课程：<b>{me.courses.map((c) => c.name ?? `#${c.id}`).join('、')}</b></span>
        )}
      </div>

      <form className="admin-form profile-form" onSubmit={submit}>
        <label>
          昵称
          <input value={displayName} onChange={(e) => { setDisplayName(e.target.value); setSaved(false); }}
                 maxLength={20} required />
          <span className="hint">2–20 字，全局唯一，不能含 @ 或空格</span>
        </label>
        <label>
          邮箱
          <input type="email" value={email} onChange={(e) => { setEmail(e.target.value); setSaved(false); }}
                 maxLength={100} required />
          <span className="hint">全局唯一，用于通知与账号找回</span>
        </label>
        <label>
          学院
          <input value={college} onChange={(e) => { setCollege(e.target.value); setSaved(false); }}
                 maxLength={50} placeholder="选填" />
        </label>
        <label>
          班级
          <input value={className} onChange={(e) => { setClassName(e.target.value); setSaved(false); }}
                 maxLength={50} placeholder="选填" />
        </label>

        {error && <p className="error">{error}</p>}
        {saved && <p className="success">已保存</p>}

        <button type="submit" disabled={saving}>{saving ? '保存中…' : '保存'}</button>
      </form>
    </section>
  );
}
