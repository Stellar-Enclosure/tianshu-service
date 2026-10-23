package top.egon.cola.component.tianshu.admin.controller.i18n;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.core.pojo.PageResultRecord;
import top.egon.cola.component.common.core.pojo.ResultRecord;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nCreateCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nDraftCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nPublishCommand;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nPageResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nPageSummary;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nBundleResult;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcI18nPageService;
import top.egon.cola.component.tianshu.admin.support.DdcAdminPageSupport;
import top.egon.cola.component.yuheng.openapi.annotation.EgonApiCatalog;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;
import java.net.URI;
import java.util.Arrays;

@RestController
@RequestMapping(path = "/api/v1/tianshu/i18n", produces = MediaType.APPLICATION_JSON_VALUE)
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "tianshuBearerAuth")
@Tag(name = "Tianshu Page Languages", description = "企业元数据中心页面多语种管理与语言包")
@EgonApiCatalog(businessDomainCode = "xingyuan", businessDomainName = "平台治理域",
        entityDomainCode = "tianshu-admin", entityDomainName = "Dynamic Config Center 管理实体域", interfaceGroupCode = "tianshu")
@RequiredArgsConstructor
@Validated
public class DdcI18nPageController {
    @Qualifier("ddcI18nPageService") private final DdcI18nPageService service;

    @GetMapping("/pages")
    @Operation(operationId = "pageI18nPages", summary = "浏览页面多语种资源")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<PageResultRecord<DdcI18nPageSummary>> page(
            @RequestParam String bizCode, @RequestParam String namespaceCode,
            @RequestParam String env, @RequestParam String appCode,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return noStore(DdcAdminPageSupport.result(service.page(new DdcI18nScope(bizCode, namespaceCode, env, appCode),
                new PageQuery(pageNo, pageSize))));
    }

    @PostMapping(path = "/pages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createI18nPage", summary = "创建页面多语种草稿")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<DdcI18nPageResult>> create(@RequestBody @Valid DdcI18nCreateCommand command) {
        var page = service.create(command);
        return ResponseEntity.created(URI.create("/api/v1/tianshu/i18n/pages/" + page.id()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(ResultRecord.success(page));
    }

    @GetMapping("/pages/{id}")
    @Operation(operationId = "getI18nPage", summary = "读取页面多语种草稿")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<DdcI18nPageResult>> get(@PathVariable Long id) {
        return noStore(ResultRecord.success(service.get(id)));
    }

    @PutMapping(path = "/pages/{id}/draft", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "replaceI18nDraft", summary = "完整保存页面多语种草稿")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<DdcI18nPageResult>> draft(@PathVariable Long id,
            @RequestBody @Valid DdcI18nDraftCommand command) {
        return noStore(ResultRecord.success(service.replaceDraft(id, command)));
    }

    @PostMapping(path = "/pages/{id}/publish", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "publishI18nPage", summary = "发布页面多语种快照")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<DdcI18nPageResult>> publish(@PathVariable Long id,
            @RequestBody @Valid DdcI18nPublishCommand command) {
        return noStore(ResultRecord.success(service.publish(id, command)));
    }

    @GetMapping("/bundle")
    @Operation(operationId = "getI18nBundle", summary = "读取已发布的页面语言包")
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.EXTERNAL)
    public ResponseEntity<ResultRecord<DdcI18nBundleResult>> bundle(
            @RequestParam String bizCode, @RequestParam String namespaceCode,
            @RequestParam String env, @RequestParam String appCode,
            @RequestParam String pageCode, @RequestParam String locale,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        // Authorization and enabled-scope checks always precede conditional-cache handling.
        var bundle = service.bundle(new DdcI18nScope(bizCode, namespaceCode, env, appCode), pageCode, locale);
        String etag = "W/\"" + bundle.version() + "\"";
        var response = ResponseEntity.status(matches(ifNoneMatch, etag) ? HttpStatus.NOT_MODIFIED : HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .header(HttpHeaders.VARY, "Authorization, Cookie, X-Egon-Tianshu-Tenant").eTag(etag);
        return matches(ifNoneMatch, etag) ? response.build() : response.body(ResultRecord.success(bundle));
    }

    private boolean matches(String candidates, String etag) {
        return candidates != null && Arrays.stream(candidates.split(",")).map(String::trim)
                .anyMatch(candidate -> "*".equals(candidate) || etag.equals(candidate) || etag.substring(2).equals(candidate));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
