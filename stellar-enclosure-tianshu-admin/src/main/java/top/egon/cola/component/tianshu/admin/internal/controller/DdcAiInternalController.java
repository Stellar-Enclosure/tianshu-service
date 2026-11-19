package top.egon.cola.component.tianshu.admin.internal.controller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcAiInternalConverter;
import top.egon.cola.component.tianshu.admin.security.ai.DdcAiInternalSecurityConfiguration;
import top.egon.cola.component.tianshu.admin.security.ai.DdcAiServiceCallerResolver;
import top.egon.cola.component.tianshu.admin.service.ai.DdcAiFeedbackService;
import top.egon.cola.component.tianshu.admin.service.ai.DdcAiResourceService;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract.Operation;
import top.egon.cola.component.tianshu.model.ai.DdcAiKindEnum;
import top.egon.cola.component.tianshu.model.ai.DdcAiResourceQuery;
import top.egon.cola.component.tianshu.model.ai.DdcAiSkillDraftUploadDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiSkillPublishDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiUsageReportBatchDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiUsageReportReceiptDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiVersionHeadDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiRevisionDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiPublicationReceiptDTO;
import top.egon.cola.component.tianshu.model.ai.DdcPromptResult;
import top.egon.cola.component.tianshu.model.ai.DdcSkillResult;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

/** Internal fixed HTTP commands. Authorization is established by the SERVICE chain and rechecked by domain services. */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController("ddcAiInternalController")
public class DdcAiInternalController {
    @Qualifier("ddcAiResourceService")
    private final DdcAiResourceService resources;
    @Qualifier("ddcAiFeedbackService")
    private final DdcAiFeedbackService feedback;
    @Qualifier("ddcAiInternalConverterImpl")
    private final DdcAiInternalConverter converter;
    @Qualifier("jacksonObjectMapper")
    private final ObjectMapper mapper;
    @Qualifier("egonColaValidationUtils")
    private final ValidationUtils validation;

