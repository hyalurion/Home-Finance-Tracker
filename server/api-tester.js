/**
 * 自动化API测试工具
 * 用于对比Go版本和JS版本API的输出一致性
 * 生成详细的JSON报告
 */

const axios = require('axios');
const fs = require('fs');
const path = require('path');

class APITester {
    constructor() {
        // 配置两个服务器的基本URL
        this.goServer = 'http://localhost:8080';
        this.jsServer = 'http://localhost:3010';
        this.timeout = 30000; // 30秒超时
        
        // 测试结果存储
        this.testResults = {
            timestamp: new Date().toISOString(),
            summary: {
                totalTests: 0,
                passedTests: 0,
                failedTests: 0,
                skippedTests: 0
            },
            tests: [],
            comparisons: [],
            differences: []
        };

        // 定义要测试的API端点
        this.apiEndpoints = [
            // 基础健康检查端点
            {
                name: '系统健康检查',
                method: 'GET',
                paths: ['/api/health']
            },
            {
                name: '轻量级健康检查',
                method: 'GET',
                paths: ['/api/health/lite']
            },
            
            // 消费记录端点
            {
                name: '获取消费记录',
                method: 'GET',
                paths: ['/api/expenses'],
                params: { page: 1, limit: 10 }
            },
            {
                name: '添加消费记录',
                method: 'POST',
                paths: ['/api/expenses'],
                data: {
                    amount: 50.00,
                    category: 'food',
                    description: 'Test expense',
                    date: new Date().toISOString().split('T')[0],
                    tags: ['test']
                }
            },
            {
                name: '删除消费记录',
                method: 'DELETE',
                paths: ['/api/expenses/1']
            },
            {
                name: '消费统计',
                method: 'GET',
                paths: ['/api/expenses/statistics']
            },
            
    
            // JSON文件操作端点
            {
                name: '获取JSON文件列表',
                method: 'GET',
                paths: ['/api/json-files']
            },
            {
                name: '读取指定JSON文件',
                method: 'GET',
                paths: ['/api/json-files/test.json']
            },
            {
                name: '写入数据到JSON文件',
                method: 'POST',
                paths: ['/api/json-files/test-data.json'],
                data: {
                    key: 'test_value',
                    timestamp: new Date().toISOString(),
                    data: { test: true, value: 123 }
                }
            },
            {
                name: '删除指定JSON文件',
                method: 'DELETE',
                paths: ['/api/json-files/test-data.json']
            },
            
            // 支付端点
            {
                name: '订阅支付',
                method: 'POST',
                paths: ['/api/payments/subscribe'],
                data: {
                    plan_id: 'basic',
                    user_id: 'test-user',
                    amount: 9.99,
                    currency: 'USD'
                }
            },
            
            // 导入导出端点
            {
                name: '导出Excel文件',
                method: 'GET',
                paths: ['/api/export/excel']
            },
            {
                name: '导入Excel文件',
                method: 'POST',
                paths: ['/api/import/excel'],
                data: {
                    filename: 'test-import.xlsx',
                    data: [
                        { amount: 100, category: 'food', description: 'Lunch', date: '2025-11-01' },
                        { amount: 50, category: 'transport', description: 'Bus fare', date: '2025-11-02' }
                    ]
                }
            },
            
            // 会员管理端点
            {
                name: '获取或创建会员',
                method: 'POST',
                paths: ['/api/members'],
                data: {
                    username: 'testuser',
                    email: 'test@example.com',
                    display_name: 'Test User'
                }
            },
            {
                name: '获取会员信息',
                method: 'GET',
                paths: ['/api/members/testuser']
            },
            {
                name: '更新会员状态',
                method: 'PUT',
                paths: ['/api/members/1/status'],
                data: {
                    status: 'active',
                    notes: 'Updated status via API test'
                }
            },
            {
                name: '订阅计划',
                method: 'GET',
                paths: ['/api/subscription-plans']
            },
            {
                name: '创建订阅',
                method: 'POST',
                paths: ['/api/subscriptions'],
                data: {
                    username: 'testuser',
                    plan_id: 'basic',
                    duration: 'monthly'
                }
            },
            {
                name: '获取会员订阅',
                method: 'GET',
                paths: ['/api/members/testuser/subscriptions']
            },
            {
                name: '获取当前订阅',
                method: 'GET',
                paths: ['/api/members/testuser/current-subscription']
            },
            {
                name: '取消订阅',
                method: 'DELETE',
                paths: ['/api/members/testuser/subscriptions']
            },
            
            // 日志管理端点
            {
                name: '接收操作日志',
                method: 'POST',
                paths: ['/api/logs'],
                data: {
                    user_id: 'test-user',
                    action: 'test_action',
                    details: 'This is a test log entry',
                    timestamp: new Date().toISOString()
                }
            },
            {
                name: '获取日志列表',
                method: 'GET',
                paths: ['/api/logs'],
                params: { page: 1, limit: 10 }
            },
            {
                name: '日志统计',
                method: 'GET',
                paths: ['/api/logs/stats'],
                params: { 
                    start_date: new Date(Date.now() - 7*24*60*60*1000).toISOString().split('T')[0],
                    end_date: new Date().toISOString().split('T')[0]
                }
            },
            {
                name: '清理过期日志',
                method: 'DELETE',
                paths: ['/api/logs/clean'],
                params: { days: 30 }
            },

        ];
    }

