<template>
  <div class="login">
    <div class="login-card">
      <div class="login-brand">
        <div class="brand-mark">EP</div>
        <h1>工程平台</h1>
        <p class="tagline">需求 → 分支 → 环境，一条流水线走完</p>
        <ul class="brand-points">
          <li><b>需求驱动</b> 每条需求自动拉出开发分支</li>
          <li><b>顺序门禁</b> dev → test → uat → prod 逐级放行</li>
          <li><b>双人卡点</b> test 首次部署必须 CR 通过</li>
          <li><b>SHA 一致</b> 每一级部署的是同一份提交</li>
        </ul>
        <p class="version">平台 v0.1 · 本地环境</p>
      </div>

      <el-form class="login-form" label-position="top" @submit.prevent="submit">
        <h2>登录</h2>
        <p class="form-sub">使用平台账号登录</p>

        <el-form-item label="账号">
          <el-input v-model="username" autocomplete="username" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password
                    autocomplete="current-password" placeholder="请输入密码" @keyup.enter="submit" />
        </el-form-item>

        <el-button type="primary" class="block" :loading="loading" native-type="submit">
          {{ loading ? '登录中…' : '登 录' }}
        </el-button>

        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="login-error" />

        <div class="login-accounts">
          <div>开发账号：<code>devuser</code> / <code>123456</code></div>
          <div>CR 管理员：<code>zhanggong</code> / <code>123456</code></div>
          <el-button link type="primary" @click="fill('zhanggong')">填入 CR 管理员账号</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();

const username = ref('devuser');
const password = ref('123456');
const loading = ref(false);
const error = ref('');

function fill(account) {
  username.value = account;
  password.value = '123456';
}

async function submit() {
  error.value = '';
  loading.value = true;
  try {
    await auth.login(username.value.trim(), password.value);
    router.push(route.query.redirect || '/requirements');
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
</script>
