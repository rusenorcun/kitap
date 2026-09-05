package app.kitapla.api.v1;

import app.kitapla.api.dto.ApiDtoMapper;
import app.kitapla.api.dto.PickupPointDto;
import app.kitapla.service.PickupPointService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pickup-points")
public class PickupPointApiController {

    private final PickupPointService pickupPointService;

    public PickupPointApiController(PickupPointService pickupPointService) {
        this.pickupPointService = pickupPointService;
    }

    @GetMapping
    public ResponseEntity<List<PickupPointDto>> getActivePickupPoints() {
        List<PickupPointDto> list = pickupPointService.active().stream()
                .map(ApiDtoMapper::toPickupPointDto)
                .toList();
        return ResponseEntity.ok(list);
    }
}
