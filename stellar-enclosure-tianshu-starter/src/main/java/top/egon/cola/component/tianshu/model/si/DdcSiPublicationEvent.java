package top.egon.cola.component.tianshu.model.si;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.lang.Nullable;

import java.time.Instant;

/**
 * Compact outbox event payload (EVENT-001: tianshu.si.catalog.changed).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcSiPublicationEvent {

    public enum EventType {
        PUBLISHED("PUBLISHED"),
        PROMOTED("PROMOTED"),
        ROLLED_BACK("ROLLED_BACK"),
        DEPRECATED("DEPRECATED"),
        ARCHIVED("ARCHIVED"),
        BOUND("BOUND"),
        UNBOUND("UNBOUND");

        private final String code;

        EventType(String code) {
            this.code = code;
        }

        @JsonValue
        public String getCode() {
            return code;
        }

        @JsonCreator
        public static EventType fromCode(String code) {
            for (EventType value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown SI event type: " + code);
        }
    }

    @NotBlank
    private String eventId;

    @NotNull
    private EventType eventType;

    @NotNull
    @Positive
    private Long tenantId;

    @NotBlank
    private String bizCode;

    @NotBlank
    private String namespaceCode;

    @NotBlank
    private String envCode;

    @NotNull
    private DdcSiKindEnum kind;

    @NotBlank
    private String resourceKey;

    @Nullable
    private Integer activeVersionNo;

    @Nullable
    private Long catalogRevision;

    @NotNull
    private Instant occurredAt;
}
