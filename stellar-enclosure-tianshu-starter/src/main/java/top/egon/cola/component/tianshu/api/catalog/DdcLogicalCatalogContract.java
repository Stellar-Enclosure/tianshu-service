package top.egon.cola.component.tianshu.api.catalog;

/** Exact paths and authorization scope for the internal, read-only Tianshu catalog. */
public final class DdcLogicalCatalogContract {
    public static final String BASE_PATH = "/internal/v1/tianshu/catalog";
    public static final String BUSINESS_PATH = BASE_PATH + "/business";
    public static final String BUSINESSES_PATH = BASE_PATH + "/businesses";
    public static final String APPLICATION_PATH = BASE_PATH + "/application";
    public static final String APPLICATIONS_PATH = BASE_PATH + "/applications";
    public static final String SCOPE = "TIANSHU_CATALOG_READ";
    public static final String OUTCOME_HEADER = "X-Egon-Catalog-Outcome";
    public static final String OUTCOME_NOT_FOUND = "not-found";
    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_KEYWORD_LENGTH = 128;

    private DdcLogicalCatalogContract() {
    }
}
