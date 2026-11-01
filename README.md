# Vocabulary Learning — Java Swing Reference

这是从 `Vocabulary-learning-software` 的 Python 行为规范独立重写的 Java 11 + Swing 参考实现。它不属于课程团队提交，不修改 `team-project`，也不复用任何课程仓库提交历史。

## 已覆盖的参考行为

- 20 词学习轮次、四选一和“不会”答案
- 中途退出后恢复相同轮次和题目快照
- 错词/不会词只进入下一轮重测
- 统一结算、掌握词去重、净新增目标进度和幂等结算
- 本地 SQLite 保存轮次、作答、掌握词、错词、历史和设置
- Swing 首页、学习、错词、历史、词典查询和设置页面
- Free Dictionary API 查询；网络失败不影响离线学习
- 结果页庆祝动画、跳过动画和减少动画设置

## UI 参考配色

当前 Swing UI 使用白底黑字，并以两个参考色板统一按钮、卡片、状态提示和图标：浅粉 `#FFF4FE` 与浅蓝 `#E4F5FF`。按钮和图标由 Java2D 绘制圆角与线性图形，不依赖额外图片资源；这只改变表现层，不改变学习、复习、词典查询或本地保存行为。

测评、完整 15 级词库、成就展示、严格导入导出、语音和全部 Python 内容规则会在此参考版的后续迭代中继续补齐。课程团队版本应重新设计 User Stories、接口和 PR，不应直接复制本仓库历史。

## 运行

```powershell
mvn test
mvn exec:java
```

默认数据写入用户目录下的 `.vocabulary-learning-reference`。也可以设置 `VOCAB_DATA_DIR` 指向演示目录。不要提交个人数据库或 API 凭据。

## 分层

`domain` 只包含业务对象和规则；`application` 定义用例及端口；`infrastructure` 提供 SQLite、词库和 HTTP API 实现；`ui` 只负责 Swing。外部 API 用 `DictionaryClient` 接口隔离，测试使用 fake client。

## 与教授要求的对应关系

这个参考项目演示 Java Swing、外部 API、数据持久化、Use Case 分层、单元测试和动画模型。课程团队仍需在自己的仓库中按 Team Contract、User Story、feature branch、Pull Request、review、README/MVP 和公平 git 贡献要求完成工作。
