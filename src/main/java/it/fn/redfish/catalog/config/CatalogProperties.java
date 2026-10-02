package it.fn.redfish.catalog.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog")
public class CatalogProperties {

    private Path storageDir = Path.of("data", "storage");

    private long maxUploadBytes = 10L * 1024 * 1024;

    private Duration apiTestTimeout = Duration.ofSeconds(30);

    private Duration importFetchTimeout = Duration.ofSeconds(20);

    private boolean healthCheckEnabled = true;

    public Path getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(Path storageDir) {
        this.storageDir = storageDir;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }

    public Duration getApiTestTimeout() {
        return apiTestTimeout;
    }

    public void setApiTestTimeout(Duration apiTestTimeout) {
        this.apiTestTimeout = apiTestTimeout;
    }

    public Duration getImportFetchTimeout() {
        return importFetchTimeout;
    }

    public void setImportFetchTimeout(Duration importFetchTimeout) {
        this.importFetchTimeout = importFetchTimeout;
    }

    public boolean isHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    public void setHealthCheckEnabled(boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
    }
}
