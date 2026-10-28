package top.egon.cola.component.tianshu.api.list;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ListDelegationRequestTest {
    private final ListDefinition definition = new ListDefinition("order.page.list", "main", List.of(
            new ListDefinition.Column("id", List.of("order.field.id"), "order.list", "field.id", true, true)));
    private final ListDefinitionBinding binding = new ListDefinitionBinding(definition, "order.component.list",
            "order.api.list", "order.api.template.system", "order.api.template.personal");

    @Test void saveRequiresMatchingIdentityAndRevisionForAnExistingTemplate() {
        assertDoesNotThrow(() -> request(ListDelegationRequest.Operation.SAVE_PERSONAL, "10", "2", "Mine", List.of("id"), false));
        assertThrows(IllegalArgumentException.class, () -> request(ListDelegationRequest.Operation.SAVE_PERSONAL, "10", null, "Mine", List.of("id"), false));
        assertThrows(IllegalArgumentException.class, () -> request(ListDelegationRequest.Operation.SAVE_PERSONAL, null, null, "Mine", List.of("id", "id"), false));
    }

    @Test void loadAndSelectionCannotCarryUnrelatedMutationPayloads() {
        assertDoesNotThrow(() -> request(ListDelegationRequest.Operation.LOAD, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> request(ListDelegationRequest.Operation.LOAD, "10", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> request(ListDelegationRequest.Operation.SELECT, "10", "2", null, null, null));
    }

    private ListDelegationRequest request(ListDelegationRequest.Operation operation, String id, String version,
            String name, List<String> columns, Boolean defaultTemplate) {
        return new ListDelegationRequest(binding, operation, "1", "2", "3", List.of("7"),
                Map.of("order:read", Set.of("order:read")), id, version, name, columns, defaultTemplate, false);
    }
}