    /**
     * 执行单个API测试
     */
    async testEndpoint(name, method, url, serverUrl, params = {}, data = null) {
        const startTime = Date.now();
        const result = {
            name,
            method,
            url,
            serverUrl,
            status: 'skipped',
            statusCode: null,
            responseTime: null,
            response: null,
            error: null
        };

        try {
            const config = {
                method,
                url: `${serverUrl}${url}`,
                params,
                timeout: this.timeout,
                validateStatus: () => true // 接受所有状态码
            };

            // 为POST/PUT/DELETE请求添加数据载荷
            if (data && ['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
                config.data = data;
                config.headers = {
                    'Content-Type': 'application/json'
                };
            }

            const response = await axios(config);

            const endTime = Date.now();
            result.responseTime = endTime - startTime;
            result.statusCode = response.status;
            result.status = response.status < 400 ? 'passed' : 'failed';
            result.response = response.data;

            if (response.status >= 400) {
                result.error = `HTTP ${response.status}: ${response.statusText}`;
            }

        } catch (error) {
            const endTime = Date.now();
            result.responseTime = endTime - startTime;
            result.status = 'error';
            result.error = error.message;
        }

        return result;
    }

    /**
     * 比较两个API响应
     */
    compareResponses(goResponse, jsResponse) {
        const comparison = {
            endpoint: goResponse.url || jsResponse.url,
            goServer: {
                status: goResponse.status,
                statusCode: goResponse.statusCode,
                responseTime: goResponse.responseTime,
                hasData: !!goResponse.response
            },
            jsServer: {
                status: jsResponse.status,
                statusCode: jsResponse.statusCode,
                responseTime: jsResponse.responseTime,
                hasData: !!jsResponse.response
            },
            differences: []
        };

        // 检查状态码差异
        if (goResponse.statusCode !== jsResponse.statusCode) {
            comparison.differences.push({
                type: 'status_code',
                go: goResponse.statusCode,
                js: jsResponse.statusCode,
                severity: 'high'
            });
        }

        // 检查响应时间差异
        const timeDiff = Math.abs(goResponse.responseTime - jsResponse.responseTime);
        if (timeDiff > 1000) { // 超过1秒差异认为有问题
            comparison.differences.push({
                type: 'response_time',
                go: goResponse.responseTime,
                js: jsResponse.responseTime,
                difference: timeDiff,
                severity: 'medium'
            });
        }

        // 检查数据内容差异
        if (goResponse.response && jsResponse.response) {
            const dataDiff = this.deepCompare(
                goResponse.response, 
                jsResponse.response, 
                goResponse.url
            );
            if (dataDiff.length > 0) {
                comparison.differences.push(...dataDiff);
            }
        }

        return comparison;
    }

    /**
     * 深度比较两个对象
     */
    deepCompare(obj1, obj2, context = '') {
        const differences = [];
        
        // 处理基本类型
        if (obj1 === obj2) return differences;
        
        // 处理null/undefined差异
        if (obj1 == null || obj2 == null) {
            if (obj1 !== obj2) {
                differences.push({
                    type: 'null_comparison',
                    path: context,
                    go: obj1,
                    js: obj2,
                    severity: 'high'
                });
            }
            return differences;
        }

        // 处理对象类型
        if (typeof obj1 === 'object' && typeof obj2 === 'object') {
            // 检查字段数量
            const keys1 = Object.keys(obj1);
            const keys2 = Object.keys(obj2);
            const missingInJS = keys1.filter(k => !keys2.includes(k));
            const missingInGO = keys2.filter(k => !keys1.includes(k));

            if (missingInJS.length > 0) {
                differences.push({
                    type: 'missing_fields',
                    path: context,
                    missingInJS,
                    severity: 'high'
                });
            }

            if (missingInGO.length > 0) {
                differences.push({
                    type: 'extra_fields',
                    path: context,
                    extraInJS: missingInGO,
                    severity: 'low'
                });
            }

            // 递归比较相同字段
            for (const key of keys1) {
                if (keys2.includes(key)) {
                    const nestedDiff = this.deepCompare(obj1[key], obj2[key], `${context}.${key}`);
                    differences.push(...nestedDiff);
                }
            }

            return differences;
        }

        // 处理基本类型差异
        differences.push({
            type: 'value_mismatch',
            path: context,
            go: obj1,
            js: obj2,
            goType: typeof obj1,
            jsType: typeof obj2,
            severity: 'medium'
        });

        return differences;
    }

    /**
     * 运行所有测试
     */
    async runAllTests() {
        console.log('🚀 开始完整API测试...\n');

        for (const endpoint of this.apiEndpoints) {
            console.log(`📋 测试: ${endpoint.name}`);
            
            for (const apiPath of endpoint.paths) {
                this.testResults.summary.totalTests += 2;

                // 并行测试两个服务器
                const [goResult, jsResult] = await Promise.all([
                    this.testEndpoint(endpoint.name, endpoint.method, apiPath, this.goServer, endpoint.params || {}, endpoint.data || null),
                    this.testEndpoint(endpoint.name, endpoint.method, apiPath, this.jsServer, endpoint.params || {}, endpoint.data || null)
                ]);

                // 记录单个测试结果
                this.testResults.tests.push(
                    { ...goResult, server: 'Go' },
                    { ...jsResult, server: 'JS' }
                );

                // 记录对比结果
                const comparison = this.compareResponses(goResult, jsResult);
                this.testResults.comparisons.push(comparison);

                // 记录差异
                if (comparison.differences.length > 0) {
                    this.testResults.differences.push(...comparison.differences);
                    this.testResults.summary.failedTests += 2;
                    console.log(`❌ ${apiPath} - 发现差异`);
                } else {
                    this.testResults.summary.passedTests += 2;
                    console.log(`✅ ${apiPath} - 输出一致`);
                }
            }
        }

        console.log('\n📊 完整API测试完成！');
        return this.testResults;
    }

    /**
     * 生成JSON报告
     */
    generateReport(testResults) {
        const reportPath = path.join(__dirname, `api-test-report-${Date.now()}.json`);
        
        // 计算统计信息
        const stats = {
            totalTests: testResults.summary.totalTests,
            passed: testResults.summary.passedTests,
            failed: testResults.summary.failedTests,
            passRate: testResults.summary.totalTests > 0 
                ? ((testResults.summary.passedTests / testResults.summary.totalTests) * 100).toFixed(2) + '%'
                : '0%'
        };

        // 按严重程度分类差异
        const differencesBySeverity = {
            high: testResults.differences.filter(d => d.severity === 'high'),
            medium: testResults.differences.filter(d => d.severity === 'medium'),
            low: testResults.differences.filter(d => d.severity === 'low')
        };

        // 按类型分类差异
        const differencesByType = {};
        testResults.differences.forEach(diff => {
            if (!differencesByType[diff.type]) {
                differencesByType[diff.type] = [];
            }
            differencesByType[diff.type].push(diff);
        });

        const finalReport = {
            metadata: {
                timestamp: testResults.timestamp,
                reportGenerated: new Date().toISOString(),
                goServer: this.goServer,
                jsServer: this.jsServer
            },
            statistics: stats,
            summary: {
                overallStatus: stats.failed === 0 ? 'PASSED' : 'FAILED',
                criticalIssues: differencesBySeverity.high.length,
                warnings: differencesBySeverity.medium.length,
                suggestions: differencesBySeverity.low.length
            },
            detailedResults: testResults.tests,
            comparisons: testResults.comparisons,
            differences: {
                bySeverity: differencesBySeverity,
                byType: differencesByType,
                totalCount: testResults.differences.length
            },
            recommendations: this.generateRecommendations(differencesBySeverity)
        };

        // 保存报告
        fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), 'utf8');
        console.log(`📄 测试报告已保存: ${reportPath}`);
        