    @GetMapping(path = DdcAiHttpContract.PROMPTS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @io.swagger.v3.oas.annotations.Operation(operationId = "internalAiPrompt", summary = "Verified SERVICE AI prompt operation")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authorized fixed AI wire response",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DdcPromptResult.class)))
    public ResponseEntity<byte[]> prompt(HttpServletRequest request) {
        DdcAiServiceCallerResolver.current(Operation.READ);
        var query = query(request);
        var result = resources.getPublishedVersion(query, DdcAiKindEnum.PROMPT);
        if (result.getVersion() == null || !DdcAiKindEnum.PROMPT.name().equals(result.getVersion().getKind())
                || result.getContent() == null || result.getContent().getPromptText() == null) throw contentInvalid();
        return success(request, DdcPromptResult.builder().resourceId(result.getVersion().getResourceId())
                .resourceKey(query.resourceKey()).versionId(result.getVersion().getId())
                .versionNo(result.getVersion().getVersionNo()).contentSha256(result.getVersion().getContentSha256())
                .template(result.getContent().getPromptText()).build());
    }

    @GetMapping(path = DdcAiHttpContract.SKILLS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @io.swagger.v3.oas.annotations.Operation(operationId = "internalAiSkill", summary = "Verified SERVICE AI skill operation")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authorized fixed AI wire response",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DdcSkillResult.class)))
    public ResponseEntity<byte[]> skill(HttpServletRequest request) {
        DdcAiServiceCallerResolver.current(Operation.READ);
        var query = query(request);
        var result = resources.getPublishedVersion(query, DdcAiKindEnum.SKILL);
        if (result.getVersion() == null || !DdcAiKindEnum.SKILL.name().equals(result.getVersion().getKind())
                || result.getContent() == null || result.getContent().getNodes() == null) throw contentInvalid();
        return success(request, DdcSkillResult.builder().resourceId(result.getVersion().getResourceId())
                .resourceKey(query.resourceKey()).versionId(result.getVersion().getId())
                .versionNo(result.getVersion().getVersionNo()).contentSha256(result.getVersion().getContentSha256())
                .files(result.getContent().getNodes()).build());
    }

    @GetMapping(path = DdcAiHttpContract.VERSION_HEAD_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @io.swagger.v3.oas.annotations.Operation(operationId = "internalAiVersionHead", summary = "Verified SERVICE AI versionHead operation")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authorized fixed AI wire response",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DdcAiVersionHeadDTO.class)))
    public ResponseEntity<byte[]> versionHead(HttpServletRequest request) {
        DdcAiServiceCallerResolver.current(Operation.READ);
        if (request.getParameter("versionNo") != null) throw invalid();
        return success(request, converter.toHead(resources.getVersionHead(query(request))));
    }

    @PostMapping(path = DdcAiHttpContract.SKILL_DRAFT_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @io.swagger.v3.oas.annotations.Operation(operationId = "internalAiDraft", summary = "Verified SERVICE AI draft operation")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authorized fixed AI wire response",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DdcAiRevisionDTO.class)))
    public ResponseEntity<byte[]> draft(HttpServletRequest request) throws IOException {
        DdcAiServiceCallerResolver.current(Operation.WRITE);
        JsonNode body = body(request);
        decimals(body, Set.of("resourceId"), false);
        decimals(body, Set.of("expectedRevision"), true);
        var command = converter.toDraft(validation.validate(read(body, DdcAiSkillDraftUploadDTO.class)));
        validation.validate(command);
        return success(request, converter.toRevision(resources.replaceSkillDraft(command)));
    }

    @PostMapping(path = DdcAiHttpContract.SKILL_PUBLICATIONS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @io.swagger.v3.oas.annotations.Operation(operationId = "internalAiPublish", summary = "Verified SERVICE AI publish operation")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authorized fixed AI wire response",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DdcAiPublicationReceiptDTO.class)))
    public ResponseEntity<byte[]> publish(HttpServletRequest request) throws IOException {
        DdcAiServiceCallerResolver.current(Operation.PUBLISH);
        JsonNode body = body(request);
        decimals(body, Set.of("resourceId"), false);
        decimals(body, Set.of("expectedRevision"), true);
        var command = converter.toPublication(validation.validate(read(body, DdcAiSkillPublishDTO.class)));
        validation.validate(command);
        return success(request, converter.toPublicationReceipt(resources.publishSkillDraft(command)));
    }

    @PostMapping(path = DdcAiHttpContract.USAGE_REPORTS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    @EgonGatewayPolicy(exposure = EgonGatewayPolicy.Exposure.INTERNAL)
    @io.swagger.v3.oas.annotations.Operation(operationId = "internalAiReport", summary = "Verified SERVICE AI report operation")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authorized fixed AI wire response",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DdcAiUsageReportReceiptDTO.class)))
    public ResponseEntity<byte[]> report(HttpServletRequest request) throws IOException {
        DdcAiServiceCallerResolver.current(Operation.REPORT);
        JsonNode body = body(request);
        JsonNode reports = body.get("reports");
        if (reports == null || !reports.isArray() || reports.isEmpty() || reports.size() > DdcAiHttpContract.MAX_BATCH_SIZE) throw invalid();
        for (JsonNode report : reports) {
            if (!report.isObject()) throw invalid();
            decimals(report, Set.of("resourceId", "versionId", "versionNo"), false);
            // Keep invalid individual timestamps as item-level validation failures, as on the previous contract.
            JsonNode occurredAt = report.get("occurredAt");
            if (occurredAt != null && occurredAt.isTextual()) {
                try { Instant.parse(occurredAt.textValue()); }
                catch (DateTimeParseException malformed) { ((com.fasterxml.jackson.databind.node.ObjectNode) report).putNull("occurredAt"); }
            }
        }
        DdcAiUsageReportBatchDTO dto = read(body, DdcAiUsageReportBatchDTO.class);
        // Deliberately validate the non-cascading domain envelope, not the SDK DTO's @Valid item list.
        var command = validation.validate(converter.toTarget(dto));
        return success(request, converter.toReceipt(feedback.accept(command)));
    }

    private DdcAiResourceQuery query(HttpServletRequest request) {
        String version = request.getParameter("versionNo");
        return validation.validate(new DdcAiResourceQuery(
                DdcAiInternalSecurityConfiguration.checked(request.getParameter("namespaceCode"), 128),
                DdcAiInternalSecurityConfiguration.checked(request.getParameter("resourceKey"), 128),
                version == null ? null : DdcAiInternalSecurityConfiguration.decimal(version, false),
                DdcAiInternalSecurityConfiguration.decimal(request.getParameter("resourceId"), false)));
    }

    private JsonNode body(HttpServletRequest request) throws IOException {
        if (request.getContentType() == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType()))) throw invalid();
        if (request.getContentLengthLong() > DdcAiHttpContract.MAX_BODY_BYTES) throw new TooLarge();
        byte[] bytes = request.getInputStream().readNBytes(DdcAiHttpContract.MAX_BODY_BYTES + 1);
        if (bytes.length > DdcAiHttpContract.MAX_BODY_BYTES) throw new TooLarge();
        try (JsonParser parser = mapper.createParser(bytes)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            JsonNode result = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(parser);
            if (result == null || !result.isObject()) throw invalid();
            return result;
        } catch (com.fasterxml.jackson.core.JacksonException malformed) {
            throw invalid();
        }
    }

    private <T> T read(JsonNode body, Class<T> type) throws IOException {
        try (JsonParser parser = body.traverse(mapper)) {
            return mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL).readValue(parser);
        } catch (com.fasterxml.jackson.core.JacksonException malformed) {
            throw invalid();
        }
    }

    private static void decimals(JsonNode object, Set<String> fields, boolean zeroAllowed) {
        for (String field : fields) {
            JsonNode value = object.get(field);
            if (value != null && !value.isNull()) {
                if (!value.isTextual()) throw invalid();
                DdcAiInternalSecurityConfiguration.decimal(value.textValue(), zeroAllowed);
            }
        }
    }

    private ResponseEntity<byte[]> success(HttpServletRequest request, Object value) {
        // Serialize this authorized fixed wire once. USER MVC field policies and Jackson 3 retain their own converters.
        final byte[] wire;
        try { wire = mapper.writeValueAsBytes(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalidResult) { throw contentInvalid(); }
        if (wire.length > DdcAiHttpContract.MAX_BODY_BYTES) throw contentInvalid();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header("Cache-Control", "no-store")
                .header("Vary", "Authorization").header("x-egon-request-id", DdcAiInternalSecurityConfiguration.requestId(request)).body(wire);
    }

    @ExceptionHandler({RuntimeException.class, IOException.class})
    public void failure(Exception failure, HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        String code = DdcAiHttpContract.ERROR_UNAVAILABLE;
        if (failure instanceof TooLarge) {
            status = HttpStatus.PAYLOAD_TOO_LARGE; code = DdcAiHttpContract.ERROR_INVALID_REQUEST;
        } else if (failure instanceof ConstraintViolationException || failure instanceof IllegalArgumentException || failure instanceof IOException) {
            status = HttpStatus.BAD_REQUEST; code = DdcAiHttpContract.ERROR_INVALID_REQUEST;
        } else if (failure instanceof DdcAiServiceCallerResolver.Rejected) {
            status = HttpStatus.FORBIDDEN; code = DdcAiHttpContract.ERROR_FORBIDDEN;
        } else if (failure instanceof CommonException common) {
            switch (common.getCode()) {
                case 56302, 56306, 56325 -> { status = HttpStatus.NOT_FOUND; code = DdcAiHttpContract.ERROR_NOT_FOUND; }
                case 56303, 56304, 56305, 56311, 56314, 56323, 56324 -> { status = HttpStatus.CONFLICT; code = DdcAiHttpContract.ERROR_REVISION_CONFLICT; }
                case 56307 -> { status = HttpStatus.FORBIDDEN; code = DdcAiHttpContract.ERROR_FORBIDDEN; }
                case 56301, 56308, 56320, 56321 -> { status = HttpStatus.BAD_REQUEST; code = DdcAiHttpContract.ERROR_INVALID_REQUEST; }
                default -> { /* Dependency/integrity failures cannot look like a miss or a successful write. */ }
            }
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            log.warn("Internal AI operation unavailable; failureType={} requestId={}", failure.getClass().getSimpleName(),
                    DdcAiInternalSecurityConfiguration.requestId(request));
        }
        DdcAiInternalSecurityConfiguration.writeError(mapper, request, response, status, code);
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANSHU_AI_INVALID_REQUEST"); }
    private static CommonException contentInvalid() { return new CommonException(56309, "TIANSHU_AI_CONTENT_INVALID", "AI content integrity is unavailable"); }
    private static final class TooLarge extends IllegalArgumentException { }
}
