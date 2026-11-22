package za.co.securebank.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.securebank.entity.Role;
import za.co.securebank.enums.RoleName;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
