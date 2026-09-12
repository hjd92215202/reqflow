export default {
    // 使用函数式写法，忽略传入的文件路径参数，直接执行全局 spotless 格式化
    '*.java': () => 'mvn spotless:apply'
};
