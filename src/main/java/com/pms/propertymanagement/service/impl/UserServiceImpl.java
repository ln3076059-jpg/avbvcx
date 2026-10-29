package com.pms.propertymanagement.service.impl;

import com.pms.propertymanagement.entity.User;
import com.pms.propertymanagement.repository.UserRepository;
import com.pms.propertymanagement.service.UserService;
import com.pms.propertymanagement.utils.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User authenticate(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        Optional<User> userOpt = userRepository.findByUsername(username.trim());
        if (userOpt.isEmpty()) {
            return null;
        }
        User user = userOpt.get();
        if (!user.isActive() || user.getPassword() == null) {
            return null;
        }
        if (!PasswordUtil.matches(password, user.getPassword())) {
            return null;
        }
        // Transparently upgrade legacy plaintext password to PBKDF2-HMAC-SHA256 hash
        if (!PasswordUtil.isEncoded(user.getPassword())) {
            user.setPassword(PasswordUtil.encode(password));
            userRepository.save(user);
        }
        return user;
    }

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public java.util.List<User> getUsersByRole(String roleName) {
        return userRepository.findByRoles_Name(roleName);
    }
}
