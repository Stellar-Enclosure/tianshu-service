package top.egon.cola.component.tianshu.admin.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.redisson.Redisson;
import org.redisson.config.HostPortNatMapper;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import top.egon.cola.component.tianshu.admin.repository.DdcRedisRepository;
import top.egon.cola.component.tianshu.redis.DdcRedisClientFactory;
import top.egon.cola.component.tianshu.model.config.DdcPublishMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DdcRedisClusterIT {

    private static final String REDIS_IMAGE = "redis:7.4-alpine";

    private final Network network = Network.newNetwork();

    private final List<GenericContainer<?>> nodes = new ArrayList<>();

    private RedissonClient redisson;

    @BeforeAll
    void startTopology() throws Exception {
        for (int index = 1; index <= 3; index++) {
            GenericContainer<?> node = node(index);
            node.start();
            nodes.add(node);
        }
        Container.ExecResult create = nodes.getFirst().execInContainer(
                "redis-cli",
                "--cluster", "create",
                "redis-cluster-1:6379",
                "redis-cluster-2:6379",
                "redis-cluster-3:6379",
                "--cluster-replicas", "0",
                "--cluster-yes"
        );
        assertThat(create.getExitCode()).isZero();
        assertThat(create.getStdout()).contains("[OK] All 16384 slots covered");

        Config config = DdcRedisClientFactory.configuration(
                "CLUSTER",
                nodes.stream().map(this::address).toList(),
                null,
                null,
                0,
                null,
                0
        );
        config.useClusterServers()
                .setScanInterval(500)
                .setNatMapper(natMapper());
        redisson = Redisson.create(config);
    }

    @AfterAll
    void stopTopology() {
        if (redisson != null) {
            redisson.shutdown();
        }
        nodes.reversed().forEach(GenericContainer::stop);
        network.close();
    }

    @Test
    void administrativeConfigCacheRoundTripsWithoutCrossSlot() {
        DdcRedisRepository configs = new DdcRedisRepository(redisson, new ObjectMapper());
        configs.writeConfig("demo", "test", "default", "switch", "on", 1L);
        configs.publish(message());
        assertThat(configs.readConfigValue(
                "demo", "test", "default", "switch"
        )).isEqualTo("on");
        assertThat(configs.readConfigVersion(
                "demo", "test", "default", "switch"
        )).isEqualTo(1L);
    }

    private GenericContainer<?> node(int index) {
        return new GenericContainer<>(REDIS_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("redis-cluster-" + index)
                .withExposedPorts(6379)
                .withCommand(
                        "redis-server",
                        "--cluster-enabled", "yes",
                        "--cluster-config-file", "nodes.conf",
                        "--cluster-node-timeout", "5000",
                        "--appendonly", "no",
                        "--protected-mode", "no"
                )
                .waitingFor(Wait.forListeningPort());
    }

    private String address(GenericContainer<?> node) {
        return "redis://" + node.getHost() + ":" + node.getMappedPort(6379);
    }

    private HostPortNatMapper natMapper() {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (GenericContainer<?> node : nodes) {
            String internal = containerIp(node) + ":6379";
            String mapped = node.getHost() + ":" + node.getMappedPort(6379);
            mappings.put(internal, mapped);
        }
        HostPortNatMapper mapper = new HostPortNatMapper();
        mapper.setHostsPortMap(mappings);
        return mapper;
    }

    private String containerIp(GenericContainer<?> container) {
        return container.getContainerInfo()
                .getNetworkSettings()
                .getNetworks()
                .values()
                .iterator()
                .next()
                .getIpAddress();
    }

    private DdcPublishMessage message() {
        DdcPublishMessage message = new DdcPublishMessage();
        message.setChangeId("change-1");
        message.setAppCode("demo");
        message.setEnv("test");
        message.setNamespace("default");
        message.setResourceName("application.yml");
        message.setContent("feature:\n  enabled: true\n");
        message.setFormat("YAML");
        message.setTargetVersion(1L);
        return message;
    }

}
