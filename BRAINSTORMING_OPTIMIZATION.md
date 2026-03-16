# 头脑风暴功能优化实施报告

## 优化目标

简化头脑风暴完成后的操作流程：
- **当前流程**：头脑风暴 → 手动选择 Agent → 确认工作流（3 步）
- **优化后流程**：
  - **完成头脑风暴**：自动推荐 Agent 组合 → 确认工作流（2 步自动推荐 + 可选调整）
  - **跳过头脑风暴**：手动选择 Agent → 确认工作流（2 步手动流程）

## 实施变更

### 1. 新增状态标记

```javascript
const isBrainstormSkipped = ref(false) // 区分用户是否跳过了头脑风暴
const recommendedAgentIds = ref([]) // 自动推荐的 Agent 组合
const showManualAgentSelection = ref(false) // 是否显示手动选择界面
```

### 2. 自动推荐逻辑

根据需求类型自动推荐 Agent 组合：

| 需求类型 | 推荐角色 |
|---------|---------|
| feature (新功能) | 架构师 + 后端开发 + 前端开发 + 测试工程师 |
| bug (Bug 修复) | 后端开发 + 测试工程师 |
| performance (性能优化) | 架构师 + DBA + 后端开发 |
| requirement (需求分析) | 产品经理 + 架构师 + Tech Lead |
| default (默认) | 架构师 + 后端开发 + 前端开发 |

### 3. Step 1 条件渲染

- **未跳过（完成头脑风暴）**：
  - 显示"推荐 Agent"卡片
  - Agent 已自动选中
  - 提供"调整选择"按钮

- **已跳过**：
  - 显示原有的 Agent 选择网格
  - 手动选择 Agent

### 4. 确认页调整

- Step 2 返回按钮改为 `goBackToStep1()`，允许用户重新选择 Agent
- 点击后显示手动选择界面

## 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `TaskGenerateDialog.vue` | 添加状态标记、自动推荐逻辑、条件渲染 |
| `zh.js` | 添加头脑风暴相关翻译 |
| `en.js` | 添加头脑风暴相关翻译 |

## 新增翻译键

### 中文 (zh.js)
```javascript
requirement: {
  // ...existing keys
  recommendedAgentsHint: '已根据头脑风暴结论为您推荐 Agent',
  recommendedForYou: '为您推荐',
  recommended: '推荐',
  adjustSelection: '调整选择',
  adjustAgentsHint: '您可以手动调整 Agent 选择'
}
```

### 英文 (en.js)
```javascript
requirement: {
  // ...existing keys
  recommendedAgentsHint: 'Agents recommended based on brainstorming conclusion',
  recommendedForYou: 'Recommended for you',
  recommended: 'Recommended',
  adjustSelection: 'Adjust selection',
  adjustAgentsHint: 'You can manually adjust agent selection'
}
```

## 用户流程

### 流程 1: 完成头脑风暴（推荐路径）

```
Step 0: 头脑风暴
  ↓ (点击"开始讨论")
角色依次发言 → 显示结论
  ↓ (点击"下一步")
Step 1: 推荐 Agent (自动选中)
  - 显示推荐卡片，Agent 已勾选
  - 可点击"调整选择"切换到手动模式
  ↓ (点击"下一步")
Step 2: 确认工作流
  - 显示工作流预览
  - 可点击"返回"修改 Agent 选择
  ↓ (点击"确认生成")
生成任务
```

### 流程 2: 跳过头脑风暴

```
Step 0: 头脑风暴
  ↓ (点击"跳过")
Step 1: 手动选择 Agent
  - 显示 Agent 选择网格
  - 手动勾选 Agent
  ↓ (点击"下一步")
Step 2: 确认工作流
  ↓ (点击"确认生成")
生成任务
```

## 验证步骤

### 场景 1 - 完成头脑风暴

1. 打开原始需求列表
2. 选择一个未转换的需求
3. 点击"生成任务"
4. 点击"开始讨论"按钮
5. **验证**：
   - 角色依次发言（打字机效果）
   - 结论卡片显示
6. 点击"下一步"
7. **验证**：
   - 显示"为您推荐"横幅
   - Agent 已自动勾选
   - 显示"推荐"标签
8. 点击"调整选择"
9. **验证**：
   - 切换到手动选择网格
   - 已选中的 Agent 保持选中
10. 点击"下一步"
11. **验证**：
    - 显示工作流预览
    - 点击"返回"可修改 Agent

### 场景 2 - 跳过头脑风暴

1. 打开原始需求列表
2. 选择一个未转换的需求
3. 点击"生成任务"
4. 点击"跳过"按钮
5. **验证**：
   - 直接进入手动选择 Agent 页面
   - 没有推荐卡片
6. 手动选择 Agent
7. 点击"下一步"
8. **验证**：
   - 显示工作流预览

## 代码质量检查

- [x] 构建成功 (`npm run build` 无错误)
- [x] 新增状态响应式
- [x] 条件渲染逻辑正确
- [x] 翻译完整（中英文）
- [x] 用户体验优化（可调整、可返回）

## 后续优化建议

1. **个性化推荐**：根据历史数据学习用户的 Agent 偏好
2. **推荐理由**：显示更详细的推荐理由（如"因为您的需求包含 API 开发，推荐后端开发"）
3. **多 Agent 组合**：支持保存常用的 Agent 组合模板
4. **头脑风暴结论传递**：将头脑风暴结论传递给 AI Agent，作为任务执行的参考

## 相关文件

- 主组件：`frontend/src/components/requirement/TaskGenerateDialog.vue`
- Mock 数据：`frontend/src/mock/brainstormingData.js`
- 翻译文件：
  - `frontend/src/locales/zh.js`
  - `frontend/src/locales/en.js`
