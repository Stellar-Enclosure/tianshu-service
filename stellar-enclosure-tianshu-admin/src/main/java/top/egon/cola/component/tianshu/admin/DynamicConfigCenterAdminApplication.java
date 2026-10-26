package top.egon.cola.component.tianshu.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan(value = "top.egon.cola.component.tianshu.admin.dao", lazyInitialization = "true")
public class DynamicConfigCenterAdminApplication {

    public static void main(String[] args) {
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
