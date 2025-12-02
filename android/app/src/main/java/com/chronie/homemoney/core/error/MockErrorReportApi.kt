package com.chronie.homemoney.core.error

import retrofit2.Response

/**
 * ErrorReportApi的模拟实现类
 * 用于在开发和测试环境中模拟错误上报功能
 */
class MockErrorReportApi : ErrorReportApi {
    
    /**
     * 模拟上报错误信息到服务器
     * 不实际发送网络请求，仅返回成功响应
     */
    override suspend fun reportError(request: ErrorReportRequest): Response<Unit> {
        // 记录错误信息到日志
        println("Mock error report: ${request.errorType} - ${request.message}")
        
        // 返回成功响应
        return Response.success(Unit)
    }
}
