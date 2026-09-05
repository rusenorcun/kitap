package app.kitapla.security;

import app.kitapla.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AppUserDetails implements UserDetails {

    private final User user;

    public AppUserDetails(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (user.isStudent()) list.add(new SimpleGrantedAuthority("ROLE_STUDENT"));
        if (user.isAdmin()) list.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        return list;
    }

    @Override public String getPassword() { return user.getPasswordHash(); }
    @Override public String getUsername() { return user.getEmail(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !user.isBlocked(); }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return !user.isBlocked(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppUserDetails that = (AppUserDetails) o;
        if (user != null && that.user != null) {
            if (user.getId() != null && that.user.getId() != null) {
                return user.getId().equals(that.user.getId());
            }
            if (user.getEmail() != null && that.user.getEmail() != null) {
                return user.getEmail().equalsIgnoreCase(that.user.getEmail());
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (user != null && user.getId() != null) {
            return user.getId().hashCode();
        }
        if (user != null && user.getEmail() != null) {
            return user.getEmail().toLowerCase(java.util.Locale.ROOT).hashCode();
        }
        return 0;
    }
}
