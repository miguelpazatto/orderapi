package com.miguelpazatto.orderapi.unit.auth.entities;

import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.auth.entities.enums.UserRole;
import com.miguelpazatto.orderapi.core.exceptions.BusinessRuleException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class UserTest {
    @Test
    void shouldManageActivationAndAuthorities() {
        User user = new User("user@test.com", "encoded", Set.of(UserRole.ADMIN, UserRole.CUSTOMER));
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getAuthorities()).extracting("authority").containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_CUSTOMER");
        user.deactivate();
        assertThat(user.isEnabled()).isFalse();
        user.activate();
        assertThat(user.isEnabled()).isTrue();
        user.updatePassword("new-encoded-password");
        assertThat(user.getPassword()).isEqualTo("new-encoded-password");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void shouldRejectBlankPassword(String password) {
        User user = new User("user@test.com", "original", Set.of(UserRole.CUSTOMER));
        assertThatThrownBy(() -> user.updatePassword(password)).isInstanceOf(BusinessRuleException.class);
        assertThat(user.getPassword()).isEqualTo("original");
    }

    @Test
    void shouldSupportUserWithoutRoles() {
        assertThat(new User("user@test.com", "password", null).getAuthorities()).isEmpty();
        assertThat(new User("user@test.com", "password", Set.of()).getAuthorities()).isEmpty();
    }
}
