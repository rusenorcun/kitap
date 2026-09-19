package app.kitapla.api.dto;

public record UserDto(
        Long id,
        String name,
        String email,
        boolean admin,
        String studentStatus,
        String schoolLevel,
        String initials,
        String address,
        String phone,
        String school
) {}
