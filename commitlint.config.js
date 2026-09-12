export default {
    extends: ['@commitlint/config-conventional'],
    rules: {
        'type-enum': [
            2,
            'always',
            [
                'feat',     // 新功能
                'fix',      // 修复
                'docs',     // 文档变更
                'style',    // 代码格式
                'refactor', // 重构
                'perf',     // 性能优化
                'test',     // 增加测试
                'chore',    // 构建过程或辅助工具变动
                'revert',   // 回滚
                'build',    // 打包配置变更
                'ci'        // CI/CD 配置变更
            ]
        ],
        'type-case': [2, 'always', 'lower-case'],
        'type-empty': [2, 'never'],
        'scope-case': [0],
        'subject-empty': [2, 'never'],
        'subject-full-stop': [2, 'never', '.']
    }
};
