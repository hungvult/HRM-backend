package com.hrm.backend.security;

import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.AccountRole;
import com.hrm.backend.repository.AccountRepository;
import com.hrm.backend.repository.AccountRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username or email: " + username));

        List<String> roles = accountRoleRepository.findByAccountIdWithRole(account.getId()).stream()
                .map(AccountRole::getRole)
                .map(com.hrm.backend.entity.Role::getCode)
                .toList();

        return new CustomUserDetails(account, roles);
    }
}
