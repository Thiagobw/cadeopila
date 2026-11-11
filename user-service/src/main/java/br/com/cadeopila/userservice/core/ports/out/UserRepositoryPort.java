package br.com.cadeopila.userservice.core.ports.out;

import br.com.cadeopila.userservice.core.domain.PageResult;
import br.com.cadeopila.userservice.core.domain.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(UUID id);

    boolean existsById(UUID id);

    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);

    boolean existsByEmailAndIdNot(String email, UUID id);

    PageResult<User> findAll(int page, int size);

    void deleteById(UUID id);
}
