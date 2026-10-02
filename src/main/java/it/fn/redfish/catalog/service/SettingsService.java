package it.fn.redfish.catalog.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fn.redfish.catalog.config.CatalogProperties;
import it.fn.redfish.catalog.domain.AppSetting;
import it.fn.redfish.catalog.repo.AppSettingRepository;

@Service
public class SettingsService {

    public static final String SETUP_COMPLETED = "setup.completed";
    public static final String APP_NAME = "app.name";
    public static final String APP_LOGO_PATH = "app.logo.path";
    public static final String UPLOAD_MAX_BYTES = "upload.max.bytes";
    public static final String APITEST_TIMEOUT_SECONDS = "apitest.timeout.seconds";
    public static final String HEALTHCHECK_INTERVAL_MINUTES = "healthcheck.interval.minutes";

    private final AppSettingRepository repository;
    private final CatalogProperties properties;

    private volatile Map<String, String> cache = Map.of();
    private volatile boolean loaded;

    public SettingsService(AppSettingRepository repository, CatalogProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    private synchronized void loadIfNeeded() {
        if (loaded) {
            return;
        }
        reload();
    }

    @Transactional(readOnly = true)
    public synchronized void reload() {
        Map<String, String> fresh = new LinkedHashMap<>();
        for (AppSetting s : repository.findAll()) {
            if (s.getValue() != null) {
                fresh.put(s.getKey(), s.getValue());
            }
        }
        cache = Map.copyOf(fresh);
        loaded = true;
    }

    public String get(String key, String defaultValue) {
        loadIfNeeded();
        String v = cache.get(key);
        return v == null || v.isBlank() ? defaultValue : v;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, Boolean.toString(defaultValue)));
    }

    public long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(get(key, Long.toString(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Transactional
    public void put(String key, String value) {
        AppSetting setting = repository.findById(key).orElseGet(() -> new AppSetting(key, null));
        setting.setValue(value);
        repository.save(setting);
        invalidate();
    }

    private void invalidate() {
        loaded = false;
    }

    @Transactional
    public void putAll(Map<String, String> values) {
        values.forEach(this::put);
    }

    public boolean isSetupCompleted() {
        return getBoolean(SETUP_COMPLETED, false);
    }

    public String appName() {
        return get(APP_NAME, "API Catalog");
    }

    public String logoPath() {
        return get(APP_LOGO_PATH, "");
    }

    public long maxUploadBytes() {
        return getLong(UPLOAD_MAX_BYTES, properties.getMaxUploadBytes());
    }

    public Duration apiTestTimeout() {
        return Duration.ofSeconds(getLong(APITEST_TIMEOUT_SECONDS, properties.getApiTestTimeout().toSeconds()));
    }

    public long healthCheckIntervalMinutes() {
        return getLong(HEALTHCHECK_INTERVAL_MINUTES, 10);
    }

    public Map<String, String> snapshot() {
        loadIfNeeded();
        return new LinkedHashMap<>(cache);
    }
}
