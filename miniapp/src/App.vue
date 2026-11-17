<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { bootstrap, session } from '@/store/session'

onLaunch(async () => {
  await bootstrap()
  // 首次进入或未建档：直接进入 onboarding（先看菜单 slide，再建档）
  if (!session.profile?.onboardingCompleted) {
    setTimeout(() => uni.reLaunch({ url: '/pages/onboarding/index' }), 0)
  }
})
</script>

<style>
page {
  overflow-x: hidden;
  background: #f4f1e9;
  color: #1c3b2e;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
}

/* H5 会把 view/text 渲染成 uni-view/uni-text，用通配选择器保证盒模型一致；
   微信小程序 WXSS 不支持 * 选择器，因此只在 H5 编译，并对小程序元素显式声明 */
/* #ifdef H5 */
* {
  box-sizing: border-box;
}
/* #endif */
view,
text,
image,
scroll-view,
input,
button {
  box-sizing: border-box;
}

.page {
  width: 100%;
  overflow-x: hidden;
  min-height: 100vh;
  padding: 0 20px 96px;
  max-width: 520px;
  margin: 0 auto;
}

.brand {
  font-size: 12px;
  letter-spacing: 2px;
  color: #556A5E;
}

.title {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 1px;
}

.card {
  background: #fff;
  border-radius: 20px;
  padding: 16px;
  box-shadow: 0 6px 20px rgba(28, 59, 46, 0.06);
}

.hero {
  background: #032C0A;
  border-radius: 20px;
  padding: 22px;
  color: #F4F1E9;
  margin-top: 14px;
}

.hero-week {
  font-size: 13px;
  opacity: 0.75;
  letter-spacing: 1px;
}

.hero-theme {
  font-size: 30px;
  font-weight: 700;
  margin-top: 6px;
  letter-spacing: 1px;
}

.hero-desc {
  font-size: 13px;
  opacity: 0.8;
  margin-top: 8px;
  line-height: 1.6;
}

.pill {
  display: inline-flex;
  align-items: center;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 12px;
  background: #EEF3EE;
  color: #556A5E;
}

.pill-active {
  background: #032C0A;
  color: #fff;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 18px 0 10px;
  color: #1c3b2e;
}

.muted {
  color: #556A5E;
  font-size: 13px;
}

.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 48px;
  border-radius: 999px;
  background: #032C0A;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
}

.btn-ghost {
  background: #fff;
  color: #032C0A;
  border: 1px solid #E2E6DC;
}

.btn:active {
  opacity: 0.88;
}

.row {
  display: flex;
  align-items: center;
}

.between {
  justify-content: space-between;
}

.grow {
  flex: 1;
}

.divider {
  height: 1px;
  background: #EEF3EE;
  margin: 12px 0;
}

.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 8px;
  background: #eef3ee;
  color: #556A5E;
  font-size: 12px;
}

.tag-warn {
  background: #fbf0e2;
  color: #875A1F;
}
</style>