        return { reportPath, report: finalReport };
    }

    /**
     * 生成建议
     */
    generateRecommendations(differencesBySeverity) {
        const recommendations = [];

        if (differencesBySeverity.high.length > 0) {
            recommendations.push({
                priority: 'HIGH',
                category: 'Critical Issues',
                message: '发现严重差异，可能影响功能正常运行',
                actions: [
                    '检查Go和JS版本的数据模型定义',
                    '确认API响应格式的一致性',
                    '验证数据序列化和反序列化逻辑'
                ]
            });
        }

        if (differencesBySeverity.medium.length > 0) {
            recommendations.push({
                priority: 'MEDIUM',
                category: 'Performance',
                message: '发现性能相关差异',
                actions: [
                    '优化响应较慢的API端点',
                    '检查数据库查询性能',
                    '考虑添加缓存机制'
                ]
            });
        }

        if (differencesBySeverity.low.length > 0) {
            recommendations.push({
                priority: 'LOW',
                category: 'Enhancement',
                message: '发现可优化的差异',
                actions: [
                    '统一返回字段格式',
                    '优化字段命名',
                    '考虑添加字段文档'
                ]
            });
        }

        return recommendations;
    }

    /**
     * 打印测试摘要
     */
    printSummary(finalReport) {
        console.log('\n' + '='.repeat(60));
        console.log('📋 API测试摘要');
        console.log('='.repeat(60));
        
        console.log(`⏰ 测试时间: ${finalReport.metadata.timestamp}`);
        console.log(`🌐 Go服务器: ${finalReport.metadata.goServer}`);
        console.log(`🌐 JS服务器: ${finalReport.metadata.jsServer}`);
        console.log('');
        
        console.log('📊 统计信息:');
        console.log(`   总测试数: ${finalReport.statistics.totalTests}`);
        console.log(`   通过: ${finalReport.statistics.passed}`);
        console.log(`   失败: ${finalReport.statistics.failed}`);
        console.log(`   通过率: ${finalReport.statistics.passRate}`);
        console.log('');
        
        console.log('⚠️  问题汇总:');
        console.log(`   严重问题: ${finalReport.summary.criticalIssues}`);
        console.log(`   警告: ${finalReport.summary.warnings}`);
        console.log(`   建议: ${finalReport.summary.suggestions}`);
        console.log('');
        
        console.log('📈 整体状态:', finalReport.summary.overallStatus === 'PASSED' ? '✅ 通过' : '❌ 失败');
        console.log('='.repeat(60));
    }
}

// 主执行函数
async function main() {
    console.log('🔧 初始化API测试工具...');
    
    const tester = new APITester();
    
    try {
        // 运行测试
        await tester.runAllTests();
        
        // 生成报告
        const { report, reportPath } = tester.generateReport(tester.testResults);
        
        // 打印摘要
        tester.printSummary(report);
        
        console.log(`\n📄 详细报告: ${reportPath}`);
        
    } catch (error) {
        console.error('❌ 测试过程中发生错误:', error);
        process.exit(1);
    }
}

// 如果直接运行此文件
if (require.main === module) {
    main();
}

module.exports = APITester;