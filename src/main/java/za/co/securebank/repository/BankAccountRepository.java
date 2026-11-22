package za.co.securebank.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.securebank.entity.BankAccount;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    Optional<BankAccount> findByIdAndOwnerId(Long id, Long ownerId);

    List<BankAccount> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    long countByOwnerId(Long ownerId);

    boolean existsByAccountNumber(String accountNumber);

    @Query("select a.id from BankAccount a where a.id = :id and a.owner.id = :ownerId")
    Optional<Long> findIdByIdAndOwnerId(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Query("select a.id from BankAccount a where a.accountNumber = :accountNumber")
    Optional<Long> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

    @Query("select a.id from BankAccount a where a.owner.id = :ownerId")
    List<Long> findIdsByOwnerId(@Param("ownerId") Long ownerId);

    /** Row level write lock (SELECT ... FOR UPDATE). Used by every operation that changes a balance. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BankAccount a where a.id = :id")
    Optional<BankAccount> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BankAccount a where a.id = :id and a.owner.id = :ownerId")
    Optional<BankAccount> findByIdAndOwnerIdForUpdate(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Override
    @EntityGraph(attributePaths = "owner")
    Page<BankAccount> findAll(Pageable pageable);
}
