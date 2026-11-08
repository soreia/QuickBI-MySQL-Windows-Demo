<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api, csrf, ApiFailure } from './api'
type Row = Record<string, any>
const me=ref<Row|null>(null), users=ref<Row[]>([]), reportData=ref<Row>({items:[],total:0}), q=ref(''), page=ref(1)
const tab=ref('reports'), busy=ref(false), error=ref(''), notice=ref(''), adminUsers=ref<Row[]>([]), jobs=ref<Row[]>([]), auditData=ref<Row>({items:[],total:0})
const auditPage=ref(1), eventType=ref(''), auditQuery=ref(''), config=ref<Row>({}), reason=ref('本地演示配置调整'), lab=ref<Row>({biUsers:[],grants:[],reports:[]})
const embed=ref<Row|null>(null), frame=ref<HTMLIFrameElement|null>(null), navigation=ref(''), signalSent=ref(false), jobItems=ref<Row[]>([])
const selectedUser=ref('u-alice'), mapTarget=ref('bi-free'), mapReason=ref('修正本地演示关联'), selectedMapping=ref<Row|null>(null)
const mockUser=ref('bi-alice'), mockReport=ref('rep-sales'), mockGranted=ref(false)
let poll: ReturnType<typeof setInterval> | undefined, timeout: ReturnType<typeof setTimeout> | undefined
let channel: BroadcastChannel | undefined
const admin=computed(()=>me.value?.roles?.split(',').includes('ADMIN'))
const auditor=computed(()=>me.value?.roles?.split(',').some((r:string)=>['ADMIN','AUDITOR'].includes(r)))
function closeEmbed(){ embed.value=null; navigation.value=''; signalSent.value=false; if(timeout) clearTimeout(timeout) }
function fail(e:unknown){
  if(e instanceof ApiFailure){
    error.value=`${e.message} · ${e.code} · requestId=${e.requestId}`
    if(e.status===401){ me.value=null; closeEmbed(); channel?.postMessage('logout'); void context() }
  } else error.value='网络不可用，请检查前后端与数据库。'
}
async function run(action:()=>Promise<void>){ busy.value=true; error.value=''; notice.value=''; try{ await action() }catch(e){ fail(e) }finally{ busy.value=false } }
async function context(){const c=await api<Row>('/auth/context'); users.value=c.users; csrf(c.csrfToken)}
async function loadReports(){reportData.value=await api(`/api/v1/reports?q=${encodeURIComponent(q.value)}&page=${page.value}&pageSize=6`)}
async function restore(){const user=await api<Row>('/api/v1/me'); me.value=user; csrf(user.csrfToken)}
async function login(){await run(async()=>{ await api('/auth/login','POST',{userId:selectedUser.value}); await restore(); tab.value=me.value?.mappingStatus==='MAPPED'?'reports':admin.value?'users':auditor.value?'audit':'reports'; await loadTab() })}
async function logout(){await run(async()=>{await api('/api/v1/auth/logout','POST'); me.value=null; closeEmbed(); channel?.postMessage('logout'); await context()})}
async function openReport(reportId:string){await run(async()=>{
  closeEmbed()
  embed.value=await api(`/api/v1/reports/${encodeURIComponent(reportId)}/embed-sessions`,'POST',{}, {'Idempotency-Key':crypto.randomUUID()})
  navigation.value='正在导航到模拟 Quick BI…'
  timeout=setTimeout(()=>{if(!signalSent.value) navigation.value='加载时间较长；跨域场景无法读取iframe内部业务状态。'},10000)
})}
async function navigated(){if(!embed.value||signalSent.value)return; signalSent.value=true; navigation.value='iframe 已导航（不代表业务报表加载成功）'; try{await api('/api/v1/embed-events','POST',{embedSessionId:embed.value.embedSessionId})}catch(e){fail(e)}}
async function loadTab(){
  closeEmbed()
  if(tab.value==='reports' && me.value?.mappingStatus==='MAPPED') await loadReports()
  if(tab.value==='users') adminUsers.value=(await api<Row>('/api/v1/admin/users')).items
  if(tab.value==='jobs') jobs.value=(await api<Row>('/api/v1/admin/sync-jobs')).items
  if(tab.value==='audit') auditData.value=await api(`/api/v1/admin/audit-events?page=${auditPage.value}&pageSize=12&eventType=${encodeURIComponent(eventType.value)}&q=${encodeURIComponent(auditQuery.value)}`)
  if(tab.value==='config') config.value=await api('/api/v1/admin/config')
  if(tab.value==='lab') lab.value=await api('/api/v1/demo/state')
}
async function changeTab(value:string){tab.value=value; await run(loadTab)}
async function submitMapping(){if(!selectedMapping.value)return; await run(async()=>{
  await api(`/api/v1/admin/users/${selectedMapping.value!.userId}/mapping`,'PUT',{quickbiUserId:mapTarget.value,expectedVersion:selectedMapping.value!.version,reason:mapReason.value})
  selectedMapping.value=null; await loadTab(); notice.value='映射已更新；目标用户所有门户会话已撤销。'
})}
async function sync(type:string,userId?:string){await run(async()=>{
  const result=await api<Row>('/api/v1/admin/sync-jobs','POST',{type,...(userId?{userId}:{})}); notice.value=`任务 ${result.taskId}；去重=${result.deduplicated}`
  if(tab.value==='jobs') await loadTab()
})}
async function retry(id:string){await run(async()=>{ const j=await api<Row>(`/api/v1/admin/sync-jobs/${id}/retry`,'POST',{}); await loadTab(); notice.value=`补跑任务：${j.taskId}` })}
async function saveConfig(){await run(async()=>{
  config.value=await api('/api/v1/admin/config','PATCH',{expectedVersion:config.value.version,idleMinutes:Number(config.value.idleMinutes),absoluteHours:Number(config.value.absoluteHours),listCacheSeconds:Number(config.value.listCacheSeconds),reason:reason.value}); notice.value='配置已保存，版本已更新。'
})}
async function setFault(mode:string){await run(async()=>{await api('/api/v1/demo/fault','POST',{mode}); await loadTab(); notice.value=`Mock模式已切换：${mode}`})}
async function grant(){await run(async()=>{await api('/api/v1/demo/grant','POST',{biUserId:mockUser.value,reportId:mockReport.value,granted:mockGranted.value});await loadTab();notice.value='仅改变 Mock 厂商授权；门户列表快照未主动刷新，可验证旧列表不能放行新签票。'})}
function fmt(value:unknown){return value?new Date(String(value).endsWith('Z')?String(value):String(value)+'Z').toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false}):'—'}
onMounted(async()=>{
  if('BroadcastChannel' in window){channel=new BroadcastChannel('quickbi-demo');channel.onmessage=()=>{me.value=null;closeEmbed();void context()}}
  try{await restore();tab.value=admin.value?'users':auditor.value?'audit':'reports';await loadTab()}catch(e){if(!(e instanceof ApiFailure&&e.status===401))fail(e); await context()}
  poll=setInterval(async()=>{
    if(!me.value)return
    try{await restore(); if(tab.value==='jobs') jobs.value=(await api<Row>('/api/v1/admin/sync-jobs')).items}catch(e){fail(e)}
  },15000)
})
onBeforeUnmount(()=>{if(poll)clearInterval(poll);if(timeout)clearTimeout(timeout);channel?.close()})
</script>
<template>
  <div class="ribbon">LOCAL MOCK DEMO · MySQL 8.4 · 不连接 Entra ID / Quick BI · 仅合成数据</div>
  <div v-if="!me" class="welcome">
    <div class="brandmark">Q<span>BI</span></div><p class="eyebrow">ENTERPRISE REPORT PORTAL</p><h1>一个入口，<br>验证完整访问闭环。</h1>
    <p class="muted">本地身份模拟 → MySQL 映射 → 授权快照 → 在线鉴权 → 单次票据</p>
    <section class="login panel"><h2>选择本地演示身份</h2><p class="muted">无密码 Mock 登录，不代表已完成企业认证。请勿部署到公网。</p>
      <label>演示账号<select v-model="selectedUser"><option v-for="u in users" :key="u.id" :value="u.id">{{u.display_name}} · {{u.roles}}</option></select></label>
      <button class="primary" :disabled="busy" @click="login">进入演示门户 →</button>
      <p class="tiny">Alice / Bob 权限不同；管理员、审计员即使无 BI 映射，也可执行相应职责。</p>
    </section>
    <p v-if="error" role="alert" class="error">{{error}}</p>
  </div>
  <template v-else>
    <header><div class="brand"><b>Q<span>BI</span></b><div>Report Portal<small>MYSQL LOCAL EDITION</small></div></div><div class="identity"><b>{{me.displayName}}</b><span class="badge">{{me.roles}}</span><button @click="logout" :disabled="busy">退出</button></div></header>
    <div class="workspace">
      <aside><p class="eyebrow">WORKSPACE</p><button :class="{active:tab==='reports'}" @click="changeTab('reports')">▦ 我的报表</button><template v-if="admin"><button :class="{active:tab==='users'}" @click="changeTab('users')">◉ 用户与映射</button><button :class="{active:tab==='jobs'}" @click="changeTab('jobs')">↻ 同步任务</button></template><button v-if="auditor" :class="{active:tab==='audit'}" @click="changeTab('audit')">≡ 审计日志</button><template v-if="admin"><button :class="{active:tab==='config'}" @click="changeTab('config')">⚙ 系统配置</button><button :class="{active:tab==='lab'}" @click="changeTab('lab')">◇ Mock 实验区</button></template><div class="aside-note">权限权威：Mock BI<br>门户不代理业务报表数据<br><br>{{me.isid}}<br>映射：{{me.mappingStatus}}</div></aside>
      <main>
        <p class="eyebrow">QUICK BI / {{tab.toUpperCase()}}</p><div class="titlebar"><div><h1>{{({reports:'我的授权报表',users:'用户与身份映射',jobs:'同步任务',audit:'访问审计',config:'系统配置',lab:'Mock 厂商实验区'} as Record<string,string>)[tab]}}</h1><p class="muted">会话检查每15秒执行，不延长业务空闲时间。时间展示：Asia/Shanghai。</p></div><button :disabled="busy" @click="run(loadTab)">刷新当前页</button></div>
        <div v-if="error" class="error" role="alert">{{error}}</div><div v-if="notice" class="notice" role="status">{{notice}}</div><div v-if="busy" class="loading">正在处理，请稍候…</div>
        <template v-if="tab==='reports'">
          <section v-if="me.mappingStatus!=='MAPPED'" class="panel empty"><h2>已完成 Mock 登录，尚无可用 BI 映射</h2><p>请由门户管理员维护身份关联；报表权限须在 Quick BI（此处为 Mock 实验区）维护。</p></section>
          <template v-else>
            <form class="toolbar" @submit.prevent="page=1;run(loadReports)"><input v-model="q" maxlength="100" placeholder="搜索报表名称或描述"><button :disabled="busy">搜索</button><span class="muted">共 {{reportData.total}} 项</span></form>
            <p class="tiny">完整快照时间：{{fmt(reportData.snapshotAt)}} · 每次打开仍重新在线鉴权</p>
            <div class="cards"><article class="report panel" v-for="r in reportData.items" :key="r.reportId"><div class="report-icon">▥</div><span class="badge">{{r.type}}</span><h2>{{r.name}}</h2><p class="muted">{{r.description}}</p><button class="primary" :disabled="busy" @click="openReport(r.reportId)">打开报表 ↗</button></article></div>
            <div v-if="!reportData.items.length" class="panel empty">{{q?'没有匹配的搜索结果':'当前没有授权报表，请在 Mock BI 维护权限。'}}</div>
            <div class="pager"><button :disabled="page===1||busy" @click="page--;run(loadReports)">上一页</button>第 {{page}} 页<button :disabled="page*6>=reportData.total||busy" @click="page++;run(loadReports)">下一页</button></div>
          </template>
          <section v-if="embed" class="panel viewer"><div class="toolbar"><b>{{navigation}}</b><button @click="frame?.requestFullscreen()">全屏</button><button @click="closeEmbed">关闭</button></div><iframe ref="frame" :src="embed.embedUrl" title="本地模拟 Quick BI 报表" referrerpolicy="no-referrer" @load="navigated"></iframe><small>票据仅保存在组件内存；关闭、退出或会话失效时移除。此Demo的Mock厂商与门户同源，生产应由浏览器直连真实Quick BI。</small></section>
        </template>
        <template v-if="tab==='users'"><section class="panel table-wrap"><table><thead><tr><th>主体</th><th>ISID / 状态</th><th>Quick BI 映射</th><th>版本</th><th>操作</th></tr></thead><tbody><tr v-for="u in adminUsers" :key="u.userId"><td><b>{{u.displayName}}</b><small>{{u.roles}}</small></td><td>{{u.isid}}<small>企业状态：{{u.enterpriseStatus}}</small></td><td>{{u.quickbiUserId||'—'}}<small>{{u.mappingStatus}}</small></td><td>v{{u.version}}</td><td><button :disabled="busy" @click="selectedMapping=u;mapTarget='bi-free'">编辑映射</button> <button :disabled="busy" @click="sync('USER',u.userId)">同步</button></td></tr></tbody></table></section>
          <form v-if="selectedMapping" class="panel editor" @submit.prevent="submitMapping"><h2>编辑 {{selectedMapping.isid}} · v{{selectedMapping.version}}</h2><p class="muted">bi-alice / bi-bob 已被占用；bi-free 可用于关联演示。成功后撤销目标用户所有门户会话。</p><label>目标 Quick BI UserId<input v-model="mapTarget" maxlength="256" required></label><label>修改原因<input v-model="mapReason" maxlength="500" required></label><button class="primary" :disabled="busy">验证并保存</button> <button type="button" @click="selectedMapping=null">取消</button></form></template>
        <template v-if="tab==='jobs'"><div class="toolbar"><button class="primary" :disabled="busy" @click="sync('FULL')">立即全量同步</button><span class="muted">MySQL 持久化队列 · 后台每1.5秒认领 · 同租户活动FULL唯一</span></div><section class="panel table-wrap"><table><thead><tr><th>任务 / 类型</th><th>结果</th><th>成功 / 失败 / 跳过</th><th>时间</th><th>操作</th></tr></thead><tbody><tr v-for="j in jobs" :key="j.id"><td class="id">{{j.id}}<small>{{j.job_type}}</small></td><td><span class="badge">{{j.state}}</span></td><td>{{j.success}} / {{j.failed}} / {{j.skipped}}（共{{j.total}}）</td><td>{{fmt(j.created_at)}}</td><td><button @click="run(async()=>{jobItems=(await api<Row>(`/api/v1/admin/sync-jobs/${j.id}/items`)).items})">明细</button> <button :disabled="busy||['QUEUED','RUNNING','SUCCEEDED'].includes(j.state)" @click="retry(j.id)">补跑</button></td></tr></tbody></table></section><pre v-if="jobItems.length" class="panel">{{JSON.stringify(jobItems,null,2)}}</pre></template>
        <template v-if="tab==='audit'"><form class="toolbar" @submit.prevent="auditPage=1;run(loadTab)"><select v-model="eventType"><option value="">全部事件</option><option v-for="e in ['LOGIN','LOGOUT','ACCESS_DECISION','TICKET_ISSUE','EMBED_NAVIGATED','ADMIN_CHANGE','SYNC','MOCK_CONTROL']" :key="e">{{e}}</option></select><input v-model="auditQuery" maxlength="100" placeholder="主体ID / 脱敏详情搜索"><button :disabled="busy">查询</button></form><section class="panel table-wrap"><table><thead><tr><th>时间 / 主体</th><th>事件</th><th>结果</th><th>安全详情 / 请求ID</th></tr></thead><tbody><tr v-for="e in auditData.items" :key="e.id"><td>{{fmt(e.event_time)}}<small>{{e.actor_id}}</small></td><td>{{e.event_type}}</td><td>{{e.result}}</td><td class="detail">{{e.safe_details}}<small>{{e.request_id}}</small></td></tr></tbody></table></section><div class="pager"><button :disabled="auditPage===1" @click="auditPage--;run(loadTab)">上一页</button>{{auditData.total}} 条 · 第{{auditPage}}页<button :disabled="auditPage*12>=auditData.total" @click="auditPage++;run(loadTab)">下一页</button></div></template>
        <form v-if="tab==='config'" class="panel editor" @submit.prevent="saveConfig"><h2>非敏感配置 · v{{config.version}}</h2><label>空闲超时（5～60分钟）<input type="number" min="5" max="60" v-model="config.idleMinutes" required></label><label>绝对超时（1～12小时）<input type="number" min="1" max="12" v-model="config.absoluteHours" required></label><label>列表TTL（0～300秒；0每次刷新）<input type="number" min="0" max="300" v-model="config.listCacheSeconds" required></label><label>修改原因<input v-model="reason" maxlength="500" required></label><button class="primary" :disabled="busy">按当前版本保存</button></form>
        <template v-if="tab==='lab'"><section class="panel warning"><h2>仅用于模拟外部 Quick BI 行为</h2><p>这些端点不是生产门户功能。仅本地Mock可用；用户可在另一浏览器保持旧列表，在此撤权，再测试新签票被拒绝。</p></section><section class="panel editor"><h2>故障注入</h2><p>当前：{{lab.mode}} · 模拟审计失败：{{lab.auditFailure}}</p><div class="toolbar"><button :disabled="busy" @click="setFault('NORMAL')">恢复正常</button><button :disabled="busy" @click="setFault('READ_UNAVAILABLE')">只读依赖不可用</button><button :disabled="busy" @click="setFault('TICKET_UNKNOWN')">签票结果未知</button><button :disabled="busy" @click="setFault('AUDIT_FAILURE')">审计不可用</button></div></section><form class="panel editor" @submit.prevent="grant"><h2>模拟厂商授权变更</h2><label>厂商用户<select v-model="mockUser"><option v-for="u in lab.biUsers" :key="u.id">{{u.id}}</option></select></label><label>资源<select v-model="mockReport"><option v-for="r in lab.reports" :key="r.id" :value="r.id">{{r.name}} · {{r.id}}</option></select></label><label class="check"><input type="checkbox" v-model="mockGranted">勾选授予；取消勾选撤销（仅Mock厂商）</label><button :disabled="busy">修改Mock授权</button></form></template>
      </main>
    </div>
  </template>
</template>
