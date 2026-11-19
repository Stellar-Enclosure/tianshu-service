package top.egon.cola.component.tianshu.admin.model.convertor;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseForwardConverter;
import top.egon.cola.component.tianshu.admin.model.command.DdcAiSkillDraftUploadCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcAiSkillPublishCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcAiUsageReportBatchCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcAiUsageReportCommand;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAiPublicationReceiptResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAiRevisionResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAiUsageReportReceiptResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcAiVersionHeadResult;
import top.egon.cola.component.tianshu.model.ai.DdcAiContentDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiPublicationReceiptDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiRevisionDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiSkillDraftUploadDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiSkillPublishDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiUsageReportBatchDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiUsageReportDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiUsageReportReceiptDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiVersionHeadDTO;

/** Typed HTTP/domain mapping; it has no transport, authentication or persistence responsibility. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcAiInternalConverter extends BaseForwardConverter<DdcAiUsageReportBatchDTO, DdcAiUsageReportBatchCommand> {
    @Override
    DdcAiUsageReportBatchCommand toTarget(DdcAiUsageReportBatchDTO source);

    DdcAiUsageReportCommand toCommand(DdcAiUsageReportDTO source);
    DdcAiUsageReportReceiptDTO toReceipt(DdcAiUsageReportReceiptResult source);
    DdcAiUsageReportReceiptDTO.ItemDTO toItem(DdcAiUsageReportReceiptResult.ItemResult source);

    @Mapping(target = "content", expression = "java(content(source))")
    DdcAiSkillDraftUploadCommand toDraft(DdcAiSkillDraftUploadDTO source);

    DdcAiSkillPublishCommand toPublication(DdcAiSkillPublishDTO source);
    DdcAiVersionHeadDTO toHead(DdcAiVersionHeadResult source);
    DdcAiRevisionDTO toRevision(DdcAiRevisionResult source);
    DdcAiPublicationReceiptDTO toPublicationReceipt(DdcAiPublicationReceiptResult source);

    default DdcAiContentDTO content(DdcAiSkillDraftUploadDTO source) {
        return DdcAiContentDTO.builder().promptText(null).nodes(source.getFiles()).build();
    }
}
