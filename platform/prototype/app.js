const ENVS = [
  { key: 'dev', label: 'dev', branch: 'dev' },
  { key: 'test', label: 'test', branch: 'test' },
  { key: 'uat', label: 'uat', branch: 'uat' },
  { key: 'prod', label: 'prod', branch: 'main' }
];

const SERVICES = {
  api: { key: 'api', label: '后端', image: 'app/api' },
  web: { key: 'web', label: '前端', image: 'app/web' }
};

const STATUS = {
  locked: { label: '未解锁', cls: 'muted' },
  ready: { label: '可部署', cls: 'info' },
  running: { label: '部署中', cls: 'running' },
  success: { label: '成功', cls: 'success' },
  failed: { label: '失败', cls: 'danger' },
  conflict: { label: '合并冲突', cls: 'danger' },
  wait_cr: { label: '待 CR', cls: 'warning' },
  rejected: { label: 'CR 驳回', cls: 'danger' }
};

const PAGE_TITLES = {
  requirements: '需求管理',
  'api-deploy': '后端部署',
  'web-deploy': '前端部署',
  approvals: '审批待办'
};

function emptyServices() {
  const services = {};
  ['api', 'web'].forEach(key => {
    services[key] = {};
    ENVS.forEach(env => {
      services[key][env.key] = { status: 'locked' };
    });
  });
  return services;
}

const state = {
  role: 'dev',
  page: 'requirements',
  project: 'app',
  deployEnv: 'dev',
  selected: new Set(),
  pipeline: null,
  lastDeployMessage: '',
  loading: false,
  envTimer: null,
  approvalQuery: '',
  approvalService: 'all',
  modal: null,
  deployConfirm: null,
  requirements: [
    {
      id: 'REQ-001',
      project: 'app',
      title: '首页增加公告栏',
      branch: '20261023-announcement',
      branchStatus: 'created',
      owner: '开发者',
      related: ['张工'],
      createdAt: '10-23 10:12',
      tip: 'a1b2c3d',
      desc: '在首页顶部增加可关闭的公告栏，支持运营配置文案与跳转链接。',
      cr: { status: 'pending', approver: '张工', service: 'api', requester: '开发者', time: '10-24 14:26' },
      services: {
        api: {
          dev: { status: 'success', sha: 'a1b2c3d', operator: '开发者', time: '10-24 14:20' },
          test: { status: 'wait_cr', sha: 'a1b2c3d', operator: '开发者', time: '10-24 14:26' },
          uat: { status: 'locked' },
          prod: { status: 'locked' }
        },
        web: {
          dev: { status: 'success', sha: 'a1b2c3d', operator: '开发者', time: '10-23 16:00' },
          test: { status: 'locked' },
          uat: { status: 'locked' },
          prod: { status: 'locked' }
        }
      }
    },
    {
      id: 'REQ-002',
      project: 'app',
      title: '订单列表支持导出',
      branch: '20261022-order-export',
      branchStatus: 'created',
      owner: '开发者',
      createdAt: '10-22 09:30',
      tip: 'b2c3d4e',
      desc: '订单列表新增导出按钮，按当前筛选条件导出 CSV，单次最多 5 万行。',
      cr: { status: 'approved', approver: '张工', service: 'api', requester: '开发者', time: '10-24 09:12' },
      services: {
        api: {
          dev: { status: 'success', sha: 'b2c3d4e', operator: '开发者', time: '10-23 17:40' },
          test: { status: 'success', sha: 'b2c3d4e', operator: '开发者', time: '10-24 09:12', approver: '张工' },
          uat: { status: 'success', sha: 'b2c3d4d', operator: '张工', time: '10-24 10:30' },
          prod: { status: 'success', sha: 'b2c3d4e', operator: '张工', time: '10-24 10:30' }
        },
        web: {
          dev: { status: 'success', sha: 'b2c3d4e', operator: '开发者', time: '10-23 17:42' },
          test: { status: 'success', sha: 'b2c3d4e', operator: '开发者', time: '10-24 09:14', approver: '张工' },
          uat: { status: 'success', sha: 'b2c3d4e', operator: '张工', time: '10-24 10:32' },
          prod: { status: 'success', sha: 'b2c3d4e', operator: '张工', time: '10-24 10:32' }
        }
      }
    },
    {
      id: 'REQ-003',
      project: 'app',
      title: '报表金额精度修正',
      branch: '20261024-report-amount',
      branchStatus: 'created',
      owner: '开发者',
      createdAt: '10-24 11:20',
      tip: 'c3d4e5f',
      desc: '修正报表汇总金额四舍五入导致的 0.01 偏差，口径与财务对账保持一致。',
      cr: { status: 'none' },
      services: {
        api: {
          dev: { status: 'ready', sha: 'c3d4e5f' },
          test: { status: 'locked' },
          uat: { status: 'locked' },
          prod: { status: 'locked' }
        },
        web: {
          dev: { status: 'ready', sha: 'c3d4e5f' },
          test: { status: 'locked' },
          uat: { status: 'locked' },
          prod: { status: 'locked' }
        }
      }
    },
    {
      id: 'REQ-004',
      project: 'app',
      title: '用户中心页面改版',
      branch: '20261022-user-center',
      branchStatus: 'created',
      owner: '开发者',
      createdAt: '10-22 15:40',
      tip: 'd4e5f6a',
      desc: '用户中心信息层级调整，新增账号安全入口与登录设备管理。',
      cr: { status: 'approved', approver: '张工', service: 'api', requester: '开发者', time: '10-23 18:20' },
      services: {
        api: {
          dev: { status: 'success', sha: 'd4e5f6a', operator: '开发者', time: '10-23 15:00' },
          test: { status: 'success', sha: 'd4e5f6a', operator: '开发者', time: '10-23 18:20', approver: '张工' },
          uat: { status: 'conflict', sha: 'd4e5f6a', operator: '开发者', time: '10-23 18:30', reason: '合并冲突：src/views/UserCenter.vue' },
          prod: { status: 'locked' }
        },
        web: {
          dev: { status: 'success', sha: 'd4e5f6a', operator: '开发者', time: '10-23 15:02' },
          test: { status: 'failed', sha: 'd4e5f6a', operator: '开发者', time: '10-23 18:22', reason: 'verify 失败：首页加载超时' },
          uat: { status: 'locked' },
          prod: { status: 'locked' }
        }
      }
    },
    {
      id: 'REQ-005',
      project: 'app',
      title: '支付回调重试机制',
      branch: '20261024-pay-retry',
      branchStatus: 'created',
      owner: '开发者',
      createdAt: '10-24 08:50',
      tip: 'e5f6a7b',
      desc: '支付回调失败后按 1m / 5m / 15m 重试，超限触发告警。',
      cr: { status: 'none' },
      services: {
        api: {
          dev: { status: 'failed', sha: 'e5f6a7b', operator: '开发者', time: '10-24 09:40', reason: 'verify 失败：/api/info 返回 db:false' },
          test: { status: 'locked' },
          uat: { status: 'locked' },
          prod: { status: 'locked' }
        },
        web: {
          dev: { status: 'ready', sha: 'e5f6a7b' },
          test: { status: 'locked' },
          uat: { status: 'locked' },
          prod: { status: 'locked' }
        }
      }
    },
    {
      id: 'REQ-006',
      project: 'app',
      title: '发票抬头维护',
      branch: 'feature/REQ-006',
      branchStatus: 'none',
      owner: '开发者',
      createdAt: '10-24 16:05',
      tip: null,
      desc: '新增发票抬头增删改查，支持默认抬头与税号校验。',
      cr: { status: 'none' },
      services: emptyServices()
    }
  ],
  deployments: [
    { time: '10-24 14:20', id: 'REQ-001', service: 'api', env: 'dev', sha: 'a1b2c3d', status: 'success', by: '开发者', approver: '-', duration: '1m 12s' },
    { time: '10-24 10:30', id: 'REQ-002', service: 'api', env: 'uat', sha: 'b2c3d4e', status: 'success', by: '张工', approver: '张工', duration: '1m 05s' },
    { time: '10-24 09:14', id: 'REQ-002', service: 'web', env: 'test', sha: 'b2c3d4e', status: 'success', by: '开发者', approver: '张工', duration: '52s' },
    { time: '10-24 09:12', id: 'REQ-002', service: 'api', env: 'test', sha: 'b2c3d4e', status: 'success', by: '开发者', approver: '张工', duration: '58s' },
    { time: '10-24 09:40', id: 'REQ-005', service: 'api', env: 'dev', sha: 'e5f6a7b', status: 'failed', by: '开发者', approver: '-', duration: '42s' },
    { time: '10-23 18:22', id: 'REQ-004', service: 'web', env: 'test', sha: 'd4e5f6a', status: 'failed', by: '开发者', approver: '张工', duration: '1m 21s' },
    { time: '10-23 18:20', id: 'REQ-004', service: 'api', env: 'test', sha: 'd4e5f6a', status: 'success', by: '开发者', approver: '张工', duration: '1m 03s' },
    { time: '10-23 17:40', id: 'REQ-002', service: 'api', env: 'dev', sha: 'b2c3d4e', status: 'success', by: '开发者', approver: '-', duration: '1m 09s' }
  ],
  prodHistory: {
    'api:REQ-002': { currentSha: 'b2c3d4e', currentTime: '10-24 10:30', previousSha: 'b2c3d4d', previousTime: '10-20 09:12' },
    'web:REQ-002': { currentSha: 'b2c3d4e', currentTime: '10-24 10:32', previousSha: 'b2c3d4d', previousTime: '10-20 09:14' }
  },
  rollbackConfirm: null
};

