import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';
import { STORAGE_KEYS } from '@/utils/constants';
import { logApiRequest, logApiResponse, logApiError } from '@/utils/operationLogger';
import { ExpenseAPI } from '@/api/expenses';

/**
 * 离线数据同步工具
 * 基于IndexedDB实现离线数据存储和网络恢复后的自动同步
 * 支持UUID和版本控制
 */
class OfflineDataSync {
  constructor () {
    this.dbName = 'HomeMoneyDB';
    this.dbVersion = 2;
    this.stores = {
      cache: 'keyValueCache',
      syncQueue: 'syncQueue',
      expenses: 'expenses'
    };
    this.db = null;
    this.initPromise = null;
    this.initDB();
    this.setupNetworkListeners();
  }

  /**
   * 初始化IndexedDB数据库
   */
  async initDB () {
    if (this.initPromise) {
      return this.initPromise;
    }

    this.initPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(this.dbName, this.dbVersion);

      request.onupgradeneeded = (event) => {
        const db = event.target.result;

        // 创建键值对缓存存储
        if (!db.objectStoreNames.contains(this.stores.cache)) {
          db.createObjectStore(this.stores.cache, { keyPath: 'key' });
        }

        // 创建同步队列存储
        if (!db.objectStoreNames.contains(this.stores.syncQueue)) {
          db.createObjectStore(this.stores.syncQueue, {
            keyPath: 'id',
            autoIncrement: true
          });
        }

        // 创建消费记录存储（支持UUID主键）
        if (!db.objectStoreNames.contains(this.stores.expenses)) {
          const expenseStore = db.createObjectStore(this.stores.expenses, {
            keyPath: 'id'
          });
          expenseStore.createIndex('updatedAt', 'updatedAt', { unique: false });
          expenseStore.createIndex('isSynced', 'isSynced', { unique: false });
          expenseStore.createIndex('deletedAt', 'deletedAt', { unique: false });
        }
      };

      request.onsuccess = (event) => {
        this.db = event.target.result;
        resolve(this.db);
      };

      request.onerror = (event) => {
        console.error('IndexedDB初始化失败:', event.target.error);
        reject(event.target.error);
      };

      request.onblocked = (event) => {
        console.warn('IndexedDB被阻塞，请关闭其他标签页');
        reject(new Error('IndexedDB blocked'));
      };
    });

    return this.initPromise;
  }

  /**
   * 确保数据库已初始化
   */
  async ensureDB () {
    if (!this.db) {
      await this.initDB();
    }
    return this.db;
  }

  /**
   * 获取数据库事务
   */
  getTransaction (storeName, mode = 'readonly') {
    if (!this.db) {
      throw new Error('Database not initialized');
    }
    return this.db.transaction(storeName, mode).objectStore(storeName);
  }

  /**
   * 生成UUID
   */
  generateId () {
    return uuidv4();
  }

  /**
   * 保存消费记录到本地（带版本控制）
   */
  async saveExpense (expense, isSynced = false) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.expenses, 'readwrite');
    
    const expenseData = {
      id: expense.id || this.generateId(),
      ...expense,
      version: expense.version || 1,
      updatedAt: expense.updatedAt || Date.now(),
      isSynced: isSynced,
      deletedAt: expense.deletedAt || null
    };

    return new Promise((resolve, reject) => {
      const request = store.put(expenseData);
      request.onsuccess = () => resolve(expenseData);
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * 获取本地消费记录
   */
  async getExpense (id) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.expenses);
    return new Promise((resolve, reject) => {
      const request = store.get(id);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * 获取所有未同步的变更
   */
  async getPendingChanges () {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.expenses);
    return new Promise((resolve, reject) => {
      const request = store.getAll();
      request.onsuccess = () => {
        const allExpenses = request.result || [];
        const pending = allExpenses.filter(e => !e.isSynced);
        resolve(pending);
      };
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * 获取所有未删除的消费记录
   */
  async getAllExpenses () {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.expenses);
    return new Promise((resolve, reject) => {
      const request = store.getAll();
      request.onsuccess = () => {
        const allExpenses = request.result || [];
        const active = allExpenses.filter(e => !e.deletedAt);
        active.sort((a, b) => b.updatedAt - a.updatedAt);
        resolve(active);
      };
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * 标记记录为已同步
   */
  async markAsSynced (id) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.expenses, 'readwrite');
    return new Promise((resolve, reject) => {
      const getRequest = store.get(id);
      getRequest.onsuccess = () => {
        const expense = getRequest.result;
        if (expense) {
          expense.isSynced = true;
          const putRequest = store.put(expense);
          putRequest.onsuccess = () => resolve();
          putRequest.onerror = () => reject(putRequest.error);
        } else {
          resolve();
        }
      };
      getRequest.onerror = () => reject(getRequest.error);
    });
  }

  /**
   * 软删除本地记录
   */
  async deleteExpense (id) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.expenses, 'readwrite');
    return new Promise((resolve, reject) => {
      const getRequest = store.get(id);
      getRequest.onsuccess = () => {
        const expense = getRequest.result;
        if (expense) {
          const now = Date.now();
          expense.deletedAt = now;
          expense.updatedAt = now;
          expense.version = (expense.version || 0) + 1;
          expense.isSynced = false;
          const putRequest = store.put(expense);
          putRequest.onsuccess = () => resolve(expense);
          putRequest.onerror = () => reject(putRequest.error);
        } else {
          resolve(null);
        }
      };
      getRequest.onerror = () => reject(getRequest.error);
    });
  }

  /**
   * 获取最后同步时间
   */
  getLastSyncTime () {
    const time = localStorage.getItem('lastSyncTime');
    return time ? parseInt(time, 10) : 0;
  }

  /**
   * 设置最后同步时间
   */
  setLastSyncTime (time) {
    localStorage.setItem('lastSyncTime', time.toString());
  }

  /**
   * 执行同步（使用新的同步API）
   */
  async performSync () {
    if (!navigator.onLine) {
      console.log('离线状态，跳过同步');
      return { success: false, offline: true };
    }

    try {
      await this.ensureDB();
      const lastSyncTime = this.getLastSyncTime();
      const pendingChanges = await this.getPendingChanges();

      console.log(`开始同步，上次同步时间: ${lastSyncTime}, 待同步变更: ${pendingChanges.length}`);

      const syncResult = await ExpenseAPI.syncExpenses(lastSyncTime, pendingChanges);

      if (syncResult.serverChanges && syncResult.serverChanges.length > 0) {
        for (const serverExpense of syncResult.serverChanges) {
          await this.saveExpense(serverExpense, true);
        }
        console.log(`已应用 ${syncResult.serverChanges.length} 条服务器变更`);
      }

      if (syncResult.conflicts && syncResult.conflicts.length > 0) {
        console.warn(`发现 ${syncResult.conflicts.length} 个冲突，已按Last-Write-Wins解决`);
        for (const conflict of syncResult.conflicts) {
          await this.saveExpense(conflict.serverVersion, true);
        }
      }

      for (const change of pendingChanges) {
        await this.markAsSynced(change.id);
      }

      this.setLastSyncTime(syncResult.syncTime);

      console.log('同步完成');
      return {
        success: true,
        serverChanges: syncResult.serverChanges?.length || 0,
        conflicts: syncResult.conflicts?.length || 0
      };
    } catch (error) {
      console.error('同步失败:', error);
      return { success: false, error: error.message };
    }
  }

  /**
   * 缓存API响应数据
   */
  async cacheResponse (key, data) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.cache, 'readwrite');
    return new Promise((resolve, reject) => {
      const request = store.put({ key, data, timestamp: Date.now() });
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * 获取缓存的响应数据
   */
  async getCachedResponse (key) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.cache);
    return new Promise((resolve, reject) => {
      const request = store.get(key);
      request.onsuccess = () => resolve(request.result?.data || null);
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * 将请求添加到同步队列（兼容旧API）
   */
  async queueForSync (request) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.syncQueue, 'readwrite');
    return new Promise((resolve, reject) => {
      const requestData = {
        ...request,
        timestamp: Date.now()
      };
      const dbRequest = store.add(requestData);
      dbRequest.onsuccess = () => resolve(dbRequest.result);
      dbRequest.onerror = () => reject(dbRequest.error);
    });
  }

  /**
   * 获取所有待同步请求
   */
  async getSyncQueue () {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.syncQueue);
    return new Promise((resolve, reject) => {
      const request = store.getAll();
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * 移除已同步的请求
   */
  async removeFromSyncQueue (id) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.syncQueue, 'readwrite');
    return new Promise((resolve, reject) => {
      const request = store.delete(id);
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * 同步队列中的所有请求（兼容旧API）
   */
  async syncQueue () {
    if (!navigator.onLine) return;

    try {
      await this.ensureDB();
      const queue = await this.getSyncQueue();
      if (queue.length === 0) return;

      console.log(`开始同步${queue.length}个离线请求`);
      for (const request of queue) {
        try {
          const response = await axios(request);
          if (response.status >= 200 && response.status < 300) {
            await this.removeFromSyncQueue(request.id);
            console.log(`已同步请求: ${request.url}`);
          }
        } catch (error) {
          console.error(`同步请求失败: ${request.url}`, error);
          break;
        }
      }
    } catch (error) {
      console.error('同步队列处理失败', error);
    }
  }

  /**
   * 设置网络状态监听
   */
  setupNetworkListeners () {
    window.addEventListener('online', () => {
      console.log('网络已恢复，开始同步');
      setTimeout(() => this.performSync(), 1000);
    });

    window.addEventListener('load', () => {
      if (navigator.onLine) {
        setTimeout(() => this.performSync(), 1000);
      }
    });
  }
}

const offlineSync = new OfflineDataSync();

export default offlineSync;

export function setupAxiosInterceptors (axiosInstance) {
  axiosInstance.interceptors.request.use(async (config) => {
    config.timestamp = Date.now();
    
    const token = localStorage.getItem(STORAGE_KEYS.TOKEN);
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    logApiRequest(config);

    if (!navigator.onLine && ['post', 'put', 'delete', 'patch'].includes(config.method)) {
      console.log(`离线模式: 将请求加入同步队列 - ${config.url}`);
      await offlineSync.queueForSync({
        url: config.url,
        method: config.method,
        data: config.data,
        headers: config.headers
      });
      return Promise.reject(new Error('OFFLINE_MODE'));
    }
    return config;
  });

  axiosInstance.interceptors.response.use(async (response) => {
    logApiResponse(response);
    
    if (response.config.method === 'get' && response.status === 200) {
      const cacheKey = `${response.config.method}-${response.config.url}`;
      await offlineSync.cacheResponse(cacheKey, response.data);
    }
    return response;
  }, async (error) => {
    logApiError(error);
    
    if (!navigator.onLine && error.config?.method === 'get') {
      const cacheKey = `${error.config.method}-${error.config.url}`;
      const cachedData = await offlineSync.getCachedResponse(cacheKey);
      if (cachedData) {
        console.log(`离线模式: 使用缓存数据 - ${error.config.url}`);
        return Promise.resolve({ data: cachedData });
      }
    }
    return Promise.reject(error);
  });
}
