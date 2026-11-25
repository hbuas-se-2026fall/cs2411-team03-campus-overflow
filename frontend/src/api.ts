import type {
  AdminUser, Bounty, LeaderboardEntry, LedgerEntry, Me, Notification, Page, QuestionDetail, QuestionSummary,
  RelatedQuestion, ReputationProfile, Role, SearchResult,
} from './types';

/** 后端以 RFC 9457 Problem Details 返回错误，这里统一转成带业务码的异常。 */
export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message);
  }
}

/** 把后端错误（RFC 9457 + 业务 code）映射成用户可读的中文提示。 */
const ERROR_MESSAGES: Record<string, string> = {
  'AUTH-401': '请先登录后再操作',
  'AUTH-403-CSRF': '会话已过期，请刷新页面后重试',
  'AUTH-429': '操作过于频繁，请稍后再试',
  'QA-1001': '标题长度不合法（1–150 字符）',
  'QA-1002': '正文长度不合法（最多 30000 字符）',
  'QA-1005': '标签数量须为 1–5 个',
  'QA-1015': '不能给自己的内容投票',
  'QA-2002': '该问题已采纳，不能重复操作',
  'QA-3001': '只有提问者或课程教师/助教可以执行此操作',
  'QA-403': '没有权限编辑/删除该内容',
  'QA-404': '内容不存在或已被删除',
  'ID-1002': '昵称需 2–20 字，且不能包含 @ 或空格',
  'ID-1003': '邮箱格式不正确或过长',
  'ID-2001': '工号、昵称或邮箱已被使用',
  'ID-2002': '该昵称已被占用，换一个试试',
  'ID-2003': '该邮箱已被占用',
  'ID-3002': '只能修改本人的资料',
  'ID-1004': '密码需 8–64 位，且同时包含字母和数字',
  'ID-3001': '仅管理员可执行该操作',
  'ID-4001': '用户不存在',
  'SYS-409-CONCURRENT': '内容已被他人修改，请刷新后重试',
  'SYS-409-DUPLICATE': '检测到重复提交，请勿重复操作',
  'SYS-000': '服务暂时不可用，请稍后再试',
};

export function describeError(e: unknown): string {
  if (e instanceof ApiError) {
    return ERROR_MESSAGES[e.code] ?? (e.message || `操作失败（${e.code}）`);
  }
  return e instanceof Error ? e.message : '操作失败，请稍后再试';
}

function cookie(name: string): string | undefined {
  return document.cookie.split('; ').find((c) => c.startsWith(`${name}=`))?.split('=')[1];
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') {
    // CSRF 双提交：从 XSRF-TOKEN Cookie 取出 token 放进请求头
    let token = cookie('XSRF-TOKEN');
    if (!token) {
      await fetch('/api/v1/auth/csrf', { credentials: 'include' });
      token = cookie('XSRF-TOKEN');
    }
    if (token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token);
  }
  const res = await fetch(`/api${path}`, {
    method, headers, credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) {
    throw new ApiError(res.status, data?.code ?? 'SYS-000', data?.detail ?? '请求失败');
  }
  return data as T;
}

const get = <T>(path: string) => request<T>('GET', path);

