package dev.hieunv.riskassessment.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateSettings;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Nguồn dữ liệu CHÍNH — Postgres của PCRT. Đây là DB duy nhất PCRT được phép ghi.
 *
 * <h2>Vì sao phải khai báo tay, trong khi trước đây Spring Boot tự lo</h2>
 * Khoảnh khắc trong context có <b>hai</b> bean {@link DataSource}, mọi auto-configuration của
 * Boot đứng hình: {@code DataSourceAutoConfiguration} mang {@code @ConditionalOnMissingBean},
 * còn {@code JpaBaseConfiguration.entityManagerFactory} mang
 * {@code @ConditionalOnMissingBean(LocalContainerEntityManagerFactoryBean.class)}. Chỉ cần
 * {@link CoreDataSourceConfig} khai một EMF cho Core là EMF của PCRT biến mất — không có lỗi
 * lúc biên dịch, chỉ có {@code NoSuchBeanDefinitionException} lúc khởi động.
 * <p>
 * Nên khi đã có nguồn thứ hai thì <b>cả hai</b> phải khai tay. Đổi lại, {@link Primary} ở đây
 * quyết định bean nào được tiêm khi chỗ gọi không nói rõ — và mọi chỗ gọi hiện có đều không
 * nói rõ: {@code JdbcTemplate}, {@code @Transactional} không tham số, Flyway, Spring Session…
 * tất cả đi vào Postgres như cũ.
 *
 * <h2>Ranh giới quét repository</h2>
 * {@code basePackages} liệt kê tường minh chứ không lấy cả gói gốc: package
 * {@code core.repository} phải thuộc về EMF của Core. Nếu để Spring quét nhầm một repository
 * của Core vào EMF này, lỗi báo ra sẽ là "Not a managed type" — đúng nhưng khó lần ra nguyên nhân.
 */
@Configuration
@EnableConfigurationProperties({JpaProperties.class, HibernateProperties.class})
@EnableJpaRepositories(
        basePackages = "dev.hieunv.riskassessment.repository",
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager")
public class PcrtDataSourceConfig {

    /**
     * Dịch {@code spring.jpa.*} thành các thuộc tính Hibernate thật.
     *
     * <h2>Vì sao không bỏ qua bước này</h2>
     * Khi EMF được khai tay, {@code HibernateJpaConfiguration} lùi lại — và cùng với nó biến
     * mất hai thứ không ai nhớ là do Boot đặt: {@code ddl-auto} (mất hàng rào validate schema)
     * và <b>naming strategy</b> ({@code CamelCaseToUnderscoresNamingStrategy}). Mất cái thứ hai
     * thì mọi trường không có {@code @Column} tường minh đổi tên cột từ {@code scan_batch_id}
     * sang {@code scanBatchId}, và ứng dụng chết lúc khởi động với một thông báo không nói gì
     * về nguyên nhân thật.
     * <p>
     * Gọi lại đúng hàm mà Boot vẫn gọi là cách chắc chắn nhất để không bỏ sót thứ nào.
     */
    static Map<String, Object> hibernatePropertiesOf(JpaProperties jpaProperties,
                                                     HibernateProperties hibernateProperties) {
        return hibernateProperties.determineHibernateProperties(
                jpaProperties.getProperties(), new HibernateSettings());
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("dataSource") DataSource dataSource,
            JpaProperties jpaProperties,
            HibernateProperties hibernateProperties) {
        return builder.dataSource(dataSource)
                .packages("dev.hieunv.riskassessment.entity")
                .persistenceUnit("pcrt")
                .properties(hibernatePropertiesOf(jpaProperties, hibernateProperties))
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
