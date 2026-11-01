package br.com.cadeopila.userservice.adapters.out.persistence.flyway;

import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class FlywayConfig {

    @Bean
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
    }

    @Bean
    public FlywayMigrationInitializer flywayMigrationInitializer(Flyway flyway) {
        return new FlywayMigrationInitializer(flyway);
    }

    /**
     * Registers flywayMigrationInitializer as a dependency of entityManagerFactory so
     * Flyway migrations run before Hibernate validates/creates the schema.
     * Using static @Bean so Spring instantiates this BDRP before other singletons,
     * without triggering early DataSource creation.
     */
    @Bean
    public static BeanDefinitionRegistryPostProcessor flywayJpaOrderingConfigurer() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
                    throws BeansException {
                try {
                    BeanDefinition emfDef = registry.getBeanDefinition("entityManagerFactory");
                    String[] existing = emfDef.getDependsOn();
                    List<String> deps = new ArrayList<>(
                            existing != null ? Arrays.asList(existing) : Collections.emptyList());
                    if (!deps.contains("flywayMigrationInitializer")) {
                        deps.add("flywayMigrationInitializer");
                        emfDef.setDependsOn(deps.toArray(new String[0]));
                    }
                } catch (NoSuchBeanDefinitionException ignored) {
                }
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
                    throws BeansException {
            }
        };
    }
}
