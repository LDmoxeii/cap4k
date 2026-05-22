# 内容发布示例：Saga

相关规则：

- [内容发布与处理示例项目总览](reference-project-overview.md)
- [Default Happy Path](../default-happy-path.md)
- [Saga](../advanced/saga.md)
- [媒体处理 callback 主路径示例](media-processing-callback.md)
- [媒体处理 polling 备用路径示例](media-processing-polling.md)

## Scenario

本页不是换一个业务域重新讲 Saga，而是在共享的 content-publication / media-processing 教学项目里做一次受控扩展。默认参考项目仍然是：内容送审、审核通过、启动媒体处理、外部系统通过 callback 回传结果，polling 只是备用补位路径。

这里加入的不是“纯等待未来事实”的 workflow，而是一个补偿导向的发布切片：内容已经通过审核，媒体处理也已经完成，但真正对外发布前，平台还要先做几件可能需要回滚的动作，例如冻结创作者 payout、创建 entitlement plan、占用付费发布资格。只要后续发布裁决被拒绝、风控拦截或 operator 明确撤销，这些已完成步骤就必须有审计化的补偿路径。

这个场景的重点不是证明当前默认链路已经需要 Saga。恰好相反，当前默认发布 / 媒体处理链路本身仍然不需要 Saga；Saga 只在这个受控扩展里，用来承接“媒体已完成之后，仍有额外 persisted coordination、恢复和补偿要求”的后半段协调。

## Why default path is not enough

默认路径擅长的是：事实一旦到齐，就把内部命令链清楚推进下去。对当前参考项目来说，这已经足够，所以默认路径仍然不需要 Saga。

但在这个受控扩展里，问题不再只是“媒体处理结果有没有回来”，而是：

- callback 已经把媒体完成事实带回来了；
- 发布前还要先完成几个可能需要回滚的外部动作；
- 其中一部分补偿请求依赖前向步骤的真实结果，例如 entitlement planId、holdId；
- 一旦发布裁决失败或人工撤销，系统要明确停止 forward path，并把已完成的可补偿步骤按逆序回滚；
- 如果某个已完成步骤本身不可补偿，其他可补偿步骤仍然要回滚，但最终态必须进入人工修复。

这时如果还硬塞回普通短命令链，文档就很难回答几个关键问题：哪些已完成步骤需要 runtime 持久化补偿请求、谁来发出明确补偿信号、补偿失败后如何继续恢复、哪些失败必须转人工修复。默认链路不是错，只是它不负责表达这种 persisted compensation / recovery 边界。

## Recommended shape

推荐形态仍然以默认参考项目为主干，再在“媒体处理已完成之后”增加一个很窄的 Saga 协调层：

- `Content` 继续只负责内容生命周期事实。
- `MediaProcessingTask` 继续只负责媒体处理生命周期事实。
- callback 仍然是媒体结果进入系统的主路径；polling 仍然只是 fallback，用来补同一条内部命令语义，不提升成主真相路径。
- Saga 只负责把“哪些前向步骤需要补偿、何时显式进入补偿、补偿失败后如何恢复或转人工”持久化下来。

一个更贴近当前 runtime 的示例代码，可以写成：

```kotlin
class PublishPaidContentSaga :
    SagaHandler<PublishPaidContentSaga.Request, PublishPaidContentSaga.Response> {

    override fun exec(request: Request): Response {
        execProcess(
            subCode = "assert-content-ready",
            request = AssertPaidPublicationReadyCmd.Request(request.contentId)
        )

        val payoutHold = execCompensableProcess(
            processCode = "create-payout-hold",
            request = CreateCreatorPayoutHoldCmd.Request(request.contentId, request.orderId),
            compensationCode = "release-payout-hold",
            compensationRequest = { hold ->
                ReleaseCreatorPayoutHoldCmd.Request(hold.holdId)
            }
        )

        val entitlementPlan = execCompensableProcess(
            processCode = "create-entitlement-plan",
            request = CreateAccessEntitlementPlanCmd.Request(request.contentId, request.orderId),
            compensationCode = "cancel-entitlement-plan",
            compensationRequest = { plan ->
                CancelEntitlementPlanCmd.Request(plan.planId)
            }
        )

        val publish = execProcess(
            subCode = "publish-content",
            request = PublishPaidContentCmd.Request(
                contentId = request.contentId,
                payoutHoldId = payoutHold.holdId,
                entitlementPlanId = entitlementPlan.planId
            )
        )

        if (!publish.accepted) {
            requestCompensation(
                code = "PUBLISH_REJECTED",
                reason = publish.reason
            )
        }

        return Response(
            publicationId = publish.publicationId
        )
    }

    data class Request(
        val contentId: String,
        val orderId: String
    ) : SagaParam<Response>

    data class Response(
        val publicationId: String
    )
}
```

