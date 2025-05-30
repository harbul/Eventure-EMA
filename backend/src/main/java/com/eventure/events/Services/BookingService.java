package com.eventure.events.Services;

import com.eventure.events.dto.BookingRequest;
import com.eventure.events.dto.BookingResponse;
import com.eventure.events.dto.PdfTicketDataDto;
import com.eventure.events.dto.Ticket;
import com.eventure.events.exception.MyException;
import com.eventure.events.model.BookingDetails;
import com.eventure.events.model.Events;
import com.eventure.events.model.Users;
import com.eventure.events.repository.BookingRepo;
import com.eventure.events.repository.EventRepo;
import com.eventure.events.repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;

@Service
public class BookingService {
    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepo bookingRepo;
    private final EventRepo eventRepo;
    private final UserRepo userRepo;
    private final EmailService emailService;
    private final QrCodeService qrcodeService;
    private final PdfTicketService pdfTicketService;

    @Autowired
    public BookingService(BookingRepo bookingRepo, EventRepo eventRepo, UserRepo userRepo, EmailService emailService, QrCodeService qrcodeService, PdfTicketService pdfTicketService) {
        this.bookingRepo = bookingRepo;
        this.eventRepo = eventRepo;
        this.userRepo = userRepo;
        this.emailService = emailService;
        this.qrcodeService = qrcodeService;
        this.pdfTicketService = pdfTicketService;
    }

    public BookingResponse bookEvent(BookingRequest request) {
        Users user = userRepo.findById(request.getUserId())
                .orElseThrow(() -> new MyException("User not found with id: " + request.getUserId()));

        Events event = eventRepo.findById(request.getEventId())
                .orElseThrow(() -> new MyException("Event not found with id: " + request.getEventId()));

        if (request.getTicketCount() > event.getAvailable_tickets()) {
            throw new MyException("Only " + event.getAvailable_tickets() + " tickets available, but "
                    + request.getTicketCount() + " requested.");
        }

        if (!request.isPaymentStatus()) {
            throw new MyException("Payment was not successful, booking aborted.");
        }

        List<Ticket> ticketList = new ArrayList<>();
        for (int i = 0; i < request.getTicketCount(); i++) {
            String ticketId = "T" + UUID.randomUUID().toString().substring(0, 8);
            Ticket ticket = new Ticket(ticketId, request.getTicketPrice(), request.getEventId(), ticketId, null);
            ticketList.add(ticket);
        }

        BookingDetails booking = new BookingDetails();
        booking.setUserId(request.getUserId());
        booking.setTicketCount(request.getTicketCount());
        booking.setTotalTicketPrice(request.getTotalTicketPrice());
        booking.setTickets(ticketList);
        booking.setBookingStatus("CONFIRMED");

        BookingDetails savedBooking = bookingRepo.save(booking);

        event.setAvailable_tickets(event.getAvailable_tickets() - request.getTicketCount());
        event.setEventAttendees(event.getEventAttendees() + request.getTicketCount());

        eventRepo.save(event);

        // Send confirmation email
        try {
            Map<String, String> emailVariables = new HashMap<>();
            emailVariables.put("userName", user.getFirstName() + " " + user.getLastName());
            emailVariables.put("eventName", event.getEventName());
            emailVariables.put("eventDate", event.getEventDateTime().toString());
            emailVariables.put("eventAddress", event.getAddress() + ", " + event.getCity() + ", " + event.getState() + " " + event.getZipCode());
            emailVariables.put("eventInstruction", event.getEventInstruction() != null ? event.getEventInstruction() : "No specific instructions provided.");
            
            String gmapUrl = String.format("https://www.google.com/maps/search/?api=1&query=%s,%s,%s,%s",
                    event.getAddress(), event.getCity(), event.getState(), event.getZipCode());
            emailVariables.put("gmapUrl", gmapUrl);

            System.out.println("Attempting to send email to: " + user.getEmail());
            emailService.sendHtmlEmail(
                user.getEmail(),
                "Booking Confirmation - " + event.getEventName(),
                "Emailtemplate/booking-confirmation.html",
                emailVariables,
                savedBooking.getTickets()
            );
            System.out.println("Email sent successfully to: " + user.getEmail());
        } catch (Exception e) {
            System.err.println("Failed to send confirmation email: " + e.getMessage());
            e.printStackTrace(); // This will print the full stack trace
        }

        return new BookingResponse(savedBooking, user, event);
    }

    public BookingResponse getBookingDetailsWithQrCodes(String bookingId, String requestingUserId) {
        BookingDetails booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new MyException("Booking not found with ID: " + bookingId));

        // Authorization: Check if the requesting user owns this booking
        if (!booking.getUserId().equals(requestingUserId)) {
            throw new MyException("User not authorized to view this booking.");
            // Or handle with a specific HTTP status in the controller like 403 Forbidden
        }

