/**
 * Tianshu 跨边界领域数据的组织包，按客户端、配置、实例、租约、管理和注册语义分类。
 * 本包及子包只保存值对象、请求、响应和事件，不保存服务、客户端实现或可变运行态。
 *
 * <p>Organizational package for Tianshu domain data grouped by client, configuration, instance,
 * lease, management, and registry semantics. It contains values, requests, responses, and events,
 * never services, client implementations, or mutable runtime state.</p>
 */
@NonNullApi
@top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionReferences({
        "TIANSHU_READ", "TIANSHU_WRITE", "TIANSHU_AI_READ", "TIANSHU_AI_WRITE", "TIANSHU_AI_PUBLISH"
})
package top.egon.cola.component.tianshu.model;

import org.springframework.lang.NonNullApi;
