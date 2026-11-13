package br.com.cadeopila.userservice.adapters.out.persistence;

import br.com.cadeopila.userservice.adapters.out.persistence.mapper.UserPersistenceMapper;
import br.com.cadeopila.userservice.core.domain.PageResult;
import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.exception.UserAlreadyExistsException;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserPersistenceAdapter implements UserRepositoryPort {

    private static final String CONSTRAINT_EMAIL = "uq_users_email";
    private static final String CONSTRAINT_CPF   = "uq_users_cpf";

    private final UserJpaRepository jpaRepository;
    private final UserPersistenceMapper mapper;

    public UserPersistenceAdapter(UserJpaRepository jpaRepository,
                                  UserPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        try {
            return mapper.toDomain(jpaRepository.saveAndFlush(mapper.toJpaEntity(user)));
        } catch (DataIntegrityViolationException ex) {
            throw toConflictException(user, ex);
        }
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByCpf(String cpf) {
        return jpaRepository.existsByCpf(cpf);
    }

    @Override
    public boolean existsByEmailAndIdNot(String email, UUID id) {
        return jpaRepository.existsByEmailAndIdNot(email, id);
    }

    @Override
    public PageResult<User> findAll(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        var jpaPage = jpaRepository.findAll(pageable);
        return new PageResult<>(
                jpaPage.getContent().stream().map(mapper::toDomain).toList(),
                jpaPage.getTotalElements(),
                jpaPage.getTotalPages(),
                page,
                size
        );
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private UserAlreadyExistsException toConflictException(User user,
                                                           DataIntegrityViolationException ex) {
        String constraint = extractConstraintName(ex);
        if (CONSTRAINT_EMAIL.equals(constraint)) {
            return new UserAlreadyExistsException("email", user.getEmail());
        }
        if (CONSTRAINT_CPF.equals(constraint)) {
            return new UserAlreadyExistsException("cpf", user.maskedCpf());
        }
        return new UserAlreadyExistsException("campo único", "");
    }

    private String extractConstraintName(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve) {
                return cve.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }
}
