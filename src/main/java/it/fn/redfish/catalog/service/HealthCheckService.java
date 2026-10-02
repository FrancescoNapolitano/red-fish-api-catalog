package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fn.redfish.catalog.config.CatalogProperties;
import it.fn.redfish.catalog.domain.HealthStatus;
import it.fn.redfish.catalog.domain.ServiceEnvironment;
import it.fn.redfish.catalog.repo.ServiceEnvironmentRepository;
import it.fn.redfish.catalog.support.Text;

@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final ServiceEnvironmentRepository environments;
    private final CatalogProperties properties;
    private final SettingsService settings;
    private final HttpClient client;

    public HealthCheckService(ServiceEnvironmentRepository environments, CatalogProperties properties,
                              SettingsService settings) {
        this.environments = environments;
        this.properties = properties;
        this.settings = settings;
        this.client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void scheduledCheck() {
        if (!properties.isHealthCheckEnabled() || !settings.isSetupCompleted()) {
            return;
        }
        Duration interval = Duration.ofMinutes(Math.max(1, settings.healthCheckIntervalMinutes()));
        Instant threshold = Instant.now().minus(interval);
        List<ServiceEnvironment> due = environments.findMonitored().stream()
                .filter(e -> e.getHealthCheckedAt() == null || e.getHealthCheckedAt().isBefore(threshold))
                .toList();
        due.forEach(this::check);
    }

    @Transactional
    public ServiceEnvironment check(ServiceEnvironment environment) {
        ServiceEnvironment managed = environments.findById(environment.getId()).orElse(null);
        if (managed == null) {
            return environment;
        }
        String url = managed.getHealthCheckUrl();
        if (url == null || url.isBlank()) {
            managed.setHealthStatus(HealthStatus.UNKNOWN);
            managed.setHealthDetail(null);
            managed.setHealthCheckedAt(Instant.now());
            return environments.save(managed);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "red-fish-api-catalog/healthcheck")
                    .GET()
                    .build();
            long started = System.nanoTime();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();

            boolean up = response.statusCode() >= 200 && response.statusCode() < 400;
            managed.setHealthStatus(up ? HealthStatus.UP : HealthStatus.DOWN);
            managed.setHealthDetail("HTTP " + response.statusCode() + I18n.text("message.in") + millis + " ms");
        } catch (IllegalArgumentException e) {
            managed.setHealthStatus(HealthStatus.DOWN);
            managed.setHealthDetail(I18n.text("message.invalid.url.2"));
        } catch (IOException e) {
            managed.setHealthStatus(HealthStatus.DOWN);
            managed.setHealthDetail(Text.truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 500));
            log.debug(I18n.text("message.health.check.failed.for"), url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            managed.setHealthStatus(HealthStatus.UNKNOWN);
            managed.setHealthDetail(I18n.text("message.check.interrupted"));
        }
        managed.setHealthCheckedAt(Instant.now());
        return environments.save(managed);
    }

    @Transactional
    public void checkService(Long serviceId) {
        environments.findByServiceIdOrderBySortOrderAscNameAsc(serviceId).stream()
                .filter(ServiceEnvironment::isMonitored)
                .forEach(this::check);
    }

}
