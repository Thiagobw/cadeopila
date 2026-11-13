package br.com.cadeopila.transactionservice.adapters.in.web.mapper;

import br.com.cadeopila.transactionservice.adapters.in.web.dto.AccountBalanceResponse;
import br.com.cadeopila.transactionservice.core.ports.in.GetAccountBalanceUseCase;
import org.springframework.stereotype.Component;

@Component
public class AccountWebMapper {

    public AccountBalanceResponse toResponse(GetAccountBalanceUseCase.Balance balance) {
        return new AccountBalanceResponse(
                balance.balance(),
                balance.dailyLimit(),
                balance.transactionLimit(),
                balance.spentToday(),
                balance.availableToday());
    }
}
