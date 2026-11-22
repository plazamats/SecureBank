package za.co.securebank.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.securebank.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    @Override
    @EntityGraph(attributePaths = {"sourceAccount", "destinationAccount"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    @Query("select t from Transaction t "
            + "left join fetch t.sourceAccount sa "
            + "left join fetch t.destinationAccount da "
            + "where t.id = :id and (sa.owner.id = :userId or da.owner.id = :userId)")
    Optional<Transaction> findByIdVisibleToUser(@Param("id") Long id, @Param("userId") Long userId);
}
