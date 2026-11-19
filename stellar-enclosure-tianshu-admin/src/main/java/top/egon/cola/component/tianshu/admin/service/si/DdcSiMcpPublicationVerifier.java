package top.egon.cola.component.tianshu.admin.service.si;

import java.time.Instant;

/** Deployment approval for an exact MCP publication; it performs no discovery or network access. */
@FunctionalInterface
public interface DdcSiMcpPublicationVerifier {
    ApprovalVO requireApproved(PublicationScopeVO scope, String normalizedSpecJson, String normalizedDigestSha256);

    /** The resource ID is read from the actual registered row, never from a submitted mapping receipt. */
    record PublicationScopeVO(String tenantId, String bizCode, String namespaceCode, String envCode,
                              String ownerAppCode, String resourceKey, String resourceId) {
        public PublicationScopeVO {
            positive(tenantId); positive(resourceId);
            token(bizCode, false); token(namespaceCode, false); token(envCode, false);
            token(ownerAppCode, true); token(resourceKey, false);
        }
        private static void positive(String value) {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid();
            try { Long.parseLong(value); } catch (NumberFormatException failure) { throw invalid(); }
        }
        private static void token(String value, boolean emptyAllowed) {
            if (value == null || (!emptyAllowed && value.isEmpty()) || value.length() > 128
                    || !value.equals(value.strip()) || value.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        }
        private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANSHU_MCP_PUBLICATION_SCOPE_INVALID"); }
    }

    /** Audit facts only. Exact-body approval deliberately does not permit unapproved tightening edits. */
    record ApprovalVO(String approvalId, String approvalsSha256, String canonicalSpecSha256, Instant validUntil) { }
}
