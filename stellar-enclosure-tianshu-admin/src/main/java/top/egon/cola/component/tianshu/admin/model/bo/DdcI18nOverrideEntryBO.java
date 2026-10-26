package top.egon.cola.component.tianshu.admin.model.bo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** One published key, selected either by its latest review domain or the requested source hash. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DdcI18nOverrideEntryBO {
    private String pageCode;
    private String messageKey;
    private String sourceHash;
    private Long publicationRevision;
    private String translationsJson;
    private String selectionKind;
}
