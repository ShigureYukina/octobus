# Implement Plan — Gemini AI 提供者

## Execution Checklist

1. 阅读现有 OpenAI / Anthropic provider 与 transformer 实现
2. 确认 Gemini API 最小接入面（公共 API）
3. 实现 Gemini provider client
4. 实现 Gemini inbound / outbound transformer
5. 接入 provider 选择与 transformer 注册
6. 补充错误映射与 usage 解析
7. 增加非流式 / 流式测试并回归现有 provider

## Validation Commands

- `cd java && mvn test`
- 对 relay 测试类执行定向验证（如项目已有 provider/relay 测试）

## Review Gates

- Gate 1：首轮支持面是否控制在文本 chat 主路径
- Gate 2：流式协议转换是否和现有抽象一致
- Gate 3：错误映射与成本记录是否接入现有链路

## Rollback Points

- Gemini provider 可通过新增类与注册点隔离，回退影响面有限
- 如流式不稳定，可先保留非流式主路径，流式作为后续增量
