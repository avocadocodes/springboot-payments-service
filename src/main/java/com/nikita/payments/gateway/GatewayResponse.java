package com.nikita.payments.gateway;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GatewayResponse {

    private boolean success;
    private String reference;
    private String errorMessage;
}
