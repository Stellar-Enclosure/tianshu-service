package top.egon.cola.component.tianshu.api.ai;

/** Internal AI transport paths and wire limits shared by the SDK and Tianshu Admin. */
public final class DdcAiHttpContract {
    public static final String BASE_PATH = "/internal/v1/ai";
    public static final String PROMPTS_PATH = BASE_PATH + "/prompts";
    public static final String SKILLS_PATH = BASE_PATH + "/skills";
    public static final String VERSION_HEAD_PATH = BASE_PATH + "/version-head";
    public static final String SKILL_DRAFT_PATH = BASE_PATH + "/skill-draft";
    public static final String SKILL_PUBLICATIONS_PATH = BASE_PATH + "/skill-publications";
    public static final String USAGE_REPORTS_PATH = BASE_PATH + "/usage-reports";

    public static final int MAX_BATCH_SIZE = 100;
    public static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
    public static final int MAX_ERROR_BODY_BYTES = 8 * 1024;

    public static final String ERROR_INVALID_REQUEST = "TIANSHU_AI_INVALID_REQUEST";
    public static final String ERROR_UNAUTHENTICATED = "TIANSHU_AI_UNAUTHENTICATED";
    public static final String ERROR_FORBIDDEN = "TIANSHU_AI_FORBIDDEN";
    public static final String ERROR_NOT_FOUND = "TIANSHU_AI_NOT_FOUND";
    public static final String ERROR_REVISION_CONFLICT = "TIANSHU_AI_REVISION_CONFLICT";
    public static final String ERROR_UNAVAILABLE = "TIANSHU_AI_UNAVAILABLE";

    /** Policy keys for separately provisioned OAuth scopes. */
    public enum Operation {
        READ,
        WRITE,
        PUBLISH,
        REPORT
    }

    private DdcAiHttpContract() { }
}
