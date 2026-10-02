package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.domain.Favorite;
import it.fn.redfish.catalog.repo.ApiServiceRepository;
import it.fn.redfish.catalog.repo.AppUserRepository;
import it.fn.redfish.catalog.repo.FavoriteRepository;
import it.fn.redfish.catalog.support.NotFoundException;

@Service
public class FavoriteService {

    private final FavoriteRepository favorites;
    private final AppUserRepository users;
    private final ApiServiceRepository services;

    public FavoriteService(FavoriteRepository favorites, AppUserRepository users, ApiServiceRepository services) {
        this.favorites = favorites;
        this.users = users;
        this.services = services;
    }

    @Transactional(readOnly = true)
    public List<ApiService> listFor(Long userId) {
        return favorites.findByUser(userId).stream().map(Favorite::getService).toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> idsFor(Long userId) {
        return userId == null ? Set.of() : Set.copyOf(favorites.findServiceIdsByUser(userId));
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long serviceId) {
        return userId != null && favorites.existsById(new Favorite.Key(userId, serviceId));
    }

    @Transactional
    public boolean toggle(Long userId, Long serviceId) {
        Favorite.Key key = new Favorite.Key(userId, serviceId);
        if (favorites.existsById(key)) {
            favorites.deleteById(key);
            return false;
        }
        var user = users.findById(userId).orElseThrow(() -> NotFoundException.of(I18n.text("ui.user"), userId));
        var service = services.findById(serviceId).orElseThrow(() -> NotFoundException.of(I18n.text("ui.service"), serviceId));
        favorites.save(new Favorite(user, service));
        return true;
    }
}
