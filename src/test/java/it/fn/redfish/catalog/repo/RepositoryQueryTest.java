package it.fn.redfish.catalog.repo;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.Entity;
import java.util.List;

class RepositoryQueryTest {
    @Test
    void validaLeQueryDiRicercaEDashboardConLeEntitaReali() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try {
            var metadata = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var entity : scanner.findCandidateComponents("it.fn.redfish.catalog.domain")) {
                metadata.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
            }
            try (var factory = metadata.buildMetadata().buildSessionFactory(); var session = factory.openSession()) {
                for (Class<?> repo : List.of(ApiServiceRepository.class, ApiEndpointRepository.class,
                        ApiModelRepository.class, ApiGroupRepository.class, TagRepository.class)) {
                    for (var method : repo.getDeclaredMethods()) {
                        Query query = method.getAnnotation(Query.class);
                        if (query != null && !query.nativeQuery()) {
                            session.createQuery(query.value(), Object.class);
                        }
                    }
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
