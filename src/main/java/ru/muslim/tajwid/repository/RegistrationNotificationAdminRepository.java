package ru.muslim.tajwid.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.muslim.tajwid.domain.RegistrationNotificationAdminEntity;

public interface RegistrationNotificationAdminRepository extends JpaRepository<RegistrationNotificationAdminEntity, Long> {

    List<RegistrationNotificationAdminEntity> findByActiveTrue();

    Optional<RegistrationNotificationAdminEntity> findByTelegramUsernameIgnoreCase(String telegramUsername);
}
