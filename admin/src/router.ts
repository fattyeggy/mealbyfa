import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from './api'

const routes = [
  { path: '/login', component: () => import('./views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('./layout/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/menus' },
      { path: 'menus', component: () => import('./views/Menus.vue'), meta: { title: '每周菜单' } },
      { path: 'catalog', component: () => import('./views/Catalog.vue'), meta: { title: '菜品与食材' } },
      { path: 'menus/:id', component: () => import('./views/MenuDetail.vue'), meta: { title: '菜单详情' } },
      { path: 'reviews', component: () => import('./views/Reviews.vue'), meta: { title: '复核事项' } },
      { path: 'pricing', component: () => import('./views/Pricing.vue'), meta: { title: '定价版本' } },
      { path: 'orders', component: () => import('./views/Orders.vue'), meta: { title: '订单' } },
      { path: 'kitchen', component: () => import('./views/Kitchen.vue'), meta: { title: '厨房生产' } },
      { path: 'cost', component: () => import('./views/Cost.vue'), meta: { title: '成本报表' } },
      { path: 'members', component: () => import('./views/MemberOnboard.vue'), meta: { title: '会员建档' } },
      { path: 'users', component: () => import('./views/Users.vue'), meta: { title: '用户与余额' } },
      { path: 'feedback', component: () => import('./views/Feedback.vue'), meta: { title: '完餐反馈' } },
      { path: 'audit', component: () => import('./views/Audit.vue'), meta: { title: '审计日志' } }
    ]
  }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!getToken()) return { path: '/login' }
  return true
})

export default router