let toastTimer = null;

function currentUser() {
  return state.role === 'approver' ? '张工' : '开发者';
}

function now() {
  const d = new Date();
  const p = n => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function escapeHtml(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function findReq(id) {
  return state.requirements.find(r => r.id === id);
}

function projectRequirements() {
  return state.requirements.filter(r => r.project === state.project);
}

function statusPill(status) {
  const meta = STATUS[status] || STATUS.locked;
  return `<span class="pill ${meta.cls}"><i></i>${meta.label}</span>`;
}

function overallPill(o) {
  return `<span class="pill ${o.cls}"><i></i>${o.label}</span>`;
}

function serviceOverall(r, serviceKey) {
  const s = r.services[serviceKey];
  if (s.prod.status === 'success') return { label: '已上线', cls: 'success' };
  if (s.test.status === 'wait_cr') return { label: '待 CR', cls: 'warning' };
  if (Object.values(s).some(e => e.status === 'running')) return { label: '部署中', cls: 'running' };
  if (Object.values(s).some(e => e.status === 'failed')) return { label: '有失败', cls: 'danger' };
  if (s.uat.status === 'ready' || s.prod.status === 'ready') return { label: '待上生产', cls: 'info' };
  if (s.test.status === 'success') return { label: '测试通过', cls: 'info' };
  if (s.dev.status === 'success') return { label: '开发完成', cls: 'info' };
  if (s.dev.status === 'ready') return { label: '可部署', cls: 'info' };
  return { label: '未开始', cls: 'muted' };
}

function requirementStatus(r) {
  if (r.branchStatus !== 'created') return { label: '待建分支', cls: 'muted' };
  if (r.cr.status === 'pending') return { label: '待 CR', cls: 'warning' };
  const all = Object.values(r.services).flatMap(s => Object.values(s));
  if (all.some(e => e.status === 'running')) return { label: '部署中', cls: 'running' };
  if (all.some(e => e.status === 'failed')) return { label: '有失败', cls: 'danger' };
  const bothOnline = Object.values(r.services).every(s => s.prod.status === 'success');
  if (bothOnline) return { label: '已上线', cls: 'success' };
  return { label: '进行中', cls: 'info' };
}

function gateCheck(r, serviceKey, envKey) {
  const s = r.services[serviceKey];
  if (s[envKey].status === 'running') {
    return { ok: false, reason: '当前环境有部署进行中' };
  }
  if (envKey === 'dev') return { ok: true };
  const prevKey = ENVS[ENVS.findIndex(e => e.key === envKey) - 1].key;
  const prev = s[prevKey];
  if (prev.status !== 'success') return { ok: false, reason: `需先完成 ${prevKey} 部署` };
  if (prev.sha !== r.tip) return { ok: false, reason: `${prevKey} 的 SHA 落后于最新提交` };
  if (envKey === 'test' && s.test.status !== 'success') {
    if (r.cr.status === 'pending') return { ok: false, reason: 'CR 审批中', pendingCr: true };
    if (r.cr.status === 'rejected') return { ok: false, reason: 'CR 已驳回，重新指定后可部署', needCr: true };
    if (r.cr.status !== 'approved') return { ok: false, reason: 'test 首次部署需 CR', needCr: true };
  }
  return { ok: true };
}

/* ---------- 登录 / 导航 ---------- */
function doLogin() {
  document.getElementById('loginScreen').classList.add('hidden');
  document.getElementById('appShell').classList.remove('hidden');
  renderAll();
}

function setRole(role, silent) {
  state.role = role;
  const select = document.getElementById('roleSelect');
  if (select.value !== role) select.value = role;
  document.getElementById('userName').textContent = currentUser();
  document.getElementById('userAvatar').textContent = role === 'approver' ? '张' : '冯';
  renderAll();
  if (!silent) toast(role === 'approver' ? '已切换为 CR 管理员视角' : '已切换为开发视角');
}

function showPage(page) {
  state.page = page;
  document.querySelectorAll('.nav-item').forEach(el => {
    el.classList.toggle('active', el.dataset.page === page);
  });
  document.querySelectorAll('.page').forEach(el => el.classList.add('hidden'));
  document.getElementById('page-' + page).classList.remove('hidden');
  document.getElementById('crumbs').textContent = PAGE_TITLES[page];
  if (page === 'requirements') renderRequirements();
  if (page === 'api-deploy') renderDeployPage('api');
  if (page === 'web-deploy') renderDeployPage('web');
  if (page === 'approvals') renderApprovals();
  updateBadge();
}

function renderAll() {
  renderRequirements();
  renderDeployPage('api');
  renderDeployPage('web');
  renderApprovals();
  updateBadge();
  if (state.page === 'detail' && state.detailId) renderDetail(state.detailId);
}

function updateBadge() {
  const count = state.requirements.filter(r => r.cr.status === 'pending').length;
  const badge = document.getElementById('approvalBadge');
  badge.textContent = String(count);
  badge.classList.toggle('zero', count === 0);
}

/* ---------- 需求管理 ---------- */
function visibleRequirements() {
  return state.requirements.filter(r =>
    r.owner === currentUser() || (r.related || []).includes(currentUser()));
}

function renderRequirements() {
  const visible = visibleRequirements();
  const rows = visible.map(r => {
    const o = requirementStatus(r);
    const branch = r.branchStatus === 'created'
      ? `<code>${r.branch}</code>`
      : `<span class="muted-text">未绑定</span>`;
    const action = r.branchStatus === 'created'
      ? `<button class="btn small" onclick="event.stopPropagation(); openDetail('${r.id}')">查看</button>`
      : `<button class="btn small primary" onclick="event.stopPropagation(); openDetail('${r.id}')">创建/绑定分支</button>`;

    return `
      <tr onclick="openDetail('${r.id}')" style="cursor:pointer">
        <td>
          <div class="req-cell">
            <button class="link" onclick="event.stopPropagation(); openDetail('${r.id}')">${r.id}</button>
            <span class="req-title">${escapeHtml(r.title)}</span>
          </div>
        </td>
        <td>${branch}</td>
        <td>${r.owner}</td>
        <td>${r.createdAt}</td>
        <td>${overallPill(o)}</td>
        <td class="ops">${action}</td>
      </tr>`;
  }).join('');

  document.getElementById('page-requirements').innerHTML = `
    <div class="page-head">
      <div>
        <h1>需求管理</h1>
        <p>只显示我创建或与我相关的需求；点需求即可创建或绑定开发分支</p>
      </div>
      <button class="btn primary" onclick="openNewRequirement()">+ 新建需求</button>
    </div>

    <div class="card">
      <div class="toolbar">
        <input class="search" placeholder="搜索需求编号、标题或分支">
        <select><option>全部状态</option><option>待绑定分支</option><option>进行中</option><option>待 CR</option></select>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th style="width:32%">需求</th>
              <th style="width:20%">开发分支</th>
              <th style="width:12%">负责人</th>
              <th style="width:14%">创建时间</th>
              <th style="width:12%">状态</th>
              <th style="width:10%"></th>
            </tr>
          </thead>
          <tbody>${rows || `<tr><td colspan="6" class="empty">没有与我相关的需求</td></tr>`}</tbody>
        </table>
      </div>
      <div class="toolbar" style="border-bottom:0; justify-content:flex-end; color:var(--text-3); font-size:12px">
        共 ${visible.length} 条
      </div>
    </div>`;
}

function createBranch(id) {
  const r = findReq(id);
  if (!r || r.branchStatus === 'created') return;
  r.branchStatus = 'created';
  r.branch = 'feature/' + r.id;
  r.tip = 'f' + Math.random().toString(16).slice(2, 7);
  r.services = emptyServices();
  renderAll();
  toast(`${r.branch} 已创建，可以开始开发`);
}

function openDetail(id) {
  state.reqModal = { id, edit: false };
  renderRequirementModal();
}

function switchBranchEdit(id) {
  state.reqModal = { id, edit: true };
  renderRequirementModal();
}

function renderRequirementModal() {
  const modalState = state.reqModal;
  if (!modalState) return;
  const r = findReq(modalState.id);
  if (!r) return;
  const created = r.branchStatus === 'created';
  const editing = modalState.edit || !created;
  const o = requirementStatus(r);

  let branchHtml;
  if (created && !editing) {
    branchHtml = `
      <div class="branch-code">
        <code>${r.branch}</code>
        <button class="btn small" onclick="copyText('${r.branch}')">复制</button>
      </div>
      <div class="kv" style="margin-top:12px">
        <span>当前提交</span><span><code>${r.tip}</code></span>
        <span>绑定时间</span><span>${r.createdAt}</span>
      </div>
      <p class="hint-line" style="margin-top:12px">分支已绑定需求，可在「后端部署 / 前端部署」中看到它。</p>
      <button class="link" onclick="switchBranchEdit('${r.id}')">更换绑定分支</button>`;
  } else {
    branchHtml = `
      <label class="field">新分支名（只填后缀）
        <input id="branchSuffixInput" placeholder="bugfix-ledger" oninput="updateBranchPreview()">
        <small>自动拼接当天日期：<code id="branchPreview">${todayCompact()}-bugfix-ledger</code></small>
      </label>
      <div class="modal-branch-actions">
        <button class="btn primary" onclick="createRequirementBranch('${r.id}')">创建分支</button>
      </div>

      <div class="divider-text"><span>或绑定已有分支</span></div>

      <label class="field">已有分支名
        <input id="existingBranchInput" value="${created && editing ? r.branch : ''}" placeholder="20261110-order-fix">
      </label>
      <div class="modal-branch-actions">
        <button class="btn" onclick="bindRequirementBranch('${r.id}')">绑定分支</button>
      </div>

      <p class="hint-line" style="margin-top:10px">分支必须绑定到需求后，才会出现在部署菜单。</p>`;
  }

  document.getElementById('modalRoot').innerHTML = `
    <div class="overlay" onclick="if(event.target===this) closeModal()">
      <div class="modal" style="width:min(620px,100%)">
        <div class="modal-head">
          <h3>${r.id} · ${escapeHtml(r.title)}</h3>
          <button class="modal-close" onclick="event.stopPropagation(); closeModal()">×</button>
        </div>
        <div class="modal-body">
          <div class="req-modal-sub">
            ${overallPill(o)}
            <span>负责人 ${r.owner}</span>
            <span>创建于 ${r.createdAt}</span>
          </div>
          <p class="detail-desc" style="margin:10px 0 0">${escapeHtml(r.desc)}</p>
          <div class="panel-head" style="padding:18px 0 10px; border-bottom:1px solid var(--border-light)">
            <h3>开发分支</h3>
          </div>
          <div style="padding-top:14px">${branchHtml}</div>
        </div>
        <div class="modal-foot">
          <button class="btn" onclick="closeModal()">关闭</button>
        </div>
      </div>
    </div>`;
}

function todayCompact() {
  const d = new Date();
  const p = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}`;
}

function updateBranchPreview() {
  const input = document.getElementById('branchSuffixInput');
  const preview = document.getElementById('branchPreview');
  if (!input || !preview) return;
  const suffix = input.value.trim() || 'bugfix-ledger';
  preview.textContent = `${todayCompact()}-${suffix}`;
}

function createRequirementBranch(id) {
  const input = document.getElementById('branchSuffixInput');
  const suffix = (input ? input.value.trim() : '').replace(/^[-_.]+|[-_.]+$/g, '');
  if (!suffix) {
    toast('请填写分支名后缀，例如 bugfix-ledger', 'error');
    return;
  }
  if (!/^[A-Za-z0-9._-]+$/.test(suffix)) {
    toast('分支名只允许字母、数字、点、下划线、短横线', 'error');
    return;
  }
  const name = `${todayCompact()}-${suffix}`;
  applyRequirementBranch(id, name);
  toast(`已创建 ${name}，可在部署菜单看到`);
}

function bindRequirementBranch(id) {
  const input = document.getElementById('existingBranchInput');
  const name = input ? input.value.trim() : '';
  if (!name) {
    toast('请填写要绑定的分支名', 'error');
    return;
  }
  applyRequirementBranch(id, name);
  toast(`已绑定分支 ${name}`);
}

function applyRequirementBranch(id, name) {
  const r = findReq(id);
  const changed = r.branch !== name;
  r.branch = name;
  r.branchStatus = 'created';
  r.tip = 'f' + Math.random().toString(16).slice(2, 7);
  if (changed) r.services = emptyServices();
  closeModal();
  renderAll();
}

function renderDetail(id) {
  const r = findReq(id);
  if (!r) return;
  const o = requirementStatus(r);
  const created = r.branchStatus === 'created';

  const branchPanel = created
    ? `
      <div class="branch-code">
        <code>${r.branch}</code>
        <button class="btn small" onclick="copyText('${r.branch}')">复制分支名</button>
      </div>
      <div class="kv" style="margin-top:14px">
        <span>当前提交</span><span><code>${r.tip}</code></span>
        <span>创建时间</span><span>${r.createdAt}</span>
        <span>负责人</span><span>${r.owner}</span>
      </div>
      <div class="cmd">
        git fetch origin<br>
        git checkout ${r.branch}
      </div>
      <div class="toolbar" style="padding:14px 0 0; border:0">
        <button class="btn small" onclick="copyText('git fetch origin && git checkout ${r.branch}')">复制克隆命令</button>
      </div>`
    : `
      <p class="hint-line">还没有开发分支。创建后平台会从 main 拉出 <code>feature/${r.id}</code>，开发提交都在这条分支上进行。</p>
      <button class="btn primary" style="margin-top:14px" onclick="createBranch('${r.id}')">创建开发分支</button>`;

  document.getElementById('page-detail').innerHTML = `
    <button class="back" onclick="showPage('requirements')">← 返回需求列表</button>

    <div class="card detail-head">
      <div>
        <h1>${r.id} · ${escapeHtml(r.title)}</h1>
        <div class="sub">
          ${overallPill(o)}
          <span>负责人 ${r.owner}</span>
          <span>创建于 ${r.createdAt}</span>
          ${created ? `<span>分支 <code>${r.branch}</code></span>` : '<span>分支未创建</span>'}
        </div>
        <p class="detail-desc">${escapeHtml(r.desc)}</p>
      </div>
    </div>

    <div class="detail-grid">
      <div class="panel">
        <div class="panel-head"><h3>开发分支</h3></div>
        <div class="panel-body">${branchPanel}</div>
      </div>

      <div class="panel">
        <div class="panel-head"><h3>接入信息</h3></div>
        <div class="panel-body rule-list">
          <div><b>仓库</b><code>http://localhost:8080/dev/app.git</code></div>
          <div><b>分支映射</b>dev → dev；test → test；uat → uat；prod → main</div>
          <div><b>CR 审批人</b>张工（test 首次部署时生效）</div>
          <div><b>部署入口</b>部署在「后端部署」「前端部署」菜单操作，本页只负责需求与分支。</div>
        </div>
      </div>
    </div>`;
}

function copyText(text) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text)
      .then(() => toast('已复制'))
      .catch(() => toast('复制失败，请手动选择', 'error'));
    return;
  }
  toast('已复制');
}

/* ---------- 部署页 ---------- */
function envDeployedCount(serviceKey, envKey) {
  return projectRequirements().filter(r => {
    const st = r.services[serviceKey][envKey].status;
    return r.branchStatus === 'created' && (st === 'success' || st === 'failed' || st === 'running');
  }).length;
}

function envStepper(serviceKey) {
  const activeIndex = ENVS.findIndex(e => e.key === state.deployEnv);
  return `
    <div class="env-stepper">
      ${ENVS.map((env, index) => {
        const active = index === activeIndex ? 'active' : index < activeIndex ? 'done' : '';
        return `<button class="env-node ${active}" onclick="selectDeployEnv('${env.key}')">
          <span class="env-circle">${env.label}</span>
        </button>
        ${index < ENVS.length - 1 ? `<span class="env-line ${index < activeIndex ? 'done' : ''}"></span>` : ''}`;
      }).join('')}
    </div>`;
}

function crCell(r, serviceKey, envKey) {
  if (envKey !== 'test') return '<span class="muted-text">-</span>';
  if (r.cr.status === 'approved') {
    return `<span class="pill success"><i></i>已CR · ${r.cr.approver || '张工'}</span>`;
  }
  if (r.cr.status === 'pending') {
    return `<button class="link warn-link" onclick="openCrDialog('${r.id}','${serviceKey}')">待 CR · ${r.cr.approver || '张工'}</button>`;
  }
  if (r.cr.status === 'rejected') {
    return `<button class="link danger-link" onclick="openCrDialog('${r.id}','${serviceKey}')">CR 驳回 · 重新指定</button>`;
  }
  return `<button class="link" onclick="openCrDialog('${r.id}','${serviceKey}')">指定 CR</button>`;
}

function deployedSection(serviceKey, envKey) {
  const items = projectRequirements().filter(r => {
    if (r.branchStatus !== 'created') return false;
    const st = r.services[serviceKey][envKey].status;
    return st === 'success' || st === 'failed' || st === 'running' || st === 'conflict';
  });

  const failedIds = items
    .filter(r => r.services[serviceKey][envKey].status === 'failed')
    .map(r => r.id);

  const rows = items.length ? items.map(r => {
    const s = r.services[serviceKey][envKey];
    const hasNew = s.sha !== r.tip;
    const tags = [];

    if (envKey === 'test') {
      if (r.cr.status === 'approved') tags.push('<span class="pill success"><i></i>已CR</span>');
      else if (r.cr.status === 'pending') tags.push(`<button class="pill warning tag-btn" onclick="openCrDialog('${r.id}','${serviceKey}')"><i></i>待CR</button>`);
      else if (r.cr.status === 'rejected') tags.push(`<button class="pill danger tag-btn" onclick="openCrDialog('${r.id}','${serviceKey}')"><i></i>CR驳回</button>`);
      else tags.push(`<button class="pill muted tag-btn" onclick="openCrDialog('${r.id}','${serviceKey}')"><i></i>未CR</button>`);
    }

    if (s.status === 'running') {
      tags.push('<span class="pill running"><i></i>部署中</span>');
      tags.push('<button class="btn small danger" onclick="abortDeployment()">终止部署</button>');
    }
    else if (s.status === 'failed') {
      /* 重试入口在卡片标题栏 */
    }
    else if (hasNew) tags.push(`<button class="pill info tag-btn" onclick="deployOne('${serviceKey}','${r.id}')"><i></i>有新提交</button>`);

    return `
      <tr>
        <td>
          <div class="req-cell">
            <button class="link" onclick="openDetail('${r.id}')">${r.id}</button>
            <span class="req-title">${escapeHtml(r.title)}</span>
          </div>
        </td>
        <td><code>${r.branch}</code></td>
        <td><code>${s.sha || '-'}</code></td>
        <td>${s.time || '-'}</td>
        <td><div class="tag-row">${tags.length ? tags.join('') : '<span class="muted-text">-</span>'}</div></td>
        <td class="result-cell">
          ${statusPill(s.status)}
          ${s.status === 'conflict' ? `<button class="link" style="margin-left:10px" onclick="resolveConflict('${serviceKey}','${r.id}')">已解决</button>` : ''}
        </td>
      </tr>`;
  }).join('') : `<tr><td colspan="6" class="empty">该环境还没有部署记录</td></tr>`;

  return `
    <div class="card section-card">
      <div class="panel-head">
        <h3>已部署分支</h3>
        <div class="head-actions">
          <span class="muted-text">${envKey} 环境 · 共 ${items.length} 个</span>
          ${failedIds.length ? `<button class="btn small primary" onclick="retryFailed('${serviceKey}')">重试${failedIds.length > 1 ? ' (' + failedIds.length + ')' : ''}</button>` : ''}
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th style="width:24%">需求</th>
              <th style="width:20%">分支</th>
              <th style="width:12%">部署提交</th>
              <th style="width:12%">部署时间</th>
              <th style="width:18%">标签</th>
              <th style="width:14%; text-align:right">部署结果</th>
            </tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
    </div>`;
}

function pendingSection(serviceKey, envKey) {
  const items = projectRequirements().filter(r => {
    if (r.branchStatus !== 'created') return false;
    const st = r.services[serviceKey][envKey].status;
    if (!(st === 'locked' || st === 'ready' || st === 'wait_cr' || st === 'rejected')) return false;
    const g = gateCheck(r, serviceKey, envKey);
    return g.ok || g.needCr || g.pendingCr || st === 'wait_cr' || st === 'rejected';
  });

  const selectedCount = items.filter(r => state.selected.has(r.id) && gateCheck(r, serviceKey, envKey).ok).length;

  const rows = items.length ? items.map(r => {
    const s = r.services[serviceKey][envKey];
    const gate = gateCheck(r, serviceKey, envKey);
    const checkable = gate.ok;
    const checked = state.selected.has(r.id) && checkable;
    const tags = [];

    if (envKey === 'test') {
      if (r.cr.status === 'approved') tags.push('<span class="pill success"><i></i>已CR</span>');
      else if (r.cr.status === 'pending' || gate.pendingCr) tags.push(`<button class="pill warning tag-btn" onclick="openCrDialog('${r.id}','${serviceKey}')"><i></i>待CR</button>`);
      else if (r.cr.status === 'rejected' || s.status === 'rejected') tags.push(`<button class="pill danger tag-btn" onclick="openCrDialog('${r.id}','${serviceKey}')"><i></i>CR驳回</button>`);
      else tags.push(`<button class="pill warning tag-btn" onclick="openCrDialog('${r.id}','${serviceKey}')"><i></i>需CR</button>`);
    }
    if (gate.ok) tags.push('<span class="pill info"><i></i>可部署</span>');

    return `
      <tr class="${checkable ? '' : 'row-disabled'}">
        <td><input type="checkbox" ${checked ? 'checked' : ''} ${checkable ? '' : 'disabled'} onchange="toggleSelect('${r.id}')"></td>
        <td>
          <div class="req-cell">
            <button class="link" onclick="openDetail('${r.id}')">${r.id}</button>
            <span class="req-title">${escapeHtml(r.title)}</span>
          </div>
        </td>
        <td><code>${r.branch}</code></td>
        <td><code>${r.tip}</code></td>
        <td><div class="tag-row">${tags.join('')}</div></td>
      </tr>`;
  }).join('') : `<tr><td colspan="5" class="empty">没有待部署的分支</td></tr>`;

  return `
    <div class="card section-card">
      <div class="panel-head">
        <h3>待部署分支</h3>
        <div class="head-actions">
          <span class="muted-text">已选 ${selectedCount} 个</span>
          <button class="btn primary" ${selectedCount ? '' : 'disabled'} onclick="deploySelected('${serviceKey}')">部署选中${selectedCount ? ' (' + selectedCount + ')' : ''}</button>
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th style="width:5%"><input type="checkbox" onclick="toggleSelectAll(this.checked, '${serviceKey}')"></th>
              <th style="width:32%">需求</th>
              <th style="width:26%">分支</th>
              <th style="width:12%">提交</th>
              <th style="width:25%">标签</th>
            </tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
    </div>`;
}

function prodRollbackSection(serviceKey, envKey) {
  if (envKey !== 'prod') return '';
  const rows = projectRequirements()
    .filter(r => r.branchStatus === 'created' && r.services[serviceKey].prod.status === 'success')
    .map(r => {
      const history = state.prodHistory[`${serviceKey}:${r.id}`];
      if (!history) return '';
      return `
        <tr>
          <td>
            <button class="link" onclick="openDetail('${r.id}')">${r.id}</button>
            <div class="title">${escapeHtml(r.title)}</div>
          </td>
          <td><code>${history.currentSha}</code><div class="title">${history.currentTime}</div></td>
          <td><code>${history.previousSha}</code><div class="title">${history.previousTime}</div></td>
          <td class="ops">
            <button class="btn small" onclick="openRollbackConfirm('${serviceKey}','${r.id}')">回滚到上一版本</button>
          </td>
        </tr>`;
    })
    .join('');

  if (!rows) return '';

  return `
    <div class="card section-card">
      <div class="panel-head">
        <h3>生产版本与回滚</h3>
        <span class="muted-text">部署失败或线上验证异常时可回滚</span>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th style="width:34%">需求</th>
              <th style="width:24%">当前线上版本</th>
              <th style="width:24%">上一版本</th>
              <th style="width:18%"></th>
            </tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
    </div>`;
}

function openRollbackConfirm(serviceKey, id) {
  if (state.pipeline && state.pipeline.status === 'running') {
    toast('当前环境正在部署，不能回滚', 'error');
    return;
  }
  const history = state.prodHistory[`${serviceKey}:${id}`];
  if (!history) {
    toast('没有可回滚的历史版本', 'error');
    return;
  }
  state.rollbackConfirm = { serviceKey, id, sha: history.previousSha };
  const svc = SERVICES[serviceKey];
  const r = findReq(id);

  document.getElementById('modalRoot').innerHTML = `
    <div class="overlay" onclick="if(event.target===this) closeModal()">
      <div class="modal" style="width:min(540px,100%)">
        <div class="modal-head">
          <h3>回滚确认</h3>
          <button class="modal-close" onclick="event.stopPropagation(); closeModal()">×</button>
        </div>
        <div class="modal-body">
          <div class="kv">
            <span>服务</span><span><span class="pill ${serviceKey === 'api' ? 'info' : 'primary'}">${svc.label} ${svc.image}</span></span>
            <span>需求</span><span>${r.id} · ${escapeHtml(r.title)}</span>
            <span>当前版本</span><span><code>${history.currentSha}</code></span>
            <span>回滚到</span><span><code>${history.previousSha}</code>（${history.previousTime}）</span>
          </div>
          <div class="notice">回滚会以旧版本重新走一遍发布流程，并替换当前生产 Pod。</div>
        </div>
        <div class="modal-foot">
          <button class="btn" onclick="closeModal()">取消</button>
          <button class="btn primary" onclick="confirmRollback()">确认回滚</button>
        </div>
      </div>
    </div>`;
}

function confirmRollback() {
  const config = state.rollbackConfirm;
  if (!config) return;
  const { serviceKey, id, sha } = config;
  closeModal();
  rollbackTo(serviceKey, id, sha);
}

async function rollbackTo(serviceKey, id, sha) {
  const r = findReq(id);
  const history = state.prodHistory[`${serviceKey}:${id}`];
  const oldCurrent = history ? history.currentSha : null;
  const done = await runPipeline(serviceKey, 'prod', [{ req: r, sha }], `回滚成功：${id} 已回滚到 ${sha}（${now()} 开始）`);
  if (done && history && oldCurrent) {
    history.previousSha = oldCurrent;
    history.previousTime = now();
    history.currentSha = sha;
    history.currentTime = now();
  }
  renderDeployPage(serviceKey);
}

function resolveConflict(serviceKey, id) {
  const envKey = state.deployEnv;
  const r = findReq(id);
  r.services[serviceKey][envKey].status = 'ready';
  state.lastDeployMessage = '';
  renderDeployPage(serviceKey);
  toast('已标记冲突解决，请重新部署');
  openDeployConfirm(serviceKey, [id]);
}

function pipelineSection(serviceKey) {
  const p = state.pipeline;
  if (!p || p.service !== serviceKey) return '';

  const steps = p.steps.map(step => `
    <div class="step ${step.status}">
      <span class="step-mark">${step.status === 'success' ? '✓' : step.status === 'running' ? '…' : step.status === 'failed' ? '×' : ''}</span>
      <div class="step-text">
        <strong>${step.name}</strong>
        <small>${step.status === 'running' ? '进行中' : step.status === 'success' ? '完成 ' + step.duration : step.status === 'failed' ? '失败' : '等待'}</small>
      </div>
    </div>`).join('');

  return `
    <div class="card section-card">
      <div class="panel-head">
        <h3>部署进度</h3>
        <span class="muted-text">开始于 ${p.startTime}</span>
      </div>
      <div class="panel-body">
        <div class="pipeline-head">
          <div>
            <strong>${p.status === 'running' ? '部署进行中' : p.status === 'terminated' ? '部署已终止' : '部署完成'}</strong>
            <span class="muted-text">${p.branch ? p.branch.id + ' · ' + p.branch.branch : ''}</span>
          </div>
          <div class="pipeline-actions">
            <span class="pill ${p.status === 'running' ? 'running' : p.status === 'success' ? 'success' : 'danger'}">第 ${p.index}/${p.total} 个分支</span>
            ${p.status === 'running' ? '<button class="btn small danger" onclick="abortDeployment()">终止部署</button>' : ''}
          </div>
        </div>
        <div class="steps">${steps}</div>
      </div>
    </div>`;
}

function renderDeployPage(serviceKey) {
  const svc = SERVICES[serviceKey];
  const envKey = state.deployEnv;

  document.getElementById('page-' + serviceKey + '-deploy').innerHTML = `
    <div class="page-head deploy-head">
      <div class="deploy-title">
        <h1>${svc.label}部署</h1>
        <label class="picker page-picker">项目
          <select onchange="changeDeployProject(this.value)">
            <option value="app" ${state.project === 'app' ? 'selected' : ''}>app</option>
            <option value="pay-center" ${state.project === 'pay-center' ? 'selected' : ''}>pay-center</option>
          </select>
        </label>
      </div>
    </div>

    <div class="card env-card">${envStepper(serviceKey)}</div>
    ${state.loading
      ? `<div class="card section-card loading-card"><span class="spinner"></span><span>正在加载 ${svc.label} ${envKey} 环境…</span></div>`
      : `${deployedSection(serviceKey, envKey)}
         ${prodRollbackSection(serviceKey, envKey)}
         ${pendingSection(serviceKey, envKey)}
         <div id="pipeline-${serviceKey}">${pipelineSection(serviceKey)}</div>
         ${podSection(serviceKey, envKey)}`}
  `;
}

function podsFor(serviceKey, envKey) {
  return projectRequirements()
    .filter(r => r.branchStatus === 'created')
    .map((r, index) => {
      const st = r.services[serviceKey][envKey].status;
      if (!(st === 'success' || st === 'failed' || st === 'running')) return null;
      const failed = st === 'failed';
      return {
        name: `${serviceKey}-${envKey}-${r.id.toLowerCase().replace('-', '')}-${String(index + 1).padStart(2, '0')}`,
        status: failed ? 'CrashLoopBackOff' : 'Running',
        ready: failed ? '0/1' : '1/1',
        restarts: failed ? 5 : 0,
        age: failed ? '14m' : '2d 4h',
        node: 'k3s-node-1',
        image: `${SERVICES[serviceKey].image}:${r.services[serviceKey][envKey].sha || r.tip}`
      };
    })
    .filter(Boolean);
}

function podSection(serviceKey, envKey) {
  const pods = podsFor(serviceKey, envKey);
  const cards = pods.length
    ? pods.map(pod => `
      <div class="pod-card">
        <div class="pod-name">
          <span class="pod-dot ${pod.status === 'Running' ? '' : 'failed'}"></span>
          <strong>${pod.name}</strong>
          <span class="pill ${pod.status === 'Running' ? 'success' : 'danger'}">${pod.status}</span>
        </div>
        <div class="pod-meta">${pod.ready} · ${pod.age} · ${pod.node}</div>
        <div class="pod-actions">
          <button class="link" onclick="openPodLogs('${pod.name}')">日志</button>
          <button class="link" onclick="openPodYaml('${pod.name}')">YAML</button>
        </div>
      </div>`).join('')
    : '<div class="empty" style="padding:28px">该环境暂无 Pod</div>';

  return `
    <div class="card section-card">
      <div class="panel-head">
        <h3>Pod 状态</h3>
        <span class="muted-text">${envKey} 环境 · ${pods.length} 个</span>
      </div>
      <div class="pod-list">${cards}</div>
    </div>`;
}

function openCodeModal(title, content) {
  document.getElementById('modalRoot').innerHTML = `
    <div class="overlay" onclick="if(event.target===this) closeModal()">
      <div class="modal" style="width:min(760px,100%)">
        <div class="modal-head">
          <h3>${title}</h3>
          <button class="modal-close" onclick="event.stopPropagation(); closeModal()">×</button>
        </div>
        <div class="modal-body">
          <pre class="code-block">${escapeHtml(content)}</pre>
        </div>
        <div class="modal-foot">
          <button class="btn" onclick="closeModal()">关闭</button>
        </div>
      </div>
    </div>`;
}

function openPodLogs(name) {
  openCodeModal(`Pod 日志 · ${name}`, [
    '2026-11-13T23:30:12Z INFO  app started, profile=dev',
    '2026-11-13T23:30:13Z INFO  HikariPool-1 - Starting...',
    '2026-11-13T23:30:14Z INFO  HikariPool-1 - Start completed.',
    '2026-11-13T23:30:15Z INFO  Tomcat started on port 8080',
    '2026-11-13T23:30:15Z INFO  Started AppApplication in 4.218 seconds',
    '2026-11-13T23:31:02Z INFO  GET /api/info 200 12ms',
    '2026-11-13T23:31:10Z INFO  GET /api/notes 200 8ms'
  ].join('\n'));
}

function openPodYaml(name) {
  openCodeModal(`Pod YAML · ${name}`, [
    'apiVersion: v1',
    'kind: Pod',
    'metadata:',
    `  name: ${name}`,
    '  namespace: app-dev',
    '  labels:',
    '    app: api',
    'spec:',
    '  nodeName: k3s-node-1',
    '  containers:',
    '    - name: api',
    '      image: app/api:dev-a1b2c3d',
    '      ports:',
    '        - containerPort: 8080',
    '      readinessProbe:',
    '        httpGet:',
    '          path: /actuator/health/readiness',
    '          port: 8080',
    'status:',
    '  phase: Running'
  ].join('\n'));
}

function currentDeployService() {
  return state.page === 'web-deploy' ? 'web' : 'api';
}

function startDeployTransition() {
  const serviceKey = currentDeployService();
  state.loading = true;
  state.selected = new Set();
  state.pipeline = null;
  state.lastDeployMessage = '';
  renderDeployPage(serviceKey);
  clearTimeout(state.envTimer);
  state.envTimer = setTimeout(() => {
    state.loading = false;
    renderDeployPage(serviceKey);
  }, 420);
}

function selectDeployEnv(envKey) {
  if (state.pipeline && state.pipeline.status === 'running') {
    toast('部署进行中，完成后再切换环境', 'error');
    return;
  }
  if (envKey === state.deployEnv) return;
  state.deployEnv = envKey;
  startDeployTransition();
}

function changeDeployProject(project) {
  if (state.pipeline && state.pipeline.status === 'running') {
    toast('部署进行中，完成后再切换项目', 'error');
    return;
  }
  if (project === state.project) return;
  state.project = project;
  startDeployTransition();
}

function toggleSelect(id) {
  if (state.selected.has(id)) state.selected.delete(id);
  else state.selected.add(id);
  renderDeployPage(SERVICES[state.page === 'web-deploy' ? 'web' : 'api'].key);
}

function toggleSelectAll(checked, serviceKey) {
  const envKey = state.deployEnv;
  if (checked) {
    projectRequirements().forEach(r => {
      if (r.branchStatus !== 'created') return;
      const st = r.services[serviceKey][envKey].status;
      const pending = st === 'locked' || st === 'ready' || st === 'wait_cr' || st === 'rejected';
      if (pending && gateCheck(r, serviceKey, envKey).ok) state.selected.add(r.id);
    });
  } else {
    state.selected = new Set();
  }
  renderDeployPage(serviceKey);
}

function deploySelected(serviceKey) {
  const envKey = state.deployEnv;
  const ids = Array.from(state.selected).filter(id => {
    const r = findReq(id);
    return r && gateCheck(r, serviceKey, envKey).ok;
  });
  if (!ids.length) {
    toast('没有可部署的分支', 'error');
    return;
  }
  openDeployConfirm(serviceKey, ids);
}

function deployOne(serviceKey, id) {
  const envKey = state.deployEnv;
  const r = findReq(id);
  const gate = gateCheck(r, serviceKey, envKey);
  if (!gate.ok) {
    toast(gate.reason, 'error');
    return;
  }
  openDeployConfirm(serviceKey, [id]);
}

function retryFailed(serviceKey) {
  const envKey = state.deployEnv;
  const failed = projectRequirements().filter(r =>
    r.branchStatus === 'created' && r.services[serviceKey][envKey].status === 'failed');
  const ids = failed.filter(r => gateCheck(r, serviceKey, envKey).ok).map(r => r.id);
  if (!ids.length) {
    const gate = gateCheck(failed[0], serviceKey, envKey);
    toast(gate.reason || '没有可重试的分支', 'error');
    return;
  }
  if (ids.length < failed.length) toast(`已跳过 ${failed.length - ids.length} 个不满足门禁的分支`, 'error');
  openDeployConfirm(serviceKey, ids);
}

function openDeployConfirm(serviceKey, ids) {
  if (state.pipeline && state.pipeline.status === 'running') {
    toast('当前环境正在部署，不能重复部署', 'error');
    return;
  }
  const envKey = state.deployEnv;
  const list = ids.map(findReq).filter(Boolean);
  if (!list.length) return;
  state.deployConfirm = { serviceKey, ids };
  const svc = SERVICES[serviceKey];

  document.getElementById('modalRoot').innerHTML = `
    <div class="overlay" onclick="if(event.target===this) closeModal()">
      <div class="modal" style="width:min(560px,100%)">
        <div class="modal-head">
          <h3>确认部署</h3>
          <button class="modal-close" onclick="event.stopPropagation(); closeModal()">×</button>
        </div>
        <div class="modal-body">
          <div class="kv">
            <span>服务</span><span><span class="pill ${serviceKey === 'api' ? 'info' : 'primary'}">${svc.label} ${svc.image}</span></span>
            <span>目标环境</span><span><code>${envKey}</code></span>
            <span>分支数</span><span>${list.length} 个</span>
          </div>
          <div class="confirm-list">
            ${list.map(r => `<div><code>${r.branch}</code><span>${r.id} · ${escapeHtml(r.title)}</span></div>`).join('')}
          </div>
          <div class="notice">确认后加入部署队列。若该环境已有部署进行中，将无法提交。</div>
        </div>
        <div class="modal-foot">
          <button class="btn" onclick="closeModal()">取消</button>
          <button class="btn primary" onclick="confirmDeploy()">确认部署</button>
        </div>
      </div>
    </div>`;
}

function confirmDeploy() {
  const config = state.deployConfirm;
  if (!config) return;
  if (state.pipeline && state.pipeline.status === 'running') {
    closeModal();
    toast('当前环境正在部署，不能重复部署', 'error');
    return;
  }
  const { serviceKey, ids } = config;
  closeModal();
  startDeploy(serviceKey, ids);
}

function defaultSteps() {
  return [
    { name: '拉取代码', status: 'pending', duration: '-' },
    { name: '编译', status: 'pending', duration: '-' },
    { name: '镜像构建', status: 'pending', duration: '-' },
    { name: '部署到 k3s', status: 'pending', duration: '-' },
    { name: '健康检查', status: 'pending', duration: '-' }
  ];
}

async function startDeploy(serviceKey, ids) {
  const list = ids.map(findReq).filter(Boolean);
  if (!list.length) return;
  const tasks = list.map(req => ({ req, sha: req.tip }));
  await runPipeline(serviceKey, state.deployEnv, tasks, '');
}

async function runPipeline(serviceKey, envKey, tasks, successMessage) {
  if (state.pipeline && state.pipeline.status === 'running') {
    toast('已有部署在进行中', 'error');
    return false;
  }
  if (!tasks.length) return false;

  state.pipeline = {
    service: serviceKey,
    env: envKey,
    total: tasks.length,
    index: 0,
    branch: null,
    steps: defaultSteps(),
    status: 'running',
    startTime: now()
  };
  state.selected = new Set();
  state.lastDeployMessage = '';
  renderDeployPage(serviceKey);
  updatePipeline(serviceKey);

  for (let i = 0; i < tasks.length; i++) {
    const task = tasks[i];
    const r = task.req;
    task.previousStatus = r.services[serviceKey][envKey].status;
    r.services[serviceKey][envKey].status = 'running';
    state.pipeline.index = i + 1;
    state.pipeline.branch = r;
    state.pipeline.steps = defaultSteps();
    renderDeployPage(serviceKey);
    renderPipeline(serviceKey);

    for (let s = 0; s < state.pipeline.steps.length; s++) {
      state.pipeline.steps[s].status = 'running';
      updatePipeline(serviceKey);
      const ms = 1800 + Math.floor(Math.random() * 400);
      await sleep(ms);
      if (state.pipeline.cancelled) break;
      state.pipeline.steps[s].status = 'success';
      state.pipeline.steps[s].duration = (ms / 1000).toFixed(1) + 's';
      updatePipeline(serviceKey);
    }

    if (state.pipeline.cancelled) break;

    r.services[serviceKey][envKey] = {
      status: 'success',
      sha: task.sha,
      operator: currentUser(),
      time: now(),
      approver: envKey === 'test' ? '张工' : undefined
    };
    pushDeployment(r, serviceKey, envKey, 'success');
  }

  if (state.pipeline.cancelled) {
    state.pipeline.status = 'terminated';
    const current = state.pipeline.branch;
    if (current) {
      const envState = current.services[serviceKey][envKey];
      if (envState.status === 'running') envState.status = 'ready';
    }
    renderDeployPage(serviceKey);
    if (!state.pipeline.abortNotified) toast('部署已终止');
    return false;
  }

  state.pipeline.status = 'success';
  state.lastDeployMessage = successMessage
    || `部署成功：${tasks.map(t => t.req.id).join('、')} 已发布到 ${envKey}（${state.pipeline.startTime} 开始）`;
  renderDeployPage(serviceKey);
  renderPipeline(serviceKey);
  toast(state.lastDeployMessage);
  return true;
}

function abortDeployment() {
  const p = state.pipeline;
  if (!p || p.status !== 'running') return;
  p.cancelled = true;
  p.status = 'terminated';
  p.abortNotified = true;
  p.steps.forEach(step => {
    if (step.status === 'running') step.status = 'failed';
  });
  const current = p.branch;
  if (current) {
    const envState = current.services[p.service][p.env];
    if (envState.status === 'running') envState.status = 'ready';
  }
  renderDeployPage(p.service);
  toast('部署已终止');
}

function updatePipeline(serviceKey) {
  renderPipeline(serviceKey);
}

function renderPipeline(serviceKey) {
  const container = document.getElementById('pipeline-' + serviceKey);
  if (!container) return;
  container.innerHTML = pipelineSection(serviceKey);
}

/* ---------- CR 指定 ---------- */
function openCrDialog(id, serviceKey) {
  const r = findReq(id);
  const isPending = r.cr.status === 'pending';
  const currentApprover = r.cr.approver || '张工';

  document.getElementById('modalRoot').innerHTML = `
    <div class="overlay" onclick="if(event.target===this) closeModal()">
      <div class="modal" style="width:min(520px,100%)">
        <div class="modal-head">
          <h3>指定 CR 人员</h3>
          <button class="modal-close" onclick="closeModal()">×</button>
        </div>
        <div class="modal-body">
          <div class="kv">
            <span>需求</span><span>${r.id} · ${escapeHtml(r.title)}</span>
            <span>服务</span><span><span class="pill ${serviceKey === 'api' ? 'info' : 'primary'}">${SERVICES[serviceKey].label} ${SERVICES[serviceKey].image}</span></span>
            <span>目标环境</span><span><code>test</code></span>
            <span>分支</span><span><code>${r.branch}</code></span>
            <span>当前状态</span><span>${isPending ? '<span class="pill warning"><i></i>待审批</span>' : r.cr.status === 'approved' ? '<span class="pill success"><i></i>已通过</span>' : '<span class="pill muted"><i></i>未指定</span>'}</span>
          </div>
          <label class="field" style="margin-bottom:14px">审批人
            <select id="crApprover">
              <option value="张工" ${currentApprover === '张工' ? 'selected' : ''}>张工</option>
              <option value="李工" ${currentApprover === '李工' ? 'selected' : ''}>李工</option>
            </select>
          </label>
          <label class="field">备注
            <textarea id="crNote" placeholder="给审批人的说明（可选）"></textarea>
          </label>
          <div class="notice">提交后该需求进入「审批待办」，CR 通过后才能部署到 test。</div>
        </div>
        <div class="modal-foot">
          <button class="btn" onclick="closeModal()">取消</button>
          <button class="btn primary" onclick="submitCrDialog('${r.id}','${serviceKey}')">提交 CR</button>
        </div>
      </div>
    </div>`;
}

function submitCrDialog(id, serviceKey) {
  const r = findReq(id);
  const approver = document.getElementById('crApprover').value;
  const note = document.getElementById('crNote').value.trim();
  r.cr = {
    status: 'pending',
    approver,
    service: serviceKey,
    requester: currentUser(),
    time: now(),
    note
  };
  if (r.services[serviceKey].test.status !== 'success') {
    r.services[serviceKey].test = { status: 'wait_cr', sha: r.tip, operator: currentUser(), time: now() };
  }
  closeModal();
  renderAll();
  toast(`已提交 CR，审批人 ${approver}`);
}

/* ---------- 审批 ---------- */
function filterApprovalRows(query, service) {
  if (query !== null && query !== undefined) state.approvalQuery = query;
  if (service) state.approvalService = service;
  const q = (state.approvalQuery || '').trim().toLowerCase();
  document.querySelectorAll('#approvalTable tbody tr[data-id]').forEach(tr => {
    const matchQuery = !q || tr.dataset.text.includes(q);
    const matchService = state.approvalService === 'all' || tr.dataset.service === state.approvalService;
    tr.style.display = matchQuery && matchService ? '' : 'none';
  });
}

function renderApprovals() {
  const items = state.requirements.filter(r => r.cr.status === 'pending');
  const canAct = state.role === 'approver';

  const rows = items.length
    ? items.map(r => {
      const svc = SERVICES[r.cr.service];
      return `
        <tr data-id="${r.id}" data-service="${r.cr.service}" data-text="${(r.id + ' ' + r.title + ' ' + r.branch).toLowerCase()}">
          <td>
            <button class="link" onclick="openDetail('${r.id}')">${r.id}</button>
            <div class="title">${escapeHtml(r.title)}</div>
          </td>
          <td><span class="pill ${r.cr.service === 'api' ? 'info' : 'primary'}">${svc.label} ${svc.image}</span></td>
          <td><code>${r.branch}</code></td>
          <td><code>${r.tip}</code></td>
          <td>${r.cr.requester}</td>
          <td>${r.cr.time}</td>
          <td><span class="pill warning"><i></i>待 CR</span></td>
          <td class="ops">
            <button class="btn small" onclick="openDetail('${r.id}')">查看</button>
            <button class="btn small danger" onclick="rejectReq('${r.id}')" ${canAct ? '' : 'disabled'}>驳回</button>
            <button class="btn small primary" onclick="approveReq('${r.id}')" ${canAct ? '' : 'disabled'}>通过</button>
          </td>
        </tr>`;
    }).join('')
    : `<tr><td colspan="8" class="empty">暂无待审批事项</td></tr>`;

  document.getElementById('page-approvals').innerHTML = `
    <div class="page-head">
      <div>
        <h1>审批待办</h1>
        <p>仅 test 首次部署需要审批；通过后平台自动合并到 test 并部署对应服务</p>
      </div>
    </div>
    <div class="card">
      <div class="toolbar">
        <input class="search" placeholder="搜索需求编号、标题或分支" value="${escapeHtml(state.approvalQuery)}" oninput="filterApprovalRows(this.value)">
        <select onchange="filterApprovalRows(null, this.value)">
          <option value="all" ${state.approvalService === 'all' ? 'selected' : ''}>全部服务</option>
          <option value="api" ${state.approvalService === 'api' ? 'selected' : ''}>后端 api</option>
          <option value="web" ${state.approvalService === 'web' ? 'selected' : ''}>前端 web</option>
        </select>
        <select><option>待 CR</option></select>
      </div>
      <div class="table-wrap">
        <table id="approvalTable">
          <thead>
            <tr>
              <th style="width:22%">需求</th>
              <th style="width:12%">服务</th>
              <th style="width:18%">分支</th>
              <th style="width:10%">提交</th>
              <th style="width:9%">提交人</th>
              <th style="width:11%">提交时间</th>
              <th style="width:8%">状态</th>
              <th style="width:16%"></th>
            </tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
      ${canAct ? '' : '<div class="toolbar" style="border-bottom:0; color:var(--text-3); font-size:12px">当前是开发视角，仅 CR 管理员可操作审批。</div>'}
    </div>`;

  if (state.approvalQuery || state.approvalService !== 'all') {
    filterApprovalRows(null, state.approvalService);
  }
}

function approveReq(id) {
  if (state.role !== 'approver') {
    toast('仅 CR 管理员可审批', 'error');
    return;
  }
  const r = findReq(id);
  const serviceKey = r.cr.service;
  const s = r.services[serviceKey];
  r.cr.status = 'approved';
  r.cr.approver = currentUser();
  r.cr.time = now();
  s.test = { status: 'running', sha: r.tip, operator: r.cr.requester, time: now() };
  renderAll();
  toast('已通过，开始部署 test');
  setTimeout(() => {
    s.test = { status: 'success', sha: r.tip, operator: r.cr.requester, time: now(), approver: currentUser() };
    pushDeployment(r, serviceKey, 'test', 'success');
    renderAll();
    toast('test 部署成功');
  }, 1600);
}

function rejectReq(id) {
  if (state.role !== 'approver') {
    toast('仅 CR 管理员可审批', 'error');
    return;
  }
  const r = findReq(id);
  const s = r.services[r.cr.service];
  r.cr.status = 'rejected';
  r.cr.time = now();
  s.test = { status: 'rejected', sha: r.tip, time: now() };
  renderAll();
  toast('已驳回，等待开发修改');
}

/* ---------- 部署记录 ---------- */
function renderDeployments() {
  const rows = state.deployments.map(d => `
    <tr>
      <td>${d.time}</td>
      <td><button class="link" onclick="openDetail('${d.id}')">${d.id}</button></td>
      <td><span class="pill ${d.service === 'api' ? 'info' : 'primary'}">${SERVICES[d.service].label}</span></td>
      <td>${d.env}</td>
      <td><code>${d.sha}</code></td>
      <td>${statusPill(d.status)}</td>
      <td>${d.by}</td>
      <td>${d.approver}</td>
      <td>${d.duration}</td>
      <td><button class="link" onclick="toast('原型：跳转 GitLab 流水线')">流水线</button></td>
    </tr>`).join('');

  document.getElementById('page-deployments').innerHTML = `
    <div class="page-head">
      <div>
        <h1>部署记录</h1>
        <p>前后端分开记录，含触发人、审批人、SHA 与耗时</p>
      </div>
    </div>
    <div class="card">
      <div class="toolbar">
        <input class="search" placeholder="搜索需求编号或 SHA">
        <select><option>全部服务</option><option>后端 api</option><option>前端 web</option></select>
        <select><option>全部环境</option><option>dev</option><option>test</option><option>uat</option><option>prod</option></select>
        <select><option>全部状态</option><option>成功</option><option>失败</option><option>待 CR</option></select>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>时间</th><th>需求</th><th>服务</th><th>环境</th><th>SHA</th><th>状态</th><th>触发人</th><th>审批人</th><th>耗时</th><th></th></tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
    </div>`;
}

function pushDeployment(r, serviceKey, envKey, status) {
  state.deployments.unshift({
    time: now(),
    id: r.id,
    service: serviceKey,
    env: envKey,
    sha: r.tip,
    status: status || 'success',
    by: currentUser(),
    approver: envKey === 'test' ? '张工' : '-',
    duration: '1m 18s'
  });
}

/* ---------- 设置 ---------- */
function renderSettings() {
  document.getElementById('page-settings').innerHTML = `
    <div class="page-head">
      <div>
        <h1>项目设置</h1>
        <p>平台只保存仓库与流程信息，构建逻辑在仓库自己的 .gitlab-ci.yml</p>
      </div>
    </div>
    <div class="card">
      <div class="panel-body">
        <div class="form-grid">
          <label class="field wide">仓库地址
            <input value="http://localhost:8080/dev/app.git">
          </label>
          <label class="field">Access Token
            <input type="password" value="glpat-xxxxxxxxxxxxxxxx">
            <small>AES 加密存储，只用于建分支、合 MR、收流水线状态</small>
          </label>
          <label class="field">CR 审批人
            <input value="张工（zhang.gong）">
            <small>test 首次部署的指定审批人</small>
          </label>
          <label class="field">dev 分支
            <input value="dev">
          </label>
          <label class="field">test 分支
            <input value="test">
          </label>
          <label class="field">uat 分支
            <input value="uat">
          </label>
          <label class="field">prod 分支
            <input value="main">
          </label>
        </div>
        <div class="form-actions">
          <button class="btn" onclick="toast('已重置')">重置</button>
          <button class="btn primary" onclick="toast('项目配置已保存')">保存配置</button>
        </div>
      </div>
    </div>`;
}

/* ---------- 新建需求 ---------- */
function openNewRequirement() {
  document.getElementById('modalRoot').innerHTML = `
    <div class="overlay" onclick="if(event.target===this) closeModal()">
      <div class="modal">
        <div class="modal-head">
          <h3>新建需求</h3>
          <button class="modal-close" onclick="closeModal()">×</button>
        </div>
        <div class="modal-body">
          <label class="field" style="margin-bottom:14px">需求标题
            <input id="newTitle" placeholder="例如：首页增加公告栏">
          </label>
          <label class="field">需求描述
            <textarea id="newDesc" placeholder="背景、范围、验收要点"></textarea>
          </label>
          <div class="notice">创建需求后，在需求详情里点「创建开发分支」，平台会从 main 拉出 <code>feature/REQ-xxx</code>。</div>
        </div>
        <div class="modal-foot">
          <button class="btn" onclick="closeModal()">取消</button>
          <button class="btn primary" onclick="submitNewRequirement()">创建需求</button>
        </div>
      </div>
    </div>`;
}

function submitNewRequirement() {
  const title = document.getElementById('newTitle').value.trim();
  const desc = document.getElementById('newDesc').value.trim();
  if (!title) {
    toast('请填写需求标题', 'error');
    return;
  }
  const seq = String(state.requirements.length + 1).padStart(3, '0');
  const id = 'REQ-' + seq;
  state.requirements.unshift({
    id,
    project: state.project,
    title,
    branch: 'feature/' + id,
    branchStatus: 'none',
    owner: currentUser(),
    createdAt: now(),
    tip: null,
    desc: desc || '（暂无描述）',
    cr: { status: 'none' },
    services: emptyServices()
  });
  closeModal();
  showPage('requirements');
  toast(`已创建 ${id}，可继续创建开发分支`);
}

/* ---------- toast / 初始化 ---------- */
function closeModal() {
  state.modal = null;
  state.reqModal = null;
  state.deployConfirm = null;
  state.rollbackConfirm = null;
  document.getElementById('modalRoot').innerHTML = '';
}

function toast(message, type) {
  const el = document.getElementById('toast');
  el.textContent = message;
  el.classList.remove('hidden', 'error');
  if (type === 'error') el.classList.add('error');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add('hidden'), 2400);
}

document.addEventListener('keydown', e => {
  if (e.key === 'Escape') closeModal();
});

window.addEventListener('load', () => {
  const hash = location.hash.replace('#', '');
  if (!hash) return;
  doLogin();
  if (hash === 'detail') openDetail('REQ-002');
  else if (hash === 'api-deploy') showPage('api-deploy');
  else if (hash === 'web-deploy') showPage('web-deploy');
  else if (hash === 'deploy') { showPage('api-deploy'); state.deployEnv = 'test'; renderDeployPage('api'); }
  else if (hash === 'uat') { showPage('api-deploy'); state.deployEnv = 'uat'; renderDeployPage('api'); }
  else if (hash === 'prod') { showPage('api-deploy'); state.deployEnv = 'prod'; renderDeployPage('api'); }
  else if (hash === 'approvals') { setRole('approver', true); showPage('approvals'); }
  else if (hash === 'new') { showPage('requirements'); openNewRequirement(); }
  else showPage('requirements');
});
