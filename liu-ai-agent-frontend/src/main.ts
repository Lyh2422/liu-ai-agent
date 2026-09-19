import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import routes, { guardRoute } from './router'

const router = createRouter({
  history: createWebHistory(),
  routes
})
router.beforeEach(guardRoute)

createApp(App)
  .use(router)
  .mount('#app')

