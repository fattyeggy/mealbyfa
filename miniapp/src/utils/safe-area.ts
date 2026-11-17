/**
 * 顶部安全区（刘海/状态栏/胶囊）。
 *
 * 注意：这里只能用微信原生 wx.* API。
 * 在页面 setup 阶段调用 uni.getSystemInfoSync() 会报 "Cannot read properties of null (reading 'ctx')"，
 * 因此小程序端走 wx.getWindowInfo / wx.getMenuButtonBoundingClientRect，其余端返回默认值。
 */
export function safeTop(extra = 12, avoidCapsule = false): number {
  try {
    // #ifdef MP-WEIXIN
    const w: any = (globalThis as any).wx
    const win: any = w.getWindowInfo ? w.getWindowInfo() : w.getSystemInfoSync()
    if (avoidCapsule) {
      const rect: any = w.getMenuButtonBoundingClientRect ? w.getMenuButtonBoundingClientRect() : null
      if (rect && rect.bottom) {
        return rect.bottom + extra
      }
    }
    return (win && win.statusBarHeight ? win.statusBarHeight : 20) + extra
    // #endif
  } catch (e) {
    // 兜底：常见状态栏高度，避免内容压到刘海
    return 44 + extra
  }
  return extra
}
