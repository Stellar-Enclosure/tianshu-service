/**
 * Tianshu 本地配置启动，读取受管 ConfigMap 基线并核对部署目标及两类摘要。
 * 启动配置保持不可变，HOT 初值写入应用后续使用的同一个动态属性源。
 *
 * <p>Loads a managed ConfigMap baseline and verifies its target and digests locally.
 * Startup properties are immutable, and the initial HOT values use the same dynamic
 * property source as subsequent complete snapshots.</p>
 */
@NonNullApi
package top.egon.cola.component.tianshu.configdata;

import org.springframework.lang.NonNullApi;
