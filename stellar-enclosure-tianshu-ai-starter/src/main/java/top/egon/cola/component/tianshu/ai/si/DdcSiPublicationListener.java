package top.egon.cola.component.tianshu.ai.si;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.listener.MessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import top.egon.cola.component.tianshu.model.si.DdcSiPublicationEvent;
import top.egon.cola.component.tianshu.model.tenant.DdcTenantMessageDTO;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Consumes EVENT-001 catalog change notifications with bounded LRU eventId deduplication. */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcSiPublicationListener implements MessageListener<String> {

    private static final int MAX_SEEN_EVENT_IDS = 10_000;
    private static final TypeReference<DdcTenantMessageDTO<DdcSiPublicationEvent>> ENVELOPE_TYPE =
            new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    @Qualifier("ddcSiCatalogSyncCoordinator")
    private final DdcSiCatalogSyncCoordinator coordinator;
    private final DdcSiClientProperties properties;

    private final Map<String, Boolean> seenEvents = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_SEEN_EVENT_IDS;
                }
            });

    @Override
    public void onMessage(CharSequence channel, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            DdcTenantMessageDTO<DdcSiPublicationEvent> envelope = objectMapper.readValue(json, ENVELOPE_TYPE);
            if (envelope == null || envelope.getPayload() == null) {
                return;
            }
            Long configuredTenant = properties.getTenantId();
            if (configuredTenant != null && !envelope.belongsToTenant(configuredTenant)) {
                return;
            }
            onCatalogPublicationEvent(envelope.getPayload());
        } catch (JsonProcessingException | IllegalArgumentException malformed) {
            log.warn("Ignoring malformed SI catalog publication message: {}", malformed.getMessage());
        } catch (RuntimeException unexpected) {
            log.warn("Failed handling SI catalog publication message: {}", unexpected.getMessage());
        }
    }

    @EventListener
    public void onCatalogPublicationEvent(DdcSiPublicationEvent event) {
        if (event == null || event.getEventId() == null || event.getTenantId() == null) {
            return;
        }
        Long configuredTenant = properties.getTenantId();
        if (configuredTenant != null && !Objects.equals(configuredTenant, event.getTenantId())) {
            log.debug("Ignoring SI event {} for foreign tenant {}", event.getEventId(), event.getTenantId());
            return;
        }
        if (properties.getEnvCode() != null && !properties.getEnvCode().isBlank()
                && !properties.getEnvCode().equals(event.getEnvCode())) {
            return;
        }
        synchronized (seenEvents) {
            if (seenEvents.containsKey(event.getEventId())) {
                return;
            }
            seenEvents.put(event.getEventId(), Boolean.TRUE);
        }
        coordinator.scheduleRefresh(
                event.getTenantId(), event.getBizCode(), event.getNamespaceCode(), event.getEnvCode());
    }
}
