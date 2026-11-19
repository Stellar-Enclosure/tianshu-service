package top.egon.cola.component.tianshu.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan(value = "top.egon.cola.component.tianshu.admin.dao", lazyInitialization = "true")
public class DynamicConfigCenterAdminApplication {

    public static void main(String[] args) {
        if (args.length > 0 && ("--config-publisher".equals(args[0]) || "--config-observer".equals(args[0]))) {
            String[] workerArguments = java.util.Arrays.copyOfRange(args, 1, args.length);
            for (String argument : workerArguments) {
                if (argument.startsWith("--config-")) {
                    throw new IllegalArgumentException("TIANSHU_CONFIG_WORKER_MODE_INVALID");
                }
            }
            top.egon.cola.component.tianshu.admin.config.DdcConfigWorkerApplicationConfiguration
                    .application(args[0].substring(2)).run(workerArguments);
            return;
        }
        for (String argument : args) {
            if (argument.startsWith("--config-")) {
                throw new IllegalArgumentException("TIANSHU_CONFIG_WORKER_MODE_INVALID");
            }
        }
        if (args.length > 0 && "bootstrap-permission-metadata".equals(args[0])) {
            top.egon.cola.component.tianshu.admin.bootstrap.DdcPermissionMetadataBootstrapCli.run(java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length > 0 && args[0].startsWith("bootstrap-permission-")) {
            throw new IllegalArgumentException("Unknown explicit permission bootstrap command");
        }
        SpringApplication.run(DynamicConfigCenterAdminApplication.class, args);
    }
}
