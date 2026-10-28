package top.egon.cola.component.tianshu.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.MockHttpInputMessage;
import top.egon.cola.component.tianshu.api.list.ListDefinition;
import top.egon.cola.component.tianshu.api.list.ListDefinitionBinding;
import top.egon.cola.component.tianshu.api.list.ListDelegationRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class DdcListDelegationWebConfigurationTest {
    private final DdcListDelegationWebConfiguration.RequestConverter converter = new DdcListDelegationWebConfiguration.RequestConverter();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void acceptsTheTypedContractAndRejectsUnknownOrDuplicateFields() throws Exception {
        var definition = new ListDefinition("shop.page.list", "main", List.of(
                new ListDefinition.Column("id", List.of("shop.field.id"), "shop.list", "field.id", true, true)));
        var binding = new ListDefinitionBinding(definition, "shop.component.list", "shop.api.list", "shop.api.system", "shop.api.personal");
        var value = new ListDelegationRequest(binding, ListDelegationRequest.Operation.LOAD, "1", "2", "3", List.of("7"),
                Map.of("shop:read", Set.of("shop:read")), null, null, null, null, null, false);
        String json = mapper.writeValueAsString(value);
        assertThat(converter.readInternal(ListDelegationRequest.class, message(json))).isEqualTo(value);
        assertThatThrownBy(() -> converter.readInternal(ListDelegationRequest.class, message(json.replaceFirst("\\{", "{\"subject\":\"other\","))))
                .isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> converter.readInternal(ListDelegationRequest.class, message(json.replaceFirst("\\{", "{\"catalogVersion\":\"1\","))))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test void rejectsOversizedBodiesBeforeParsing() {
        var body = new MockHttpInputMessage(new byte[1024 * 1024 + 1]);
        assertThatThrownBy(() -> converter.readInternal(ListDelegationRequest.class, body))
                .isInstanceOf(DdcListDelegationWebConfiguration.BodyTooLargeException.class);
    }

    private MockHttpInputMessage message(String json) { return new MockHttpInputMessage(json.getBytes(StandardCharsets.UTF_8)); }
}
