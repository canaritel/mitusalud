package io.github.canaritel.mitusalud.diario.repository;

import io.github.canaritel.mitusalud.diario.entity.Agua;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a los registros de agua (tablas observation + agua).
 * Al consultar Agua, Hibernate une solo con la tabla agua: nunca devuelve pesos.
 */
public interface AguaRepository extends JpaRepository<Agua, UUID> {

    // ORDER BY observed_at DESC, id DESC, igual que en el peso.
    List<Agua> findAllByOrderByObservadoEnDescIdDesc();

    // El agua creada con esa clave de idempotencia, si existe.
    Optional<Agua> findByClaveIdempotencia(UUID claveIdempotencia);
}
