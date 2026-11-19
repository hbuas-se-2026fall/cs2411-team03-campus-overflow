import { useEffect, useState } from 'react';
import { BrowserRouter, Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth';
import { api } from './api';
import { QuestionListPage } from './pages/QuestionListPage';
import { QuestionDetailPage } from './pages/QuestionDetailPage';
import { AskPage } from './pages/AskPage';
import { LoginPage } from './pages/LoginPage';
import { LeaderboardPage } from './pages/LeaderboardPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { ReputationPage } from './pages/ReputationPage';

function Header() {
  const { me, logout } = useAuth();
  const navigate = useNavigate();
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    if (!me) return;
    const load = () => api.unreadCount().then((r) => setUnread(r.count)).catch(() => undefined);
    load();

    // SSE 实时通道（#49）：连接正常时不轮询；断开（onerror）后由 30 秒轮询兜底，EventSource 自身也会重连
    let live = false;
    const stream = new EventSource('/api/v1/notifications/stream', { withCredentials: true });
    stream.onopen = () => { live = true; };
    stream.onmessage = () => {
      live = true;
      load();
    };
    stream.onerror = () => { live = false; };

    const timer = setInterval(() => { if (!live) load(); }, 30_000);
    const onChanged = () => load();
    // 通知页标记已读后立即刷新徽标
    window.addEventListener('co:unread-changed', onChanged);
    return () => {
      clearInterval(timer);
      window.removeEventListener('co:unread-changed', onChanged);
      stream.close();
    };
  }, [me]);

  return (
    <header className="topbar">
      <Link className="brand" to="/">CampusOverflow</Link>
      <nav>
        <Link to="/">问题</Link>
        <Link to="/leaderboard">排行榜</Link>
        {me && <Link to="/notifications">通知{unread > 0 && <span className="badge">{unread}</span>}</Link>}
      </nav>
      <div className="spacer" />
      {me ? (
        <div className="user">
          <Link to={`/users/${me.id}/reputation`}>{me.displayName}</Link>
          <span className="role">{me.role}</span>
          <button onClick={() => logout().then(() => navigate('/'))}>退出</button>
        </div>
      ) : (
        <Link className="primary" to="/login">登录 / 注册</Link>
      )}
    </header>
  );
}

function RequireAuth({ children }: { children: JSX.Element }) {
  const { me, loading } = useAuth();
  if (loading) return <p className="muted">加载中…</p>;
  return me ? children : <Navigate to="/login" replace />;
}

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Header />
        <main>
          <Routes>
            <Route path="/" element={<QuestionListPage />} />
            <Route path="/questions/:id" element={<QuestionDetailPage />} />
            <Route path="/ask" element={<RequireAuth><AskPage /></RequireAuth>} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/leaderboard" element={<LeaderboardPage />} />
            <Route path="/users/:id/reputation" element={<ReputationPage />} />
            <Route path="/notifications" element={<RequireAuth><NotificationsPage /></RequireAuth>} />
          </Routes>
        </main>
      </AuthProvider>
    </BrowserRouter>
  );
}
