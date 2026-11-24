const BASE = (import.meta as any).env?.VITE_API_BASE || '/admin'
const TOKEN_KEY = 'sancan_admin_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

interface ApiResult<T> {
  code: number
  data: T
  msg: string
}

async function request<T>(method: string, path: string, body?: any): Promise<T> {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {})
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const payload = (await res.json()) as ApiResult<T>
  if (payload && payload.code === 0) {
    return payload.data
  }
  if (payload && payload.code === 401) {
    clearToken()
    location.hash = '#/login'
  }
  throw new Error(payload?.msg || `请求失败（${payload?.code ?? res.status}）`)
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: any) => request<T>('POST', path, body),
  put: <T>(path: string, body?: any) => request<T>('PUT', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path)
}

/** 上传单个文件（multipart，字段名 file），返回接口 data。 */
export async function uploadFile<T>(path: string, file: Blob, filename = 'photo.jpg'): Promise<T> {
  const form = new FormData()
  form.append('file', file, filename)
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: getToken() ? { Authorization: `Bearer ${getToken()}` } : {},
    body: form
  })
  let payload: ApiResult<T> | null = null
  try {
    payload = (await res.json()) as ApiResult<T>
  } catch {
    throw new Error(res.status === 413 ? '照片太大，请重拍' : `上传失败（${res.status}）`)
  }
  if (payload && payload.code === 0) {
    return payload.data
  }
  if (payload && payload.code === 401) {
    clearToken()
    location.hash = '#/login'
  }
  throw new Error(payload?.msg || `上传失败（${res.status}）`)
}

/** 带鉴权读取图片等二进制，返回可用于 <img src> 的 object URL，用完需 revokeObjectURL。 */
export async function fetchBlobUrl(path: string): Promise<string> {
  const res = await fetch(BASE + path, {
    headers: getToken() ? { Authorization: `Bearer ${getToken()}` } : {}
  })
  if (!res.ok) {
    throw new Error(`读取失败（${res.status}）`)
  }
  return URL.createObjectURL(await res.blob())
}

export async function login(username: string, password: string) {
  const data = await request<{ token: string; nickname: string; roles: string[] }>('POST', '/auth/login', {
    username,
    password
  })
  setToken(data.token)
  localStorage.setItem('sancan_admin_name', data.nickname || username)
  localStorage.setItem('sancan_admin_role', (data.roles && data.roles[0]) || 'OPS')
  return data
}

export function adminName() {
  return localStorage.getItem('sancan_admin_name') || '管理员'
}

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  OPS: '运营',
  NUTRITIONIST: '营养计划',
  KITCHEN: '厨房负责人',
  PURCHASER: '采购',
  FINANCE: '财务',
  COURIER: '配送',
  AUDITOR: '只读审计'
}

export function adminRole() {
  return localStorage.getItem('sancan_admin_role') || 'OPS'
}

export function adminRoleLabel() {
  return ROLE_LABELS[adminRole()] || adminRole()
}

export function money(value: any) {
  return Number(value || 0).toFixed(2)
}

/** 带鉴权下载 CSV：先 fetch 成 blob，再触发浏览器下载。 */
export async function downloadCsv(path: string, filename: string) {
  const res = await fetch(BASE + path, {
    headers: getToken() ? { Authorization: `Bearer ${getToken()}` } : {}
  })
  if (!res.ok) {
    throw new Error('导出失败（' + res.status + '）')
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

export interface PageResult<T> {
  list: T[]
  total: number
}
