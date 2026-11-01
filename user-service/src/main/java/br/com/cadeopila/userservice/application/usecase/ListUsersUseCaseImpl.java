package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.domain.PageResult;
import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.ports.in.ListUsersUseCase;
import br.com.cadeopila.userservice.core.ports.out.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListUsersUseCaseImpl implements ListUsersUseCase {

    private final UserRepositoryPort userRepository;

    public ListUsersUseCaseImpl(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<User> list(int page, int size) {
        int cappedSize = Math.min(Math.max(size, 1), 100);
        return userRepository.findAll(page, cappedSize);
    }
}
