package io.github.canaritel.mitusalud.diario.repository;

import io.github.canaritel.mitusalud.diario.entity.Peso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a la tabla {@code peso}.
 * Es solo una interfaz: Spring Data genera la implementación al arrancar.
 * JpaRepository ya trae save, findById, findAll, delete...
 */
public interface PesoRepository extends JpaRepository<Peso, Long> {

    // Spring Data deduce la consulta a partir del nombre del método:
    // SELECT ... FROM peso ORDER BY fecha DESC, id DESC
    List<Peso> findAllByOrderByFechaDescIdDesc();
}
