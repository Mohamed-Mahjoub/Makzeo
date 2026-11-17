package ma.pfe.stock.auth.infrastructure.persistence;

import ma.pfe.stock.auth.domain.model.AppSettings;
import ma.pfe.stock.auth.domain.port.SettingsRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SettingsRepositoryAdapter implements SettingsRepository {

    private final SettingsJpaRepository jpa;

    public SettingsRepositoryAdapter(SettingsJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<AppSettings> load() {
        return jpa.findById(SettingsEntity.SINGLETON_ID)
                .map(e -> new AppSettings(e.getCurrency(), e.getUpdatedAt(), e.getUpdatedBy()));
    }

    @Override
    public AppSettings save(AppSettings settings) {
        SettingsEntity e = jpa.findById(SettingsEntity.SINGLETON_ID).orElseGet(SettingsEntity::new);
        e.setCurrency(settings.getCurrency().getCurrencyCode());
        e.setUpdatedAt(settings.getUpdatedAt());
        e.setUpdatedBy(settings.getUpdatedBy());
        jpa.save(e);
        return settings;
    }
}
