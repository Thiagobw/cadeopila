package br.com.cadeopila.userservice.core.ports.in;

import br.com.cadeopila.userservice.core.domain.PageQuery;
import br.com.cadeopila.userservice.core.domain.PageResult;
import br.com.cadeopila.userservice.core.domain.User;

public interface ListUsersUseCase {

    PageResult<User> list(PageQuery query);
}
