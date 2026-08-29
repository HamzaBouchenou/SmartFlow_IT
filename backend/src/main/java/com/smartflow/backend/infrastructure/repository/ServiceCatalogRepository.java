package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.ServiceCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalog, Long> {

    // §6.2 - "Recherche par mot-clé, catégorie ou service responsable" : les trois filtres
    // sont optionnels et combinables (chaque `:x is null` laisse passer la ligne quand
    // l'appelant n'a pas fourni ce filtre) ; seules les fiches actives sont catalogue
    // ("Activation, désactivation... des types de demande"). displayOrder porte l'ordre
    // d'affichage configuré, jamais un tri alphabétique ou par id.
    // `cast(:keyword as string)` : sans lui, Hibernate/le pilote JDBC PostgreSQL n'ont
    // aucune information de type pour lier un :keyword null utilisé à la fois dans
    // "is null" et dans concat(...) - PostgreSQL retombe alors sur bytea pour ce paramètre
    // et lower(bytea) échoue en base ("function lower(bytea) does not exist"). Le cast
    // force le type text, sans changer la sémantique : un keyword non null se comporte
    // toujours comme une chaîne, RG "recherche par mot-clé" (§6.2) inchangée.
    @Query("""
            select sc from ServiceCatalog sc
            where sc.active = true
              and (:keyword is null
                   or lower(sc.name) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(sc.description) like lower(concat('%', cast(:keyword as string), '%')))
              and (:category is null or sc.category = :category)
              and (:departmentId is null or sc.department.id = :departmentId)
            order by sc.displayOrder asc
            """)
    List<ServiceCatalog> search(@Param("keyword") String keyword, @Param("category") String category,
                                 @Param("departmentId") Long departmentId);
}
