<template>
  <div class="membership-container">
    <MessageTip v-model:message="successMessage" type="success" />
    <MessageTip v-model:message="errorMessage" type="error" />
    <Header :title="$t('membership.title')" />
    
    <div class="back-button-container">
      <GlassButton 
        type="default" 
        @click="goToHome"
        class="back-button"
      >
        <
      </GlassButton>
    </div>

    <!-- 用户登录/注册表单 -->
    <div class="login-form" v-if="!isLoggedIn">
      <GlassForm @submit.prevent="handleLogin">
        <GlassFormItem :label="$t('membership.goldStreamUsername')" prop="username" error="">
          <GlassInput 
            v-model="loginForm.username" 
            :placeholder="$t('membership.usernamePlaceholder')" 
            showWordLimit
            :maxlength="20"
          ></GlassInput>
        </GlassFormItem>
        <GlassFormItem>
          <GlassButton type="primary" @click="handleLogin" :disabled="isLoggingIn">
            {{ $t('membership.loginOrRegister') }}
          </GlassButton>
        </GlassFormItem>
      </GlassForm>
    </div>
    
    <!-- 用户信息和当前会员状态 -->
    <div class="user-info-card" v-if="isLoggedIn && userInfo">
      <div class="user-info-content">
        <AvatarUpload
          v-model="avatarUrl"
          @avatar-uploaded="handleAvatarUploaded"
          :max-size="10"
          :size="100"
          :username="userInfo?.username"
        />
        <div class="user-details">
          <p>{{ userInfo.username }}</p>
        </div>
      </div>
      <GlassButton 
        type="warning" 
        @click="logout"
        style="margin-left: 10px"
      >
        {{ $t('membership.logout') }}
      </GlassButton>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Header from '@/components/Header.vue';
import GlassButton from '@/components/GlassButton.vue';
import GlassForm from '@/components/GlassForm.vue';
import GlassFormItem from '@/components/GlassFormItem.vue';
import GlassInput from '@/components/GlassInput.vue';
import MessageTip from '@/components/MessageTip.vue';
import AvatarUpload from '@/components/AvatarUpload.vue';
import '../styles/membership.css';

export default {
  name: 'MembershipView',
  components: {
    GlassButton,
    GlassForm,
    GlassFormItem,
    GlassInput,
    MessageTip,
    AvatarUpload
  },
  setup() {
    const router = useRouter()
    const { t, locale } = useI18n()
    const isLoggingIn = ref(false)
    const userInfo = ref(null)
    const successMessage = ref('')
    const errorMessage = ref('')
    const avatarUrl = ref('')
    const isLoggedIn = ref(false)
    const loginFormRef = ref(null)
    const loginForm = ref({
      username: ''
    })
    const formLabelWidth = '100px'
    
    // 返回主页
    const goToHome = () => {
      router.push('/')
    }
    
    // 登录表单验证规则
    const loginRules = {
      username: [
        { required: true, message: t('membership.error.requiredUsername'), trigger: 'blur' },
        { pattern: /^[A-Za-z]+$/, message: t('membership.error.usernameFormat'), trigger: 'blur' },
        { min: 1, max: 20, message: t('membership.error.usernameLength'), trigger: 'blur' }
      ]
    }
    
    // 从本地存储获取用户名
    const getUsername = () => {
      return localStorage.getItem('username') || ''
    }

    // 从本地存储获取头像
    const getAvatar = () => {
      return localStorage.getItem('avatar-' + getUsername()) || ''
    }

    // 保存头像到本地存储
    const saveAvatar = (avatarDataUrl) => {
      localStorage.setItem('avatar-' + getUsername(), avatarDataUrl)
      avatarUrl.value = avatarDataUrl
    }

    // 处理头像上传完成
    const handleAvatarUploaded = (avatarDataUrl) => {
      saveAvatar(avatarDataUrl)
      successMessage.value = t('membership.success.avatarUploaded')
    }
    
    // 检查是否已登录
    const checkLoginStatus = () => {
      const username = getUsername()
      isLoggedIn.value = !!username
      loginForm.value.username = username
      if (isLoggedIn.value) {
        userInfo.value = { username }
        avatarUrl.value = getAvatar()
      } else {
        userInfo.value = null
        avatarUrl.value = ''
      }
    }
    
    // 处理登录/注册
    const handleLogin = async () => {
      if (!loginForm.value.username.trim()) {
        errorMessage.value = t('membership.error.requiredUsername')
        return
      }
      
      // 验证用户名格式，只允许大小写英文字母
      const usernamePattern = /^[A-Za-z]+$/
      if (!usernamePattern.test(loginForm.value.username)) {
        errorMessage.value = t('membership.error.usernameFormat')
        return
      }
      
      try {
        isLoggingIn.value = true
        
        // 保存用户名到本地存储
        localStorage.setItem('username', loginForm.value.username)
        isLoggedIn.value = true
        
        // 设置用户信息
        userInfo.value = { username: loginForm.value.username }
        avatarUrl.value = getAvatar()
        
        successMessage.value = t('membership.success.login')
      } catch (error) {
        console.error('登录失败:', error)
        errorMessage.value = t('membership.error.loginFailed')
      } finally {
        isLoggingIn.value = false
      }
    }
    
    // 处理登出
    const logout = () => {
      localStorage.removeItem('username')
      isLoggedIn.value = false
      userInfo.value = null
      successMessage.value = t('membership.success.logout')
    }
    
    
    // 页面加载时获取数据
    onMounted(() => {
      // 首先检查登录状态
      checkLoginStatus()
    })
    
    return {
      isLoggingIn,
      userInfo,
      isLoggedIn,
      loginForm,
      loginFormRef,
      loginRules,
      formLabelWidth,
      handleLogin,
      logout,
      successMessage,
      errorMessage,
      goToHome,
      avatarUrl,
      handleAvatarUploaded
    }
  }
}
</script>