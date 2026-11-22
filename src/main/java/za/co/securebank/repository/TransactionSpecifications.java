package za.co.securebank.repository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import za.co.securebank.entity.BankAccount;
import za.co.securebank.entity.Transaction;
import za.co.securebank.enums.TransactionType;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /**
     * Builds a transaction filter. Any argument may be null, meaning "no restriction".
     *
     * @param visibleAccountIds restricts results to transactions touching one of these accounts (must not be empty)
     * @param accountId         restricts results to transactions touching this single account
     * @param type              transaction type
     * @param fromInclusive     earliest creation instant (inclusive)
     * @param toExclusive       latest creation instant (exclusive)
     */
    public static Specification<Transaction> filter(Collection<Long> visibleAccountIds, Long accountId,
                                                    TransactionType type, Instant fromInclusive, Instant toExclusive) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Transaction, BankAccount> source = root.join("sourceAccount", JoinType.LEFT);
            Join<Transaction, BankAccount> destination = root.join("destinationAccount", JoinType.LEFT);

            if (visibleAccountIds != null) {
                predicates.add(cb.or(source.get("id").in(visibleAccountIds), destination.get("id").in(visibleAccountIds)));
            }
            if (accountId != null) {
                predicates.add(cb.or(cb.equal(source.get("id"), accountId), cb.equal(destination.get("id"), accountId)));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (fromInclusive != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), fromInclusive));
            }
            if (toExclusive != null) {
                predicates.add(cb.lessThan(root.<Instant>get("createdAt"), toExclusive));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
