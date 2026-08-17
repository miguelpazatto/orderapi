package com.miguelpazatto.orderapi.delivery.clients;

import com.miguelpazatto.orderapi.delivery.dtos.DeliveryRequestDTO;
import com.miguelpazatto.orderapi.delivery.dtos.DeliveryResponseDTO;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "deliveryClient", url = "${delivery.api.url}")
public interface DeliveryClient {

    @PostMapping("/envios")
    DeliveryResponseDTO solicitarRastreio(@RequestBody @Valid DeliveryRequestDTO request);

}
