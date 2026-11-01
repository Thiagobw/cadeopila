package br.com.cadeopila.userservice.adapters.out.persistence.flyway;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;

public class FlywayMigrationInitializer implements InitializingBean {

    private final Flyway flyway;

    public FlywayMigrationInitializer(Flyway flyway) {
        this.flyway = flyway;
    }

    @Override
    public void afterPropertiesSet() {
        flyway.migrate();
    }
}
