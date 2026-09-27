package io.github.canaritel.mitusalud.diario.repository;

import io.github.canaritel.mitusalud.diario.entity.Energia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Acceso a los registros de energía (tablas observation + energia).
 * Al consultar Energia, Hibernate une solo con la tabla energia: nunca devuelve pesos ni agua.
 */
public interface EnergiaRepository extends JpaRepository<Energia, UUID> {

    // ORDER BY observed_at DESC, id DESC, igual que en el peso y el agua.
    List<Energia> findAllByOrderByObservadoEnDescIdDesc();
}