export const api = {
  csrf: () => get<{ token: string }>('/v1/auth/csrf'),
  register: (body: Record<string, string>) => request<{ id: number }>('POST', '/v1/auth/register', body),
  login: (username: string, password: string) => request<Me>('POST', '/v1/auth/login', { username, password }),
  logout: () => request<void>('POST', '/v1/auth/logout'),
  me: () => get<Me>('/v1/auth/me'),
  updateMe: (body: { displayName: string; email: string; college?: string; className?: string }) =>
    request<Me>('PATCH', '/v1/auth/me', body),

  questions: (page = 1, courseId?: number) =>
    get<Page<QuestionSummary>>(`/v1/questions?page=${page}${courseId ? `&courseId=${courseId}` : ''}`),
  question: (id: number) => get<QuestionDetail>(`/v1/questions/${id}`),
  ask: (body: { courseId?: number; title: string; body: string; tags: string[] }) =>
    request<{ id: number }>('POST', '/v1/questions', body),
  deleteQuestion: (id: number) => request<void>('DELETE', `/v1/questions/${id}`),
  editQuestion: (id: number, body: { title: string; body: string; tags: string[] }) =>
    request<void>('PUT', `/v1/questions/${id}`, body),
  editAnswer: (id: number, body: string) => request<void>('PUT', `/v1/answers/${id}`, { body }),
  deleteAnswer: (id: number) => request<void>('DELETE', `/v1/answers/${id}`),
  answer: (questionId: number, body: string) =>
    request<{ id: number }>('POST', `/v1/questions/${questionId}/answers`, { body }),
  accept: (questionId: number, answerId: number) =>
    request<void>('PUT', `/v1/questions/${questionId}/accepted-answer`, { answerId }),
  endorse: (answerId: number) => request<void>('POST', `/v1/answers/${answerId}/endorsement`, {}),
  vote: (targetType: 'QUESTION' | 'ANSWER', targetId: number, value: number) =>
    request<{ myVote: number; scoreDelta: number }>('PUT', '/v1/votes', { targetType, targetId, value }),
  comment: (targetType: 'QUESTION' | 'ANSWER', targetId: number, body: string, parentId?: number) =>
    request<{ id: number }>('POST', '/v1/comments', { targetType, targetId, body, parentId }),

  search: (q: string, params: {
    courseId?: number; tag?: string; sort?: string; unanswered?: boolean; bounty?: boolean;
    page?: number; size?: number;
  } = {}) => {
    const search = new URLSearchParams({ q, page: String(params.page ?? 1) });
    if (params.courseId) search.set('courseId', String(params.courseId));
    if (params.tag) search.set('tag', params.tag);
    if (params.sort) search.set('sort', params.sort);
    if (params.unanswered) search.set('unanswered', 'true');
    if (params.bounty) search.set('bounty', 'true');
    if (params.size) search.set('size', String(params.size));
    return get<Page<SearchResult>>(`/v1/search/questions?${search}`);
  },
  related: (questionId: number, limit = 5) =>
    get<RelatedQuestion[]>(`/v1/questions/${questionId}/related?limit=${limit}`),
  popularTags: () => get<{ name: string; questionCount: number }[]>('/v1/tags/popular?limit=15'),

  leaderboard: (period: string) => get<LeaderboardEntry[]>(`/v1/leaderboard?period=${period}`),
  reputation: (userId: number) => get<ReputationProfile>(`/v1/users/${userId}/reputation`),
  ledger: (userId: number, page = 1, size = 20) =>
    get<Page<LedgerEntry>>(`/v1/users/${userId}/reputation/ledger?page=${page}&size=${size}`),
  bounty: (questionId: number, points: number, days: number) =>
    request<{ id: number }>('POST', `/v1/questions/${questionId}/bounty`, { points, days }),
  bountyLatest: (questionId: number) =>
    get<Bounty | undefined>(`/v1/questions/${questionId}/bounty`),

  notifications: (page = 1, size = 20) =>
    get<Page<Notification>>(`/v1/notifications?page=${page}&size=${size}`),
  unreadCount: () => get<{ count: number }>('/v1/notifications/unread-count'),
  markRead: (id: number) => request<void>('POST', `/v1/notifications/${id}/read`, {}),
  readAll: () => request<{ updated: number }>('POST', '/v1/notifications/read-all', {}),

  adminUsers: (verified = false, page = 1, size = 20) =>
    get<Page<AdminUser>>(`/v1/admin/users?verified=${verified}&page=${page}&size=${size}`),
  verifyUser: (id: number) => request<void>('POST', `/v1/admin/users/${id}/verify`, {}),
  createStaff: (body: {
    username: string; displayName: string; email: string; password: string; role: Role; college?: string;
  }) => request<{ id: number }>('POST', '/v1/admin/users', body),
  changeRole: (id: number, role: Role) => request<void>('PUT', `/v1/admin/users/${id}/role`, { role }),
};
