/**
 * 分页数据获取工具
 * 提供高效的分页加载机制，避免一次性获取大量数据
 */

// 分页配置
const DEFAULT_PAGINATION_CONFIG = {
  pageSize: 20,           // 每页数据量
  maxConcurrent: 3,        // 最大并发数
  retryTimes: 3,           // 重试次数
  retryDelay: 1000,        // 重试延迟(ms)
  timeout: 30000,          // 请求超时(ms)
  enableProgress: true,    // 是否显示进度
};

/**
 * 分页获取数据的主要函数
 * @param {Object} options - 配置选项
 * @param {Function} options.apiCall - API调用函数，接收{page, limit}参数
 * @param {number} options.pageSize - 每页数据量
 * @param {number} options.maxConcurrent - 最大并发数
 * @param {Function} options.onProgress - 进度回调函数
 * @param {Function} options.onError - 错误回调函数
 * @param {AbortSignal} options.signal - 取消信号
 * @returns {Promise<Array>} - 合并后的所有数据
 */
export async function fetchAllPages(options = {}) {
  const config = { ...DEFAULT_PAGINATION_CONFIG, ...options };
  const {
    apiCall,
    pageSize = config.pageSize,
    maxConcurrent = config.maxConcurrent,
    onProgress,
    onError,
    signal
  } = config;

  if (!apiCall || typeof apiCall !== 'function') {
    throw new Error('apiCall参数必须是一个函数');
  }

  const allData = [];
  let totalCount = 0;
  let isCompleted = false;

  // 创建取消检查函数
  const checkCancellation = () => {
    if (signal?.aborted) {
      throw new Error('操作已被取消');
    }
  };

  try {
    // 首先获取总数
    checkCancellation();
    const firstResponse = await makeApiCallWithRetry(
      () => apiCall({ page: 1, limit: 1 }),
      config,
      signal
    );

    totalCount = getTotalCount(firstResponse);
    
    if (totalCount === 0) {
      console.log('fetchAllPages: 没有数据需要加载');
      return [];
    }

    if (totalCount <= pageSize) {
      // 数据量小于等于一页，直接获取
      const singlePageData = getDataFromResponse(firstResponse);
      console.log(`fetchAllPages: 数据量较小(${totalCount}条)，单页获取完成`);
      return singlePageData;
    }

    console.log(`fetchAllPages: 开始分页加载，共${totalCount}条数据，每页${pageSize}条`);

    // 计算总页数
    const totalPages = Math.ceil(totalCount / pageSize);
    
    // 分批加载数据
    const batches = [];
    for (let page = 1; page <= totalPages; page++) {
      batches.push(page);
    }

    // 并发控制
    const batchSize = Math.min(maxConcurrent, batches.length);
    const results = [];

    for (let i = 0; i < batches.length; i += batchSize) {
      checkCancellation();
      
      const batch = batches.slice(i, i + batchSize);
      const batchPromises = batch.map(page => 
        makeApiCallWithRetry(
          () => apiCall({ page, limit: pageSize }),
          config,
          signal
        ).then(response => ({ page, data: getDataFromResponse(response) }))
      );

      try {
        const batchResults = await Promise.all(batchPromises);
        results.push(...batchResults);
        
        // 按页面顺序排序
        results.sort((a, b) => a.page - b.page);
        
        // 合并数据
        allData.length = 0;
        results.forEach(result => {
          allData.push(...result.data);
        });

        // 进度回调
        if (onProgress && typeof onProgress === 'function') {
          const loadedCount = allData.length;
          const progress = Math.round((loadedCount / totalCount) * 100);
          onProgress({
            loaded: loadedCount,
            total: totalCount,
            progress,
            currentPage: Math.max(...results.map(r => r.page))
          });
        }

        console.log(`fetchAllPages: 已加载 ${allData.length}/${totalCount} 条数据 (${Math.round((allData.length / totalCount) * 100)}%)`);
        
      } catch (batchError) {
        console.error('批次加载失败:', batchError);
        if (onError && typeof onError === 'function') {
          onError(batchError);
        }
        throw batchError;
      }
    }

    isCompleted = true;
    console.log(`fetchAllPages: 完成！共加载 ${allData.length} 条数据`);

    return allData;

  } catch (error) {
    if (error.message === '操作已被取消') {
      console.log('fetchAllPages: 操作被用户取消');
      throw error;
    }
    
    console.error('fetchAllPages: 加载失败', error);
    throw error;
  }
}

