package top.egon.cola.component.tianshu.api.client;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import top.egon.cola.component.tianshu.model.ai.DdcAiResourceQuery;
import top.egon.cola.component.tianshu.model.ai.DdcPromptResult;
import top.egon.cola.component.tianshu.model.ai.DdcAiClientScope;

public interface DdcPromptClient {

    DdcPromptResult get(@NotNull @Valid DdcAiResourceQuery query);

    /** 返回传输固定的范围；旧适配器在无法证明范围时拒绝。 Returns the transport scope, failing closed for legacy adapters. */
    default DdcAiClientScope scope() {
        throw new IllegalStateException("AI client scope is unavailable");
    }
}
