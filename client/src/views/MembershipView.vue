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
      <h2>{{ $t('membership.userInfo') }}</h2>
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
          <p v-if="currentSubscription">
            {{ currentSubscription.SubscriptionPlan?.name }}
          </p>
          <p v-if="currentSubscription">
            {{ $t('membership.expiresOn') }}: {{ formatDate(currentSubscription.endDate) }}
          </p>
          <p v-if="!currentSubscription">
            {{ $t('membership.noActiveSubscription') }}
          </p>
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
    
    <div style="text-align: center;">
      <p v-html="$t('membership.paymentMethod')"></p>
      <br>
    </div>

    <!-- 订阅计划列表 -->
    <div class="plans-container">
      <h2>{{ $t('membership.selectPlan') }}</h2>
      <div class="plans-grid">
        <div 
          v-for="plan in subscriptionPlans" 
          :key="plan.id"
          class="plan-card"
          :class="{ selected: selectedPlanId === plan.id }"
          @click="selectPlan(plan.id)"
        >
          <div class="plan-header">
            <h3>{{ plan.name }}</h3>
            <div class="price-tag">¥{{ plan.price }}</div>
          </div>
          <div class="plan-description">{{ plan.description }}</div>
          <GlassButton 
            type="primary" 
            class="subscribe-button"
            :disabled="isProcessing"
            @click.stop="subscribe(plan)"
          >
            {{ $t('membership.subscribe') }}
          </GlassButton>
        </div>
      </div>
    </div>

    <!-- 金流服务费用提示 -->
    <div class="payment-fee-notice">
      <GlassAlert
        :title="$t('feeNoticeTitle')"
        type="info"
        :closable="false"
        class="fee-alert"
      >
        <p class="fee-message">{{ $t('feeNoticeContent') }}</p>
      </GlassAlert>
      <br>
    </div>
    
    <!-- 订阅历史记录 -->
      <div class="history-container">
        <h2>{{ $t('membership.subscriptionHistory') }}</h2>
        <GlassTable :data="subscriptionHistory" :columns="subscriptionColumns" style="width: 100%" />
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
import GlassAlert from '@/components/GlassAlert.vue';
import GlassTable from '@/components/GlassTable.vue';
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
    GlassAlert,
    GlassTable,
    MessageTip,
    AvatarUpload
  },
  setup() {
    const router = useRouter()
    const { t } = useI18n()
    const subscriptionPlans = ref([])
    const selectedPlanId = ref(null)
    const isProcessing = ref(false)
    const isCancelling = ref(false)
    const isLoggingIn = ref(false)
    const userInfo = ref(null)
    const currentSubscription = ref(null)
    const subscriptionHistory = ref([])
    const successMessage = ref('')
    const errorMessage = ref('')
    const avatarUrl = ref('')
    const subscriptionColumns = ref([
      { label: t('membership.plan'), prop: 'planName' },
      { label: t('membership.startDate'), prop: 'startDate' },
      { label: t('membership.endDate'), prop: 'endDate' },
      { label: t('membership.status'), prop: 'status' }
    ])
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
        avatarUrl.value = getAvatar()
      } else {
        avatarUrl.value = ''
      }
    }
    
    // 格式化日期
    const formatDate = (dateString) => {
      if (!dateString) return ''
      const date = new Date(dateString)
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    }
    
    // 获取订阅计划列表
    const fetchSubscriptionPlans = async () => {
      try {
        // 使用正确的API路径格式
        const response = await fetch('/api/members/subscription-plans', {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json'
          }
        })
        
        if (response.ok) {
          const data = await response.json()
          subscriptionPlans.value = data.data || data
        } else {
          throw new Error(t('membership.error.fetchSubscriptionFailed'))
        }
      } catch (error) {
        errorMessage.value = t('membership.error.fetchSubscriptionFailed')
        console.error(t('membership.error.fetchSubscriptionFailed') + ':', error)
      }
    }
    
    // 获取用户信息和当前订阅
    const fetchUserInfo = async () => {
      if (!isLoggedIn.value) return
      
      try {
        const username = getUsername()
        
        // 优化：直接使用用户名调用API，避免404错误
        // 创建或获取用户 - 注意路径格式：/api/members/members/:username
        const userResponse = await fetch('/api/members/members/' + encodeURIComponent(username), {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json'
          }
        })
        
        if (userResponse.ok) {
          const userData = await userResponse.json()
          userInfo.value = userData.data || userData
          
          // 更新头像数据
          if (userInfo.value && userInfo.value.avatar) {
            avatarUrl.value = userInfo.value.avatar
          }
        } else {
          // 如果用户不存在，尝试创建用户
          const createUserResponse = await fetch('/api/members/members', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username })
          })
          
          if (createUserResponse.ok) {
            const userData = await createUserResponse.json()
            userInfo.value = userData.data || userData
          } else {
            throw new Error(t('membership.error.createUserFailed'))
          }
        }
        
        // 获取当前订阅
        try {
          const subscriptionResponse = await fetch('/api/members/members/' + encodeURIComponent(username) + '/current-subscription', {
            method: 'GET',
            headers: {
              'Content-Type': 'application/json'
            }
          })
          
          if (subscriptionResponse.ok) {
            const subscriptionData = await subscriptionResponse.json()
            currentSubscription.value = subscriptionData.data || subscriptionData
          }
        } catch (subError) {
          console.error('获取当前订阅失败:', subError)
        }
        
        // 获取订阅历史
        try {
          const historyResponse = await fetch('/api/members/members/' + encodeURIComponent(username) + '/subscriptions', {
            method: 'GET',
            headers: {
              'Content-Type': 'application/json'
            }
          })
          
          if (historyResponse.ok) {
            const historyData = await historyResponse.json()
            subscriptionHistory.value = historyData.data || historyData
            
            // 转换数据格式，确保与表格列配置匹配
            subscriptionHistory.value = subscriptionHistory.value.map(item => ({
              ...item,
              planName: item.SubscriptionPlan?.name || t('membership.unknownPlan')
            }))
          }
        } catch (histError) {
          console.error('获取订阅历史失败:', histError)
        }
      } catch (error) {
        console.error(t('membership.error.fetchUserInfoFailed') + ':', error)
        errorMessage.value = t('membership.error.fetchUserInfoFailed')
      }
    }
    
    // 选择订阅计划
    const selectPlan = (planId) => {
      selectedPlanId.value = planId
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
        
        // 尝试创建或获取用户
        await fetchUserInfo()
        
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
      currentSubscription.value = null
      subscriptionHistory.value = []
      successMessage.value = t('membership.success.logout')
    }
    
    // 订阅处理
    const subscribe = async (plan) => {
      if (!isLoggedIn.value) {
        errorMessage.value = t('membership.error.loginFirst')
        return
      }
      
      try {
        isProcessing.value = true
        const username = getUsername()
        
        // 调用订阅支付接口 - 直接使用fetch避免404错误
        const paymentResponse = await fetch('/api/payments/subscribe', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ username, planId: plan.period })
        })
        
        const paymentData = await paymentResponse.json()
        
        if (paymentResponse.ok && (paymentData.success || !paymentData.error)) {
          // 创建订阅记录 - 直接使用fetch避免404错误
          const createSubscriptionResponse = await fetch('/api/members/subscriptions', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({
              username,
              planId: plan.period,
              paymentId: paymentData.data?.orderId || 'temp-order-' + Date.now(),
              autoRenew: false
            })
          })
          
          const createData = await createSubscriptionResponse.json();
          if (createSubscriptionResponse.ok && (createData.success || !createData.error)) {
            successMessage.value = t('membership.success.subscribe')
            // 刷新用户信息和订阅状态
            await fetchUserInfo()
          } else {
            errorMessage.value = t('membership.error.subscriptionRecordFailed')
          }
        } else {
          errorMessage.value = paymentData.error || t('membership.error.paymentFailed')
        }
      } catch (error) {
        errorMessage.value = t('membership.error.subscribeError')
        console.error('订阅失败:', error)
      } finally {
        isProcessing.value = false
      }
    }
    
    // 取消订阅
    const cancelSubscription = async () => {
      if (!isLoggedIn.value) {
        errorMessage.value = t('membership.error.loginFirst')
        return
      }
      
      try {
        isCancelling.value = true
        const username = getUsername()
        
        // 直接使用fetch避免404错误
        const response = await fetch('/api/members/members/' + encodeURIComponent(username) + '/subscriptions', {
          method: 'DELETE',
          headers: {
            'Content-Type': 'application/json'
          }
        })
        
        const responseData = await response.json()
        
        if (response.ok && (responseData.success || !responseData.error)) {
          successMessage.value = t('membership.success.cancelSubscription')
          await fetchUserInfo()
        } else {
          errorMessage.value = responseData.error || t('membership.error.cancelSubscriptionFailed')
        }
      } catch (error) {
        errorMessage.value = t('membership.error.cancelSubscriptionError')
        console.error('取消订阅失败:', error)
      } finally {
        isCancelling.value = false
      }
    }
    
    // 页面加载时获取数据
    onMounted(() => {
      // 首先检查登录状态
      checkLoginStatus()
      // 获取订阅计划列表（无论是否登录都可以查看）
      fetchSubscriptionPlans()
      // 如果已登录，获取用户信息
      if (isLoggedIn.value) {
        fetchUserInfo()
      }
    })
    
    return {
      subscriptionPlans,
      selectedPlanId,
      isProcessing,
      isCancelling,
      isLoggingIn,
      userInfo,
      currentSubscription,
      subscriptionHistory,
      subscriptionColumns,
      isLoggedIn,
      loginForm,
      loginFormRef,
      loginRules,
      formLabelWidth,
      selectPlan,
      subscribe,
      cancelSubscription,
      formatDate,
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