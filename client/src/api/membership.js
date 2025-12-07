import axios from 'axios'
import { setupAxiosInterceptors } from '@/utils/offlineDataSync'

// 创建axios实例
const apiClient = axios.create({
  baseURL: '/api/members',
  timeout: 30000
})

// 设置拦截器
setupAxiosInterceptors(apiClient)

/**
 * 获取会员订阅计划列表
 * @returns {Promise<Object>} 订阅计划列表
 */
export const getMembershipPlans = async () => {
  try {
    const response = await apiClient.get('/subscription-plans')
    return response.data
  } catch (error) {
    console.error('获取会员订阅计划失败:', error)
    throw error
  }
}

/**
 * 创建或获取用户信息
 * @param {Object} userData - 用户数据
 * @param {string} userData.username - 用户名
 * @returns {Promise<Object>} 用户信息
 */
export const getOrCreateUser = async (userData) => {
  try {
    const response = await apiClient.post('', userData)
    return response.data
  } catch (error) {
    console.error('创建或获取用户失败:', error)
    throw error
  }
}

/**
 * 获取用户当前订阅
 * @param {string} username - 用户名
 * @returns {Promise<Object>} 当前订阅信息
 */
export const getCurrentSubscription = async (username) => {
  try {
    const response = await apiClient.get(`/${username}/current-subscription`)
    return response.data
  } catch (error) {
    console.error('获取当前订阅失败:', error)
    // 如果获取失败，返回空数据而不是抛出错误，避免影响页面加载
    return { success: true, data: null }
  }
}

/**
 * 获取用户订阅历史
 * @param {string} username - 用户名
 * @returns {Promise<Object>} 订阅历史列表
 */
export const getUserSubscriptionHistory = async (username) => {
  try {
    const response = await apiClient.get(`/${username}/subscriptions`)
    return response.data
  } catch (error) {
    console.error('获取订阅历史失败:', error)
    // 如果获取失败，返回空列表而不是抛出错误，避免影响页面加载
    return { success: true, data: [] }
  }
}

/**
 * 订阅会员计划
 * @param {Object} subscriptionData - 订阅数据
 * @param {string} subscriptionData.username - 用户名
 * @param {string} subscriptionData.planId - 订阅计划ID
 * @returns {Promise<Object>} 支付响应
 */
export const subscribeToPlan = async (subscriptionData) => {
  try {
    // 使用支付API而不是会员API
    const response = await axios.post('/api/payments/subscribe', subscriptionData)
    return response.data
  } catch (error) {
    console.error('订阅失败:', error)
    throw error
  }
}

/**
 * 取消用户订阅
 * @param {string} username - 用户名
 * @returns {Promise<Object>} 取消结果
 */
export const cancelUserSubscription = async (username) => {
  try {
    const response = await apiClient.delete(`/${username}/subscriptions`)
    return response.data
  } catch (error) {
    console.error('取消订阅失败:', error)
    throw error
  }
}

/**
 * 检查会员状态
 * @param {string} username - 用户名
 * @returns {Promise<boolean>} 是否为活跃会员
 */
export const checkMemberStatus = async (username) => {
  try {
    // 强制重新获取，不使用缓存
    const response = await fetch(`/api/members/members/${encodeURIComponent(username)}/current-subscription?cacheBust=${Date.now()}`)
    const subscription = await response.json()
    
    // 严格验证：必须有活跃订阅且totalActiveSubscriptions > 0，才能认为是会员
    return subscription.success && subscription.totalActiveSubscriptions > 0 && !!subscription.data
  } catch (error) {
    console.error('检查会员状态失败:', error)
    // 出错时默认返回false，确保未付费用户不能访问
    return false
  }
}

export default {
  getMembershipPlans,
  getOrCreateUser,
  getCurrentSubscription,
  getUserSubscriptionHistory,
  subscribeToPlan,
  cancelUserSubscription,
  checkMemberStatus
}