package app.kitapla.api.v1;

import app.kitapla.api.dto.AdminStatsDto;
import app.kitapla.api.dto.ApiDtoMapper;
import app.kitapla.api.dto.UserDto;
import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.CurrentUser;
import app.kitapla.service.AdminService;
import app.kitapla.service.AdminStats;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminApiController {

    private final AdminService adminService;
    private final UserRepository userRepository;

    public AdminApiController(AdminService adminService, UserRepository userRepository) {
        this.adminService = adminService;
        this.userRepository = userRepository;
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDto> getStats() {
        AdminStats stats = adminService.stats();
        return ResponseEntity.ok(ApiDtoMapper.toAdminStatsDto(stats));
    }

    @GetMapping("/pending-docs")
    public ResponseEntity<List<UserDto>> getPendingDocs() {
        List<User> list = adminService.pendingDocuments();
        List<UserDto> dtos = list.stream().map(ApiDtoMapper::toUserDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/docs/{id}/approve")
    public ResponseEntity<Void> approveDoc(@PathVariable Long id) {
        adminService.approveStudent(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/docs/{id}/reject")
    public ResponseEntity<Void> rejectDoc(@PathVariable Long id, @RequestParam(required = false) String reason) {
        adminService.rejectStudent(id, reason);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers(@RequestParam(required = false) String q) {
        List<User> list = adminService.searchUsers(q);
        List<UserDto> dtos = list.stream().map(ApiDtoMapper::toUserDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/users/{id}/block")
    public ResponseEntity<Void> toggleBlockUser(@PathVariable Long id) {
        User admin = CurrentUser.get();
        if (admin == null) throw new IllegalStateException("Giriş yapmalısınız.");

        User target = userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Kullanıcı bulunamadı."));
        adminService.setBlocked(admin, id, !target.isBlocked());
        return ResponseEntity.noContent().build();
    }
}
