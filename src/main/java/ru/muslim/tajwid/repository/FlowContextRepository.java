package ru.muslim.tajwid.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.muslim.tajwid.domain.FlowContextEntity;

public interface FlowContextRepository extends JpaRepository<FlowContextEntity, Long> {

    Optional<FlowContextEntity> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
