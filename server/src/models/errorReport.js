const { DataTypes } = require('sequelize')

module.exports = (sequelize) => {
  const ErrorReport = sequelize.define('ErrorReport', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    memberId: {
      type: DataTypes.UUID,
      allowNull: true,
      references: {
        model: 'Members',
        key: 'id'
      }
    },
    errorType: {
      type: DataTypes.STRING,
      allowNull: false,
      comment: '错误类型（如：Crash、NetworkError、UIError等）'
    },
    message: {
      type: DataTypes.TEXT,
      allowNull: false,
      comment: '错误消息'
    },
    stackTrace: {
      type: DataTypes.TEXT,
      allowNull: true,
      comment: '堆栈跟踪信息'
    },
    deviceInfo: {
      type: DataTypes.JSON,
      allowNull: true,
      comment: '设备信息，如操作系统版本、设备型号等'
    },
    appVersion: {
      type: DataTypes.STRING,
      allowNull: true,
      comment: '应用版本名称'
    },
    appBuild: {
      type: DataTypes.STRING,
      allowNull: true,
      comment: '应用构建版本号'
    },
    environment: {
      type: DataTypes.STRING,
      allowNull: true,
      comment: '环境（如：production、development）'
    },
    isProcessed: {
      type: DataTypes.BOOLEAN,
      defaultValue: false,
      comment: '错误报告是否已处理'
    },
    processedAt: {
      type: DataTypes.DATE,
      allowNull: true,
      comment: '处理时间'
    },
    createdAt: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW
    },
    updatedAt: {
      type: DataTypes.DATE,
      allowNull: false,
      defaultValue: DataTypes.NOW,
      onUpdate: DataTypes.NOW
    }
  }, {
    tableName: 'error_reports',
    indexes: [
      {
        name: 'idx_error_reports_member_id',
        fields: ['memberId']
      },
      {
        name: 'idx_error_reports_created_at',
        fields: ['createdAt']
      },
      {
        name: 'idx_error_reports_is_processed',
        fields: ['isProcessed']
      },
      {
        name: 'idx_error_reports_error_type',
        fields: ['errorType']
      }
    ]
  })

  return ErrorReport
}