        if (booking.getTickets() != null && !booking.getTickets().isEmpty()) {
            for (Ticket ticket : booking.getTickets()) {
                if (ticket.getTicketId() != null && !ticket.getTicketId().isEmpty()) {
                    try {
                        String qrBase64 = qrcodeService.generateQrCodeBase64(ticket.getTicketId(), 200, 200);
                        ticket.setQrCodeImageBase64(qrBase64);
                    } catch (Exception e) {
                        logger.error("Failed to generate QR code for ticketId {}: {}", ticket.getTicketId(), e.getMessage(), e);
                        ticket.setQrCodeImageBase64(null); // Or an error indicator
                    }
                } else {
                    ticket.setQrCodeImageBase64(null); // No value to encode
                }
            }
        } else {
             logger.warn("Booking with ID: {} has no tickets.", booking.getId());
        }

        // Fetch associated event and user details to return a comprehensive BookingResponse
        Events event = null;
        if (booking.getTickets() != null && !booking.getTickets().isEmpty() && booking.getTickets().get(0).getEventId() != null) {
            event = eventRepo.findById(booking.getTickets().get(0).getEventId()).orElse(null);
        }
        if (event == null) {
            logger.warn("Event details not found for booking ID: {}", bookingId);
            // You might want to throw an exception or handle this gracefully depending on requirements
        }

        Users user = userRepo.findById(booking.getUserId()).orElse(null);
        if (user == null) {
            logger.warn("User details not found for booking ID: {}", bookingId);
             // You might want to throw an exception or handle this gracefully
        }
        
        // The BookingDetails object ('booking') now has its tickets populated with qrCodeImageBase64.
        // Construct and return your BookingResponse DTO.
        return new BookingResponse(booking, user, event);
    }

    public String cancelBooking(String bookingId, String userId) {
        BookingDetails booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new MyException("Booking not found with id: " + bookingId));

        if (!booking.getUserId().equals(userId)) {
            throw new MyException("This booking does not belong to the user: " + userId);
        }

        if ("CANCELLED".equalsIgnoreCase(booking.getBookingStatus())) {
            throw new MyException("Booking is already cancelled.");
        }

        // Get user and event details for the email
        Users user = userRepo.findById(userId)
                .orElseThrow(() -> new MyException("User not found with id: " + userId));

        String eventId = booking.getTickets().get(0).getEventId();
        Events event = eventRepo.findById(eventId)
                .orElseThrow(() -> new MyException("Event not found with id: " + eventId));

        // Update booking status
        booking.setBookingStatus("CANCELLED");
        bookingRepo.save(booking);

        // Update event details
        event.setAvailable_tickets(event.getAvailable_tickets() + booking.getTicketCount());
        event.setEventAttendees(Math.max(0, event.getEventAttendees() - booking.getTicketCount()));
        eventRepo.save(event);

        // Send cancellation email
        try {
            Map<String, String> emailVariables = new HashMap<>();
            emailVariables.put("userName", user.getFirstName() + " " + user.getLastName());
            emailVariables.put("eventName", event.getEventName());
            emailVariables.put("eventDate", event.getEventDateTime().toString());
            emailVariables.put("eventAddress", event.getAddress() + ", " + event.getCity() + ", " + event.getState() + " " + event.getZipCode());

            emailService.sendHtmlEmail(
                user.getEmail(),
                "Booking Cancelled - " + event.getEventName(),
                "Emailtemplate/booking-cancellation.html",
                emailVariables,
                null
            );
            System.out.println("Cancellation email sent successfully to: " + user.getEmail());
        } catch (Exception e) {
            System.err.println("Failed to send cancellation email: " + e.getMessage());
            e.printStackTrace();
            // Don't throw the exception as the booking is already cancelled
        }

        return "Booking cancelled successfully.";
    }

    public PdfTicketDataDto getPdfGenerationData(String bookingId, String requestingUserId) {
        BookingDetails booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new MyException("Booking not found with ID: " + bookingId));

        if (!booking.getUserId().equals(requestingUserId)) {
            throw new MyException("User not authorized to view this booking.");
        }

        Events event = null;
        if (booking.getTickets() != null && !booking.getTickets().isEmpty() && booking.getTickets().get(0).getEventId() != null) {
            event = eventRepo.findById(booking.getTickets().get(0).getEventId())
                    .orElseThrow(() -> new MyException("Event details not found for booking: " + bookingId));
        } else {
             throw new MyException("Cannot determine event for booking: " + bookingId);
        }

        Users user = userRepo.findById(booking.getUserId())
                .orElseThrow(() -> new MyException("User details not found for booking: " + bookingId));

        return new PdfTicketDataDto(booking, event, user);
    }

    public ByteArrayResource generatePdf(String bookingId, String requestingUserId) throws IOException {
        PdfTicketDataDto data = getPdfGenerationData(bookingId, requestingUserId);
        // PdfTicketService now needs to accept PdfTicketDataDto or you extract from it here
        byte[] pdfBytes = pdfTicketService.generateTicketPdf(data.getBooking(), data.getEvent(), data.getUser());
        return new ByteArrayResource(pdfBytes);
    }

}

