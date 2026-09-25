package app.kitappla.api.dto;

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
        String school,
        boolean blocked,
        boolean student,
        int noShowCount,
        String documentNo,
        boolean hasDocument,
        boolean emailVerified
) {}
