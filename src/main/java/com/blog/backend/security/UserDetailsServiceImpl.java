package com.blog.backend.security;

import com.blog.backend.entity.User;
import com.blog.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userService.getByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        // Spring Security 需要 ROLE_ 前缀来匹配 hasRole("ADMIN") 这类规则。
        String role = user.getRole() == null ? "USER" : user.getRole();
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getStatus() == null || user.getStatus() != 0,
                true,
                true,
                user.getLockedUntil() == null || user.getLockedUntil().isBefore(java.time.LocalDateTime.now()),
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)));
    }
}
