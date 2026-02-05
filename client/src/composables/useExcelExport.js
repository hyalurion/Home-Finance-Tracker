/*
 * @file useExcelExport.js
 * @package 家庭记账本
 * @module 组合式函数
 * @description Excel导出功能组合式函数，负责消费数据的Excel格式转换和导出
 * @author 开发者
 * @version 1.0
 */

import * as XLSX from 'xlsx';
import { i18n } from '@/main';

/**
 * Excel导出组合式函数
 * @returns {Object} 包含导出方法的对象
 */
export function useExcelExport () {
  /**
   * 将消费数据转换为Excel工作表
   * @param {Array} expenses - 消费记录数组
   * @returns {Object} Excel工作表对象
   */
  const createWorksheet = (expenses) => {
    if (!expenses || !Array.isArray(expenses)) return { header: [], data: [] };
    const header = [i18n.global.t('expense.columns.date'), i18n.global.t('expense.columns.type'), i18n.global.t('expense.columns.amount'), i18n.global.t('expense.columns.remark')];
    const data = expenses.map(expense => [
      expense.date || expense.time,
      expense.type,
      `${i18n.global.t('common.currencySymbol')}${expense.amount}`,
      expense.remark || '-'
    ]);
    return XLSX.utils.aoa_to_sheet([header, ...data]);
  };

  /**
   * 执行Excel导出操作
   * @param {Array} expenses - 要导出的消费记录数组
   * @param {string} [filename='消费记录导出'] - 导出文件名
   */
  const exportToExcel = (expenses = [], filename = i18n.global.t('expense.exportFilename')) => {
    const workbook = XLSX.utils.book_new();
    const worksheet = createWorksheet(expenses);
    XLSX.utils.book_append_sheet(workbook, worksheet, i18n.global.t('app.recordList'));
    XLSX.writeFile(workbook, `${filename}.xlsx`);
  };

  return {
    exportToExcel
  };
}
