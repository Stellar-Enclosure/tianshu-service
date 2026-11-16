package top.egon.cola.component.tianshu.ai.si;

import top.egon.cola.component.tianshu.model.si.DdcSiSelfRegistrationRequest;

import java.util.List;

/** Extension point allowing microservices to contribute code-first Agents, MCPs, Prompts or Skills on startup. */
@FunctionalInterface
public interface DdcSiLocalResourceContributor {

    List<DdcSiSelfRegistrationRequest> contributeResources();
}
