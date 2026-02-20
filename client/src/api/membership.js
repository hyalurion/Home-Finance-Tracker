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
 * 更新用户头像
 * @param {string} username - 用户名
 * @param {string} avatar - 头像数据（Base64格式）
 * @returns {Promise<Object>} 更新结果
 */
export const updateUserAvatar = async (username, avatar) => {
  try {
    const response = await apiClient.put(`/members/${username}/avatar`, { avatar })
    return response.data
  } catch (error) {
    console.error('更新头像失败:', error)
    throw error
  }
}

export default {
  getOrCreateUser,
  updateUserAvatar
}