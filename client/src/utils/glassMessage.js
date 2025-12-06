import { createApp } from 'vue'
import GlassMessage from '../components/GlassMessage.vue'

// 消息容器
let container = null

// 创建消息容器
const createContainer = () => {
  if (container) return container
  
  container = document.createElement('div')
  container.className = 'glass-message-container'
  document.body.appendChild(container)
  
  return container
}

// 创建消息实例
const createMessage = (options) => {
  // 确保容器存在
  const messageContainer = createContainer()
  
  // 处理参数
  const messageOptions = typeof options === 'string' ? { message: options } : options
  
  // 创建Vue应用实例
  const app = createApp(GlassMessage, {
    ...messageOptions,
    onClose: () => {
      // 关闭时销毁应用
      app.unmount(messageElement)
      messageElement.remove()
      
      // 如果容器为空，移除容器
      if (messageContainer.children.length === 0) {
        messageContainer.remove()
        container = null
      }
      
      // 调用用户提供的onClose回调
      if (messageOptions.onClose) {
        messageOptions.onClose()
      }
    }
  })
  
  // 挂载应用
  const messageElement = document.createElement('div')
  messageContainer.appendChild(messageElement)
  app.mount(messageElement)
  
  return {
    close: () => {
      app.unmount(messageElement)
      messageElement.remove()
    }
  }
}

// 创建各种类型的消息方法
const messageMethods = {
  success(options) {
    return createMessage({
      ...(typeof options === 'string' ? { message: options } : options),
      type: 'success'
    })
  },
  warning(options) {
    return createMessage({
      ...(typeof options === 'string' ? { message: options } : options),
      type: 'warning'
    })
  },
  error(options) {
    return createMessage({
      ...(typeof options === 'string' ? { message: options } : options),
      type: 'error'
    })
  },
  info(options) {
    return createMessage({
      ...(typeof options === 'string' ? { message: options } : options),
      type: 'info'
    })
  },
  closeAll() {
    if (container) {
      container.innerHTML = ''
      container.remove()
      container = null
    }
  }
}

export default messageMethods
