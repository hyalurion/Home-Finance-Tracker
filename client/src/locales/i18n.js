/*
 * @file i18n.js
 * @package 家庭记账本
 * @module 国际化配置
 * @description 多语言支持核心配置文件，初始化i18n实例并加载语言包
 * @author 开发者
 * @version 1.0
*/

import { createI18n } from 'vue-i18n';
import enUS from './en-US.json';
import zhCN from './zh-CN.json';
import zhTW from './zh-TW.json';

// 获取浏览器默认语言
const browserLanguage = navigator.language || navigator.userLanguage;
console.log('获取到的浏览器默认语言为:', browserLanguage);
// 定义支持的语言列表
const supportedLanguages = ['en-US', 'zh-CN', 'zh-TW'];
console.log('支持的语言列表为:', supportedLanguages);
// 检查浏览器语言是否在支持列表中
let defaultLocale = 'en-US'; // 默认语言
console.log('初始默认语言设置为:', defaultLocale);

// 优化语言检测逻辑
// 首先检查是否是完全匹配的支持语言
if (supportedLanguages.includes(browserLanguage)) {
  defaultLocale = browserLanguage;
  console.log('浏览器语言在支持列表中，默认语言更新为:', defaultLocale);
} 
// 然后检查是否是繁体中文相关
else if (browserLanguage.startsWith('zh-TW') || browserLanguage.includes('TW') || browserLanguage.includes('HK')) {
  defaultLocale = 'zh-TW';
  console.log('浏览器语言为繁体中文相关，默认语言更新为:', defaultLocale);
}
// 最后检查是否是其他中文
else if (browserLanguage.startsWith('zh')) {
  defaultLocale = 'zh-CN';
  console.log('浏览器语言为中文，默认语言更新为:', defaultLocale);
}
else {
  console.log('未找到匹配的语言，默认语言保持不变:', defaultLocale);
}
/**
 * 初始化i18n实例
 * @type {import('vue-i18n').I18n}
 */
const i18n = createI18n({
  legacy: false, // 使用组合式API模式
  locale: defaultLocale, // 根据浏览器语言设置默认语言
  fallbackLocale: 'en-US', // 回退语言
  messages: {
    'en-US': enUS,
    'zh-CN': zhCN,
    'zh-TW': zhTW
  }
});

// 导出语言切换方法
export const changeLanguage = (newLocale) => {
  if (supportedLanguages.includes(newLocale)) {
    i18n.global.locale.value = newLocale;
    console.log(`语言已切换为: ${newLocale}`);
  } else {
    console.error(`不支持的语言: ${newLocale}`);
  }
};

export default i18n;
