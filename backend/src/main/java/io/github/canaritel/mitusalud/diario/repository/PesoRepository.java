package io.github.canaritel.mitusalud.diario.repository;

import io.github.canaritel.mitusalud.diario.entity.Peso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Acceso a los pesos (tablas observation + peso).
 * Es solo una interfaz: Spring Data genera la implementación al arrancar.
 * JpaRepository ya trae save, findById, findAll, delete...
 */
public interface PesoRepository extends JpaRepository<Peso, UUID> {

    // Spring Data deduce la consulta a partir del nombre del método. Hibernate une las dos tablas:
    // SELECT ... FROM peso JOIN observation ... ORDER BY observed_at DESC, id DESC
    // El id solo desempata registros con el mismo instante, siempre en el mismo orden.
    List<Peso> findAllByOrderByObservadoEnDescIdDesc();
}
