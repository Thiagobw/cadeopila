package br.com.cadeopila.userservice.adapters.out.persistence.mapper;

import br.com.cadeopila.userservice.adapters.out.persistence.UserJpaEntity;
import br.com.cadeopila.userservice.core.domain.User;
import org.springframework.stereotype.Component;

@Component
public class UserPersistenceMapper {

    public User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getCpf(),
                entity.getPasswordHash(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public UserJpaEntity toJpaEntity(User user) {
        var entity = new UserJpaEntity();
        if (user.getId() != null) {
            entity.setId(user.getId());
        }
        entity.setName(user.getName());
        entity.setEmail(user.getEmail());
        entity.setCpf(user.getCpf());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        return entity;
    }
}