这个例子的重点是：

- `execCompensableProcess(...)` 只用于“前向成功后，需要保留 reverse compensation 能力”的步骤。
- 补偿请求不是在失败时临时拼出来的；runtime 会在前向成功时就把最终 compensation request 持久化。
- `requestCompensation(...)` 是显式控制信号。它的意思不是“再试一下”，而是“forward 目标不再继续，立刻转补偿”。
- callback 仍然只负责把媒体处理结果带回主链；Saga 没有把 callback 变成 step-level resume engine。

## Manual repair boundary

补偿型示例必须把人工修复边界说死：

- 如果某个已完成步骤是可补偿的，Saga 仍然应该尽量把它回滚。
- 如果某个已完成步骤本身不可补偿，Saga 不应该假装“一切已经恢复正常”。
- 当前 runtime 已实现的自动 `MANUAL_REPAIR_REQUIRED` 路径，是补偿扫描到某个已执行步骤需要回滚，但该步骤根本没有持久化 compensation request。
- 这类场景下，其他可补偿步骤仍然可以继续回滚；`MANUAL_REPAIR_REQUIRED` 在文档里首先是设计边界和 operator 处理面，不应被表述成“补偿重试耗尽后自动进入”的既有行为。

例如：

- payout hold 已成功释放；
- entitlement plan 已成功取消；
- 但某个已经对外生效、没有自动 reverse API 的发行登记步骤无法补偿；
- 这时文档上应把它视为人工修复边界；如果该已执行步骤缺少 compensation request，当前 runtime 会把 Saga 推到 `MANUAL_REPAIR_REQUIRED`，等待运营或财务接手。

## Non-example / misuse

- 媒体平台 callback 一回来，马上转一条内部命令就能完成后续推进，却为了形式感把这段短 callback-to-command 链升级成 Saga。
- 把 Saga 写成一个 application 超级 handler，审核、媒体处理、发布判断、人工确认、补偿全塞进一个大类里。
- 明明 callback 已经是主路径，却让 polling 变成真正的发布真相源，再把 callback 降成可有可无的旁路。
- 还没出现跨时间等待，只是想把“以后可能会复杂”的担心预埋进去，就提前把默认链路整体包装成 Saga。
- 用一个包装器把命令链重新套壳，却没有明确哪些步骤需要 `execCompensableProcess(...)`、何时 `requestCompensation(...)`、何时转 `MANUAL_REPAIR_REQUIRED`，结果只是把原本清楚的边界讲乱。

## Usage boundary

这页的使用边界必须讲死：

- 当前默认 publication / media-processing 链路仍然不需要 Saga。
- 这页只是同一参考项目里的受控扩展示例，不是建议把默认主链改写成 Saga。
- 只有在媒体处理已经完成之后，仍然存在 persisted compensation、恢复、人工接管这些要求时，Saga 才开始合理。
- 如果条件都已经是已知事实，或者 callback 回来后只差一两条普通内部命令，那么继续用默认命令链即可。
- waiting-style Saga、外部 callback-step resume 不在当前公开能力切片里。
- callback 保持主路径，polling 保持 fallback；无论是否引入 Saga，都不应该把 polling 升格成主要真相来源。

## Audit cues

- 文档是否明确写出“当前默认路径 / 默认链路仍然不需要 Saga”，而不是暗示默认设计不完整。
- 示例是否被表述成同一 reference project 的受控扩展，而不是跳到新的业务域。
- 补偿步骤是否具体到 payout hold、entitlement plan 这类真实已完成动作，而不是泛泛写“失败了就回滚一下”。
- 是否给出 `execCompensableProcess(...)` + `requestCompensation(...)` 的清晰写法，而不是继续鼓励手写 `try/catch` 补偿。
- manual repair 边界是否明确写出：可补偿步骤仍然回滚，但不要把“补偿失败重试耗尽后自动 `MANUAL_REPAIR_REQUIRED`”写成当前已实现行为。
- callback 是否仍被描述为主路径，polling 是否仍只作为 fallback 收敛到同一内部命令语义。
- 页面是否避免把 Saga 包装器本身写成中心角色，而是始终把注意力放在等待、恢复、补偿边界上。
