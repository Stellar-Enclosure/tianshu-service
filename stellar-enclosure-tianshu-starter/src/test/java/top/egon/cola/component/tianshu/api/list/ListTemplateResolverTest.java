package top.egon.cola.component.tianshu.api.list;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ListTemplateResolverTest {
    private final ListDefinition definition = new ListDefinition("order.page.list", "main", List.of(
            new ListDefinition.Column("order", List.of("order.field.id"), "order.list", "field.id", true, true),
            new ListDefinition.Column("phone", List.of("order.field.phone"), "order.list", "field.phone", true, true),
            new ListDefinition.Column("profit", List.of("order.field.profit"), "order.list", "field.profit", false, true),
            new ListDefinition.Column("computed", List.of("order.field.id", "order.field.profit"), "order.list", "field.computed", true, true)));

    @Test
    void personalTemplateCanChooseAuthorizedColumnsOutsideSystemDefaults() {
        var allowed = Set.of("order.field.id", "order.field.profit");
        assertEquals(List.of("profit", "order"), ListTemplateResolver.visible(definition,
                List.of("profit", "phone", "order"), allowed::contains).stream().map(ListDefinition.Column::columnKey).toList());
    }

    @Test
    void missingTemplateUsesDefaultsButEmptyTemplateDoesNotWiden() {
        var allowed = Set.of("order.field.id", "order.field.phone");
        assertEquals(List.of("order", "phone"), ListTemplateResolver.visible(definition, null, allowed::contains)
                .stream().map(ListDefinition.Column::columnKey).toList());
        assertTrue(ListTemplateResolver.visible(definition, List.of(), allowed::contains).isEmpty());
    }

    @Test
    void editingRetainsFieldsTemporarilyHiddenByPermission() {
        var allowed = Set.of("order.field.id", "order.field.profit");
        assertEquals(List.of("profit", "phone"), ListTemplateResolver.mergePersonal(definition,
                List.of("order", "phone"), List.of("profit"), allowed::contains));
        assertThrows(IllegalArgumentException.class, () -> ListTemplateResolver.mergePersonal(definition,
                List.of("phone"), List.of("phone"), allowed::contains));
    }

    @Test
    void retainedAndNewColumnsCannotExceedTheStoredTemplateLimit() {
        var retained = java.util.stream.IntStream.range(0, 256).mapToObj(index -> "removed" + index).toList();
        assertThrows(IllegalArgumentException.class, () -> ListTemplateResolver.mergePersonal(definition,
                retained, List.of("order"), key -> true));
    }

    @Test
    void rejectsUnknownAndDuplicateRequestedColumns() {
        assertThrows(IllegalArgumentException.class, () -> ListTemplateResolver.mergePersonal(definition,
                List.of(), List.of("unknown"), key -> true));
        assertThrows(IllegalArgumentException.class, () -> ListTemplateResolver.mergePersonal(definition,
                List.of(), List.of("order", "order"), key -> true));
    }

    @Test
    void selectionIsScopedToTheVerifiedOwnerAndFallsBackThroughDefaults() {
        var system = new ListTemplateResolver.Template("s", ListTemplateResolver.Template.Type.SYSTEM,
                null, null, true, List.of("order"));
        var mine = new ListTemplateResolver.Template("m", ListTemplateResolver.Template.Type.PERSONAL,
                "https://id.example", "alice", true, List.of("phone"));
        var other = new ListTemplateResolver.Template("o", ListTemplateResolver.Template.Type.PERSONAL,
                "https://id.example", "bob", true, List.of("profit"));
        var templates = List.of(system, mine, other);
        assertEquals(system, ListTemplateResolver.select(templates, "s", "https://id.example", "alice"));
        assertEquals(mine, ListTemplateResolver.select(templates, "o", "https://id.example", "alice"));
        assertEquals(system, ListTemplateResolver.select(templates, null, "https://other.example", "alice"));
    }
}
