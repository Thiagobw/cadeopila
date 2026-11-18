package br.com.cadeopila.userservice.adapters.in.web;

import br.com.cadeopila.userservice.adapters.in.web.dto.CreateUserRequest;
import br.com.cadeopila.userservice.adapters.in.web.dto.PagedResponse;
import br.com.cadeopila.userservice.adapters.in.web.dto.UpdateUserRequest;
import br.com.cadeopila.userservice.adapters.in.web.dto.UserResponse;
import br.com.cadeopila.userservice.adapters.in.web.mapper.UserWebMapper;
import br.com.cadeopila.userservice.adapters.in.web.security.AuthenticatedUser;
import br.com.cadeopila.userservice.core.domain.PageQuery;
import br.com.cadeopila.userservice.core.ports.in.CreateUserUseCase;
import br.com.cadeopila.userservice.core.ports.in.DeleteUserUseCase;
import br.com.cadeopila.userservice.core.ports.in.FindUserByIdUseCase;
import br.com.cadeopila.userservice.core.ports.in.ListUsersUseCase;
import br.com.cadeopila.userservice.core.ports.in.UpdateUserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Validated
@Tag(name = "Usuários", description = "Gerenciamento de perfis de usuário")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final CreateUserUseCase createUser;
    private final FindUserByIdUseCase findUserById;
    private final ListUsersUseCase listUsers;
    private final UpdateUserUseCase updateUser;
    private final DeleteUserUseCase deleteUser;
    private final UserWebMapper mapper;

    public UserController(CreateUserUseCase createUser,
                          FindUserByIdUseCase findUserById,
                          ListUsersUseCase listUsers,
                          UpdateUserUseCase updateUser,
                          DeleteUserUseCase deleteUser,
                          UserWebMapper mapper) {
        this.createUser = createUser;
        this.findUserById = findUserById;
        this.listUsers = listUsers;
        this.updateUser = updateUser;
        this.deleteUser = deleteUser;
        this.mapper = mapper;
    }

    @Operation(summary = "Criar usuário")
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
                                               UriComponentsBuilder uriBuilder) {
        var user = createUser.create(mapper.toCommand(request));
        var location = uriBuilder.path("/api/v1/users/{id}").buildAndExpand(user.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(user));
    }

    @Operation(summary = "Listar usuários (paginado, máx. " + PageQuery.MAX_SIZE + " por página)")
    @GetMapping
    public ResponseEntity<PagedResponse<UserResponse>> list(
            @Parameter(description = "Página (base 0). Padrão 0.", example = "0")
            @RequestParam(defaultValue = "0")
            @PositiveOrZero(message = "deve ser maior ou igual a 0") int page,
            @Parameter(description = "Tamanho da página (1 a " + PageQuery.MAX_SIZE + "). Padrão 20.", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "deve ser maior ou igual a 1")
            @Max(value = PageQuery.MAX_SIZE, message = "deve ser no máximo " + PageQuery.MAX_SIZE) int size) {
        var result = listUsers.list(new PageQuery(page, size));
        var content = result.content().stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(new PagedResponse<>(content, result.totalElements(),
                result.totalPages(), result.pageNumber(), result.pageSize()));
    }

    @Operation(summary = "Buscar usuário por ID (apenas a própria conta)")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(
            @Parameter(description = "ID (UUID) do usuário. Deve ser o do próprio usuário autenticado.",
                    example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        requireOwnership(id, principal);
        return ResponseEntity.ok(mapper.toResponse(findUserById.findById(id)));
    }

    @Operation(summary = "Atualizar usuário (substituição completa, apenas a própria conta)")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @Parameter(description = "ID (UUID) do usuário. Deve ser o do próprio usuário autenticado.",
                    example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateUserRequest request) {
        requireOwnership(id, principal);
        var updated = updateUser.update(mapper.toCommand(id, request));
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @Operation(summary = "Excluir usuário (apenas a própria conta)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID (UUID) do usuário. Deve ser o do próprio usuário autenticado.",
                    example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        requireOwnership(id, principal);
        deleteUser.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Autorização em nível de objeto: o dono do recurso vem do principal autenticado (Basic Auth), nunca do
     * path. Impede que um usuário autenticado troque o {@code id} da URL e leia, altere ou exclua a conta de
     * outro (IDOR). Sem principal do tipo esperado (ex.: não autenticado) também nega.
     */
    private static void requireOwnership(UUID targetId, AuthenticatedUser principal) {
        if (principal == null || !principal.getId().equals(targetId)) {
            throw new ForbiddenOperationException("Operação permitida apenas na própria conta.");
        }
    }
}