/**
 * 带重试机制的API调用
 * @param {Function} apiCall - API调用函数
 * @param {Object} config - 配置
 * @param {AbortSignal} signal - 取消信号
 * @returns {Promise} - API响应
 */
async function makeApiCallWithRetry(apiCall, config, signal) {
  let lastError;
  
  for (let attempt = 0; attempt <= config.retryTimes; attempt++) {
    try {
      checkSignal(signal);
      
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), config.timeout);
      
      const response = await apiCall();
      clearTimeout(timeoutId);
      
      return response;
    } catch (error) {
      lastError = error;
      console.warn(`API调用失败 (尝试 ${attempt + 1}/${config.retryTimes + 1}):`, error.message);
      
      if (attempt < config.retryTimes) {
        await delay(config.retryDelay * Math.pow(2, attempt)); // 指数退避
      }
    }
  }
  
  throw lastError;
}

/**
 * 检查取消信号
 * @param {AbortSignal} signal 
 */
function checkSignal(signal) {
  if (signal?.aborted) {
    throw new Error('操作已被取消');
  }
}

/**
 * 延迟函数
 * @param {number} ms - 延迟毫秒数
 * @returns {Promise}
 */
function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * 从响应中获取数据总数
 * @param {Object} response - API响应
 * @returns {number} - 数据总数
 */
function getTotalCount(response) {
  // 尝试多种可能的响应格式
  if (response?.data?.total !== undefined) {
    return Number(response.data.total);
  }
  if (response?.total !== undefined) {
    return Number(response.total);
  }
  if (response?.data?.length !== undefined) {
    return Number(response.data.length);
  }
  if (Array.isArray(response?.data)) {
    return Number(response.data.length);
  }
  if (Array.isArray(response)) {
    return Number(response.length);
  }
  
  console.warn('无法从响应中获取数据总数，默认为1');
  return 1;
}

/**
 * 从响应中获取数据数组
 * @param {Object} response - API响应
 * @returns {Array} - 数据数组
 */
function getDataFromResponse(response) {
  // 尝试多种可能的响应格式
  if (response?.data?.data && Array.isArray(response.data.data)) {
    return response.data.data;
  }
  if (response?.data && Array.isArray(response.data)) {
    return response.data;
  }
  if (Array.isArray(response)) {
    return response;
  }
  
  console.warn('无法从响应中获取数据数组，返回空数组');
  return [];
}

/**
 * 取消分页获取操作
 * @param {AbortController} controller - 取消控制器
 */
export function cancelPagination(controller) {
  if (controller) {
    controller.abort();
    console.log('分页获取操作已取消');
  }
}

/**
 * 创建取消控制器
 * @returns {AbortController} - 取消控制器
 */
export function createCancellationController() {
  return new AbortController();
}

/**
 * 预加载数据（后台静默加载）
 * @param {Object} options - 配置选项
 * @returns {Promise<Array>} - 预加载的数据
 */
export async function preloadData(options = {}) {
  const config = { 
    ...DEFAULT_PAGINATION_CONFIG, 
    ...options,
    enableProgress: false // 预加载不显示进度
  };
  
  return fetchAllPages(config);
}

/**
 * 获取分页统计信息
 * @param {number} totalCount - 总数据量
 * @param {number} pageSize - 每页大小
 * @returns {Object} - 分页统计信息
 */
export function getPaginationInfo(totalCount, pageSize) {
  const totalPages = Math.ceil(totalCount / pageSize);
  return {
    totalCount,
    pageSize,
    totalPages,
    estimatedTime: Math.ceil(totalCount / 1000 * 2), // 估算时间（秒）
    recommendedBatchSize: Math.min(Math.ceil(1000 / pageSize), 5)
  };
}

export default {
  fetchAllPages,
  cancelPagination,
  createCancellationController,
  preloadData,
  getPaginationInfo,
  DEFAULT_PAGINATION_CONFIG
};