# 天枢列表模板客户端

`stellar-enclosure-tianshu-list-client` 为业务服务提供带用户和服务双凭据的模板委托调用。它先通过业务服务本地鉴神执行守卫验证对应页面或模板 API，再携带当前已验证 USER 令牌及受限 SERVICE 令牌访问天枢；返回后再次复核授权版本。客户端只支持启用 USER scope 策略的宿主，不接受浏览器指定用户或租户。

接入、元数据声明、SERVICE Grant 和运行时绑定要求见 [列表字段模板与 Excel 导出](../docs/list-templates-and-excel.md)。客户端不负责业务查询、数据范围或 Excel 字段脱敏；这些由调用方的业务查询和鉴神授权投影负责。
