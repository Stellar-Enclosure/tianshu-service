package top.egon.cola.component.tianshu.admin.model.bo;

/** Counts derived from replica candidates and checked by the primary update predicate. */
public record DdcPublishAckSummaryBO(int total, int success, int failed, int ignored, int timeout,
                                     String terminalStatus, String failureStage, String errorMessage,
                                     String publicationCheck) {
    public DdcPublishAckSummaryBO {
        if (total < 0 || success < 0 || failed < 0 || ignored < 0 || timeout < 0
                || (long) success + failed + ignored + timeout > total
                || publicationCheck == null || !java.util.Set.of("ANY", "PUBLISHED", "UNPUBLISHED").contains(publicationCheck)
                || terminalStatus != null && !java.util.Set.of("SUCCESS", "FAILED", "TIMEOUT").contains(terminalStatus)
                || "SUCCESS".equals(terminalStatus) && !"PUBLISHED".equals(publicationCheck)
                || "FAILED".equals(terminalStatus) && (failed == 0 || !"ANY".equals(publicationCheck))
                || "TIMEOUT".equals(terminalStatus) && !"ANY".equals(publicationCheck)
                || terminalStatus == null && (failed > 0 || "PUBLISHED".equals(publicationCheck))
                || !"ANY".equals(publicationCheck) && success != total) {
            throw new IllegalArgumentException("TIANSHU_ACK_SUMMARY_INVALID");
        }
    }
}
