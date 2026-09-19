package app.kitapla.api.v1;

import app.kitapla.api.dto.AdminReportDto;
import app.kitapla.api.dto.AdminStatsDto;
import app.kitapla.api.dto.ApiDtoMapper;
import app.kitapla.api.dto.UserDto;
import app.kitapla.domain.Report;
import app.kitapla.domain.User;
import app.kitapla.repo.UserRepository;
import app.kitapla.security.CurrentUser;
import app.kitapla.service.AdminService;
import app.kitapla.service.AdminStats;
import app.kitapla.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminApiController {

    private final AdminService adminService;
    private final UserRepository userRepository;
    private final ReportService reportService;

    public AdminApiController(AdminService adminService, UserRepository userRepository, ReportService reportService) {
        this.adminService = adminService;
        this.userRepository = userRepository;
        this.reportService = reportService;
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

    @GetMapping("/reports")
    public ResponseEntity<List<AdminReportDto>> getReports(@RequestParam(required = false, defaultValue = "false") boolean all) {
        List<Report> list = all ? reportService.all() : reportService.open();
        List<AdminReportDto> dtos = list.stream().map(ApiDtoMapper::toAdminReportDto).toList();
        return ResponseEntity.ok(dtos);
    }

    public record ResolveReportBody(boolean actioned, String adminNote) {}

    @PostMapping("/reports/{id}/resolve")
    public ResponseEntity<Void> resolveReport(@PathVariable Long id, @RequestBody(required = false) ResolveReportBody body) {
        User admin = CurrentUser.get();
        if (admin == null) throw new IllegalStateException("Giriş yapmalısınız.");
        boolean actioned = body != null && body.actioned();
        String adminNote = body != null ? body.adminNote() : null;
        reportService.resolve(id, admin, actioned, adminNote);
        return ResponseEntity.noContent().build();
    }
}
