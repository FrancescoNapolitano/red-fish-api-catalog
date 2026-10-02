package it.fn.redfish.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import it.fn.redfish.catalog.config.CatalogProperties;

@SpringBootApplication
@EnableConfigurationProperties(CatalogProperties.class)
@EnableScheduling
@EnableAsync
public class ApiCatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiCatalogApplication.class, args);
    }
}
