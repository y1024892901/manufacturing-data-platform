import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import ShellView from '../views/ShellView.vue'
import DashboardView from '../views/DashboardView.vue'
import ApprovalCenterView from '../views/ApprovalCenterView.vue'
import MaterialView from '../views/MaterialView.vue'
import SystemWorkspaceView from '../views/SystemWorkspaceView.vue'
import BomView from '../views/BomView.vue'
import EcnView from '../views/EcnView.vue'
import OperationsView from '../views/OperationsView.vue'
const router = createRouter({ history: createWebHistory(), routes: [{ path: '/login', component: LoginView }, { path: '/', component: ShellView, children: [{ path: '', component: DashboardView }, { path: 'approval', component: ApprovalCenterView }, { path: 'mdm/materials', component: MaterialView }, { path: 'mdm/boms', component: BomView }, { path: 'plm/ecns', component: EcnView }, { path:'ops/:kind',component:OperationsView,props:true }, { path: 'system/:code', component: SystemWorkspaceView, props: true }] }] })
router.beforeEach((to) => to.path !== '/login' && !localStorage.getItem('mfg_token') ? '/login' : true)
export default router
