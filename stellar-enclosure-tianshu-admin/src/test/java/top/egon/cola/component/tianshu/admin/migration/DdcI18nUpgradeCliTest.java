package top.egon.cola.component.tianshu.admin.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlManifestBO;
import top.egon.cola.component.common.mybatis.ddl.EgonColaDdlResult;
import top.egon.cola.component.common.mybatis.sharding.EgonColaShardingProperties;
import top.egon.cola.component.common.mybatis.sharding.bootstrap.EgonColaShardingTopologyValidator;
import top.egon.cola.component.common.mybatis.sharding.bootstrap.EgonColaShardingYamlLoader;
import top.egon.cola.component.common.mybatis.sharding.strategy.EgonColaShardingStrategyFactory;
import top.egon.cola.component.common.mybatis.sharding.strategy.EgonColaSingleTableShardingStrategy;
import top.egon.cola.component.common.mybatis.sharding.strategy.EgonColaStandardTenantIdShardingStrategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class DdcI18nUpgradeCliTest {
    @TempDir Path temporary;

    @Test void upgradeRequiresAnExplicitSingleAbsoluteTargetConfiguration() {
        assertThat(DdcPersistenceMigrationCli.parseMode(new String[]{"upgrade-i18n", "--target-config", "/private/config.yml"}))
                .isEqualTo(DdcPersistenceMigrationCli.Mode.UPGRADE_I18N);
        for (String[] invalid : List.of(new String[]{"upgrade-i18n"},
                new String[]{"upgrade-i18n", "--target-config", "relative.yml"},
                new String[]{"upgrade-i18n", "--target-config", "/a.yml", "--source-config", "/b.yml"},
                new String[]{"upgrade-i18n", "--target-config", "/a.yml", "--target-config", "/b.yml"})) {
            assertThatThrownBy(() -> DdcPersistenceMigrationCli.parseMode(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void rejectsUnprotectedAndSymlinkTargetsBeforeReadingConfigurationOrConnecting() throws Exception {
        Path config = temporary.resolve("target.yml");
        Files.writeString(config, "test: fixture\n");
        Files.setPosixFilePermissions(config, PosixFilePermissions.fromString("rw-r--r--"));
        assertThatThrownBy(() -> DdcPersistenceMigrationCli.main(command(config)))
                .hasMessageContaining("FILE_PERMISSIONS_UNSAFE");
        Files.setPosixFilePermissions(config, PosixFilePermissions.fromString("rw-------"));
        Path link = temporary.resolve("link.yml");
        Files.createSymbolicLink(link, config);
        assertThatThrownBy(() -> DdcPersistenceMigrationCli.main(command(link)))
                .hasMessageContaining("PROTECTED_FILE_REQUIRED");
        assertThatThrownBy(() -> DdcPersistenceMigrationCli.main(command(config)))
                .hasMessageContaining("CONFIGURATION_REQUIRED");
    }

    @Test void derivesPreviousFingerprintByRemovingOnlyTheNewLogicalTableWithoutMutatingCurrent() throws Exception {
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            ValidationUtils validation = new ValidationUtils(validators.getValidator());
            var environment = environment();
            var current = bind(environment);
            var previous = bind(environment);
            var loader = loader(environment, validation);
            var validator = new EgonColaShardingTopologyValidator(validation);
            var currentTopology = validator.validate(current, loader.load(current));
            var previousTopology = DdcPersistenceMigrationCli.priorI18nTopology(current, previous, currentTopology, loader, validator);
            assertThat(current.getTables()).containsKey("ddc_i18n_page").hasSize(31);
            assertThat(previous.getTables()).doesNotContainKey("ddc_i18n_page").hasSize(30);
            assertThat(previousTopology.profiles()).doesNotContainKey("ddc_i18n_page");
            assertThat(currentTopology.profiles()).containsKey("ddc_i18n_page");
            assertThat(previousTopology.fingerprint()).isNotEqualTo(currentTopology.fingerprint());
            assertThat(validator.validate(current, loader.load(current)).fingerprint()).isEqualTo(currentTopology.fingerprint());
        }
    }

    @Test void rejectsNativeDistributedAndRedirectedI18nTopologies() throws Exception {
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            ValidationUtils validation = new ValidationUtils(validators.getValidator());
            var environment = environment();
            var loader = loader(environment, validation);
            var validator = new EgonColaShardingTopologyValidator(validation);
            var valid = bind(environment);
            var topology = validator.validate(valid, loader.load(valid));
            var nativeProperties = bind(environment).setConfigStyle(EgonColaShardingProperties.ConfigStyleEnum.NATIVE);
            var distributed = bind(environment).setTransactionDefaultType("XA");
            var redirected = bind(environment);
            redirected.getTables().put("ddc_i18n_page", new EgonColaShardingProperties.TableProperties(
                    "STANDARD_TENANT_ID", null, null, null, null, "other_schema"));
            for (var invalid : List.of(nativeProperties, distributed, redirected)) {
                assertThatThrownBy(() -> DdcPersistenceMigrationCli.priorI18nTopology(invalid, bind(environment), topology, loader, validator))
                        .hasMessageContaining("STANDARD_LOCAL_TOPOLOGY_REQUIRED");
            }
            assertThatThrownBy(() -> DdcPersistenceMigrationCli.priorI18nTopology(valid, valid, topology, loader, validator))
                    .hasMessageContaining("INDEPENDENT_BIND_REQUIRED");
            var changedPrevious = bind(environment);
            changedPrevious.getTables().remove("ddc_biz");
            assertThatThrownBy(() -> DdcPersistenceMigrationCli.priorI18nTopology(valid, changedPrevious, topology, loader, validator))
                    .hasMessageContaining("ADDITIVE_TOPOLOGY_REQUIRED");
        }
    }

    @Test void resultVerificationAcceptsOnlyCompleteLegacyOrDeclaredBaselineSuffix() throws Exception {
        EgonColaDdlManifestBO manifest;
        try (var input = getClass().getClassLoader().getResourceAsStream("db/egon-mp/repository-manifest.json")) {
            manifest = new ObjectMapper().readValue(input, EgonColaDdlManifestBO.class);
        }
        var results = manifest.scripts().stream().map(script -> new EgonColaDdlResult("tianshu_0", "public",
                script.version(), script.sha256(), EgonColaDdlResult.StatusEnum.SKIPPED, Duration.ZERO)).toList();
        assertThatCode(() -> DdcPersistenceMigrationCli.validateTianshuResults(results, manifest, "tianshu_0"))
                .doesNotThrowAnyException();
        assertThatCode(() -> DdcPersistenceMigrationCli.validateTianshuResults(results.subList(3, results.size()), manifest, "tianshu_0"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DdcPersistenceMigrationCli.validateTianshuResults(results.subList(4, 5), manifest, "tianshu_0"))
                .hasMessageContaining("RESULT_INCOMPLETE");
        assertThatThrownBy(() -> DdcPersistenceMigrationCli.validateTianshuResults(results.subList(0, 4), manifest, "tianshu_0"))
                .hasMessageContaining("RESULT_INCOMPLETE");
    }

    private String[] command(Path target) { return new String[]{"upgrade-i18n", "--target-config", target.toAbsolutePath().toString()}; }

    private StandardEnvironment environment() throws Exception {
        var environment = new StandardEnvironment();
        String prefix = EgonColaShardingProperties.PREFIX + ".data-sources[0].";
        environment.getPropertySources().addFirst(new MapPropertySource("fixture", Map.of(
                prefix + "name", "tianshu_0", prefix + "logical-name", "tianshu_0", prefix + "role", "PRIMARY",
                prefix + "driver-class-name", "org.postgresql.Driver",
                prefix + "jdbc-url", "jdbc:postgresql://127.0.0.1:5432/fixture",
                prefix + "username", "fixture", prefix + "password", "fixture")));
        new YamlPropertySourceLoader().load("persistence", new ClassPathResource("egon-tianshu-persistence.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return environment;
    }

    private EgonColaShardingProperties bind(StandardEnvironment environment) {
        return Binder.get(environment).bind(EgonColaShardingProperties.PREFIX,
                Bindable.of(EgonColaShardingProperties.class)).orElseThrow(IllegalStateException::new);
    }

    private EgonColaShardingYamlLoader loader(StandardEnvironment environment, ValidationUtils validation) {
        var factory = new EgonColaShardingStrategyFactory(validation, Map.of("STANDARD_TENANT_ID",
                new EgonColaStandardTenantIdShardingStrategy(), "SINGLE", new EgonColaSingleTableShardingStrategy()));
        return new EgonColaShardingYamlLoader(factory, new DefaultResourceLoader(), environment);
    }
}
