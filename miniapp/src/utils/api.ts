/**
 * 接口封装。
 * - H5 开发态走 vite 代理 /api，避免跨域；
 * - 小程序构建时用 VITE_API_BASE 指向真实域名。
 */
const ENV = (import.meta as any).env || {}
const BASE = ENV.VITE_API_BASE || '/api'
const CLOUD_ENV = ENV.VITE_CLOUD_ENV || ''
const CLOUD_SERVICE = ENV.VITE_CLOUD_SERVICE || ''
const USE_CLOUD = Boolean(CLOUD_ENV && CLOUD_SERVICE)
let cloudReady = false

function initCloud() {
  if (!USE_CLOUD || cloudReady) return
  const wx = (globalThis as any).wx
  if (wx?.cloud?.init) {
    wx.cloud.init({ env: CLOUD_ENV, traceUser: true })
    cloudReady = true
  }
}

const TOKEN_KEY = 'sancan_token'
const CODE_KEY = 'sancan_demo_code'

export interface ApiResult<T> {
  code: number
  data: T
  msg: string
}

let token = (uni.getStorageSync(TOKEN_KEY) as string) || ''

export function getToken() {
  return token
}

export function setToken(value: string) {
  token = value
  uni.setStorageSync(TOKEN_KEY, value)
}

export function clearToken() {
  token = ''
  uni.removeStorageSync(TOKEN_KEY)
}

function handlePayload<T>(payload: ApiResult<T>, statusCode: number, resolve: (v: T) => void,
                          reject: (e: Error) => void, retry: () => void) {
  if (payload && payload.code === 0) {
    resolve(payload.data)
    return
  }
  if (payload && payload.code === 401) {
    retry()
    return
  }
  reject(new Error(payload?.msg || `请求失败（${payload?.code ?? statusCode}）`))
}

function cloudRequest<T>(method: 'GET' | 'POST' | 'PUT', path: string, body?: any,
                         retried = false): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    initCloud()
    const wx = (globalThis as any).wx
    if (!wx?.cloud?.callContainer) {
      reject(new Error('当前环境不支持云托管调用'))
      return
    }
    wx.cloud.callContainer({
      config: { env: CLOUD_ENV },
      path,
      method,
      data: body,
      header: {
        'Content-Type': 'application/json',
        'X-WX-SERVICE': CLOUD_SERVICE,
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success: async (res: any) => {
        let payload = res.data
        if (typeof payload === 'string') {
          try { payload = JSON.parse(payload) } catch (e) { /* 保留原始内容 */ }
        }
        if (payload && payload.code === 401 && !retried) {
          try {
            await login()
            resolve(await cloudRequest<T>(method, path, body, true))
          } catch (e) {
            reject(e)
          }
          return
        }
        handlePayload(payload, res.statusCode, resolve, reject, () => reject(new Error('登录失效')))
      },
      fail: (err: any) => reject(new Error(err?.errMsg || '云托管请求失败'))
    })
  })
}

function request<T>(method: 'GET' | 'POST' | 'PUT', path: string, body?: any, retried = false): Promise<T> {
  // #ifdef MP-WEIXIN
  if (USE_CLOUD) {
    return cloudRequest<T>(method, path, body, retried)
  }
  // #endif
  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: BASE + path,
      method,
      data: body,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success: async (res: any) => {
        const payload = res.data as ApiResult<T>
        if (payload && payload.code === 0) {
          resolve(payload.data)
          return
        }
        // 令牌过期：自动重新登录一次
        if (payload && payload.code === 401 && !retried) {
          try {
            await login()
            resolve(await request<T>(method, path, body, true))
          } catch (e) {
            reject(e)
          }
          return
        }
        reject(new Error(payload?.msg || `请求失败（${payload?.code ?? res.statusCode}）`))
      },
      fail: (err: any) => reject(new Error(err?.errMsg || '网络异常'))
    })
  })
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: any) => request<T>('POST', path, body),
  put: <T>(path: string, body?: any) => request<T>('PUT', path, body)
}

function wxLoginCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (res: any) => (res?.code ? resolve(res.code) : reject(new Error('微信登录未返回 code'))),
      fail: (err: any) => reject(new Error(err?.errMsg || '微信登录失败'))
    })
  })
}

/**
 * 登录：
 * - 微信小程序：uni.login 取 code，服务端 code2Session 换 openid；
 * - H5 本地开发：允许 VITE_DEMO_CODE 模拟（体验/生产构建不要设置该变量）。
 */
export async function login(code?: string) {
  let loginCode = code
  // #ifdef MP-WEIXIN
  if (!loginCode) loginCode = await wxLoginCode()
  // #endif
  if (!loginCode) {
    const fixed = (import.meta as any).env?.VITE_DEMO_CODE
    const isDev = Boolean((import.meta as any).env?.DEV)
    if (!isDev && !fixed) {
      throw new Error('请在微信中打开小程序登录')
    }
    loginCode = fixed || newDemoCode()
  }
  uni.setStorageSync(CODE_KEY, loginCode)
  const data = await request<{ token: string; userId: number; nickname: string }>(
    'POST', '/app/auth/login', { code: loginCode }
  )
  setToken(data.token)
  return data
}

/** 换一个演示账号（便于演示不同体重/蛋白目标）。 */
export async function switchDemoAccount() {
  return login(newDemoCode())
}

function newDemoCode() {
  // 固定账号便于截图与联调：VITE_DEMO_CODE=demo-h5-001
  const fixed = (import.meta as any).env?.VITE_DEMO_CODE
  if (fixed) return fixed
  return 'h5-' + Math.random().toString(36).slice(2, 10)
}

export function hasSession() {
  return !!token
}
