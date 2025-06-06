package com.eventure.events.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {
    private String ticketId;
    private double ticketPrice;
    private String eventId;
    private String qrCodeValue;
    private String qrCodeImageBase64;
}
