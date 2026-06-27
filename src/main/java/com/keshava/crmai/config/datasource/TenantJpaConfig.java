package com.keshava.crmai.config.datasource;

import com.keshava.crmai.multitenancy.TenantDataSourceRouter;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Properties;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {
                "com.keshava.crmai.security.repository",
                "com.keshava.crmai.crm",
                "com.keshava.crmai.module.repository"
        },
        entityManagerFactoryRef = "tenantEntityManagerFactory",
        transactionManagerRef = "tenantTransactionManager"
)
public class TenantJpaConfig {

    @Bean(name = "tenantDataSourceRouter")
    public TenantDataSourceRouter tenantDataSourceRouter() {
        return new TenantDataSourceRouter();
    }

    @Bean(name = "tenantEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(
            @Qualifier("tenantDataSourceRouter") TenantDataSourceRouter router) {

        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(router);
        emf.setPackagesToScan(
                "com.keshava.crmai.security.entity",
                "com.keshava.crmai.crm.contact.entity",
                "com.keshava.crmai.crm.ticket.entity",
                "com.keshava.crmai.module.entity",
                "com.keshava.crmai.common.entity"
        );
        emf.setPersistenceUnitName("tenant");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        emf.setJpaVendorAdapter(adapter);
        emf.setJpaProperties(hibernateProperties());

        return emf;
    }

    @Bean(name = "tenantTransactionManager")
    public PlatformTransactionManager tenantTransactionManager(
            @Qualifier("tenantEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean(name = "tenantJdbcTemplate")
    public JdbcTemplate tenantJdbcTemplate(
            @Qualifier("tenantDataSourceRouter") TenantDataSourceRouter router) {
        return new JdbcTemplate(router);
    }

    @Bean(name = "tenantNamedJdbcTemplate")
    public NamedParameterJdbcTemplate tenantNamedJdbcTemplate(
            @Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private Properties hibernateProperties() {
        Properties props = new Properties();
        // Router starts empty — disable JDBC metadata probe so Hibernate doesn't attempt
        // a connection at startup. Dialect must be explicit when metadata access is off.
        props.setProperty("hibernate.boot.allow_jdbc_metadata_access", "false");
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.setProperty("hibernate.hbm2ddl.auto", "none");
        props.setProperty("hibernate.show_sql", "true");
        props.setProperty("hibernate.format_sql", "true");
        props.setProperty("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        return props;
    }
}
