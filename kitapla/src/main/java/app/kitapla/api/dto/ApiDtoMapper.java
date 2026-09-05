package app.kitapla.api.dto;

import app.kitapla.domain.*;
import app.kitapla.service.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public final class ApiDtoMapper {

    private ApiDtoMapper() {}

    public static String toIso(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    public static UserDto toUserDto(User user) {
        if (user == null) return null;
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.isAdmin(),
                user.getStudentStatus() != null ? user.getStudentStatus().name() : null,
                user.getSchoolLevel() != null ? user.getSchoolLevel().name() : null,
                user.getInitials(),
                user.getAddress(),
                user.getPhone(),
                user.getSchool() != null ? user.getSchool().name() : null
        );
    }

    public static QuotaDto toQuotaDto(Quota q) {
        if (q == null) return null;
        return new QuotaDto(
                q.tier(),
                q.weeklyUsed(),
                q.weeklyLimit(),
                q.weeklyRemaining(),
                q.monthlyUsed(),
                q.monthlyLimit(),
                q.monthlyRemaining(),
                q.canReceive()
        );
    }

    public static MeDto toMeDto(User user, Quota quota) {
        return new MeDto(toUserDto(user), toQuotaDto(quota));
    }

    public static BookDto toBookDto(Book book) {
        if (book == null) return new BookDto(0L, "", null, null, null, null);
        return new BookDto(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getCoverUrl(),
                book.getPurchaseLink(),
                book.getDescription()
        );
    }

    public static PickupPointDto toPickupPointDto(PickupPoint point) {
        if (point == null) return null;
        return new PickupPointDto(
                point.getId(),
                point.getName(),
                point.getDescription(),
                point.isActive()
        );
    }

    public static MeetingDto toMeetingDto(Meeting meeting) {
        if (meeting == null || !meeting.isArranged()) return null;
        return new MeetingDto(
                toPickupPointDto(meeting.getPoint()),
                meeting.getNote(),
                toIso(meeting.getAt()),
                toIso(meeting.getArrangedAt()),
                toIso(meeting.getRemindedAt())
        );
    }

    public static EligibilityDto toEligibilityDto(ClaimEligibility eligibility) {
        if (eligibility == null) return new EligibilityDto(true, null, null);
        return new EligibilityDto(
                eligibility.allowed(),
                eligibility.code(),
                eligibility.reason()
        );
    }

    public static DonationDto toDonationDto(DonationView view, ClaimEligibility eligibility) {
        if (view == null || view.donation() == null) return null;
        Donation d = view.donation();
        return new DonationDto(
                d.getId(),
                toBookDto(d.getBook()),
                d.getDonor() != null ? d.getDonor().getName() : null,
                d.getDonor() != null ? d.getDonor().getInitials() : null,
                d.getDescription(),
                d.getQuantity(),
                view.claimed(),
                view.remaining(),
                d.getSource() != null ? d.getSource().name() : null,
                d.getTargetLevel() != null ? d.getTargetLevel().name() : null,
                d.getStatus() != null ? d.getStatus().name() : null,
                view.isPriorityActive(),
                view.getPriorityLeft(),
                toPickupPointDto(d.getPreferredPoint()),
                toIso(d.getCreatedAt()),
                toEligibilityDto(eligibility)
        );
    }

    public static ClaimDto toClaimDto(Claim claim, boolean includeAddress, Long conversationId) {
        if (claim == null) return null;
        User requester = claim.getStudent();
        return new ClaimDto(
                claim.getId(),
                claim.getStatus() != null ? claim.getStatus().name() : null,
                requester != null ? requester.getName() : null,
                requester != null ? requester.getInitials() : null,
                includeAddress && requester != null ? requester.getAddress() : null,
                includeAddress && requester != null ? requester.getPhone() : null,
                toMeetingDto(claim.getMeeting()),
                conversationId,
                toIso(claim.getCreatedAt())
        );
    }

    public static MyDonationDto toMyDonationDto(DonationView view, List<ClaimDto> claims) {
        if (view == null || view.donation() == null) return null;
        Donation d = view.donation();
        return new MyDonationDto(
                d.getId(),
                toBookDto(d.getBook()),
                d.getQuantity(),
                view.claimed(),
                view.remaining(),
                d.getSource() != null ? d.getSource().name() : null,
                d.getTargetLevel() != null ? d.getTargetLevel().name() : null,
                d.getStatus() != null ? d.getStatus().name() : null,
                claims != null ? claims : Collections.emptyList(),
                toPickupPointDto(d.getPreferredPoint()),
                d.getPreferredPointNote(),
                toIso(d.getCreatedAt())
        );
    }

    public static MyClaimDto toMyClaimDto(Claim claim, Long conversationId) {
        if (claim == null) return null;
        Donation d = claim.getDonation();
        User donor = d != null ? d.getDonor() : null;
        return new MyClaimDto(
                claim.getId(),
                claim.getStatus() != null ? claim.getStatus().name() : null,
                toBookDto(d != null ? d.getBook() : null),
                donor != null ? donor.getName() : null,
                donor != null ? donor.getInitials() : null,
                toMeetingDto(claim.getMeeting()),
                conversationId,
                toIso(claim.getCreatedAt()),
                toIso(claim.getMeeting() != null ? claim.getMeeting().getArrangedAt() : null),
                toIso(claim.getShippedAt()),
                toIso(claim.getDeliveredAt())
        );
    }

    public static RequestDto toRequestDto(BookRequest request, Long conversationId) {
        if (request == null) return null;
        User requester = request.getStudent();
        User fulfiller = request.getFulfilledBy();
        return new RequestDto(
                request.getId(),
                toBookDto(request.getBook()),
                requester != null ? requester.getName() : null,
                requester != null ? requester.getInitials() : null,
                request.getDescription(),
                request.getSource() != null ? request.getSource().name() : null,
                request.getStatus() != null ? request.getStatus().name() : null,
                fulfiller != null ? fulfiller.getName() : null,
                fulfiller != null ? fulfiller.getInitials() : null,
                toMeetingDto(request.getMeeting()),
                conversationId,
                toIso(request.getCreatedAt())
        );
    }

    public static SwapListingDto toSwapListingDto(SwapBook swapBook) {
        if (swapBook == null) return null;
        User owner = swapBook.getUser();
        return new SwapListingDto(
                swapBook.getId(),
                toBookDto(swapBook.getBook()),
                swapBook.getNote(),
                swapBook.getStatus() != null ? swapBook.getStatus().name() : null,
                owner != null ? owner.getName() : null,
                owner != null ? owner.getInitials() : null,
                toIso(swapBook.getCreatedAt())
        );
    }

    public static OfferDto toOfferDto(SwapOffer offer, User me, Long conversationId, boolean addressVisible) {
        if (offer == null) return null;
        boolean isOutgoing = offer.getFromUser() != null && offer.getFromUser().getId().equals(me.getId());
        User counterpart = isOutgoing ? offer.getToUser() : offer.getFromUser();
        Book takeBook = isOutgoing ? (offer.getTargetSwapBook() != null ? offer.getTargetSwapBook().getBook() : null)
                                   : (offer.getOfferedSwapBook() != null ? offer.getOfferedSwapBook().getBook() : null);
        Book giveBook = isOutgoing ? (offer.getOfferedSwapBook() != null ? offer.getOfferedSwapBook().getBook() : null)
                                   : (offer.getTargetSwapBook() != null ? offer.getTargetSwapBook().getBook() : null);
        boolean mineHandedOver = isOutgoing ? offer.getFromShippedAt() != null : offer.getToShippedAt() != null;
        boolean theirsHandedOver = isOutgoing ? offer.getToShippedAt() != null : offer.getFromShippedAt() != null;

        return new OfferDto(
                offer.getId(),
                isOutgoing ? "OUTGOING" : "INCOMING",
                offer.getStatus() != null ? offer.getStatus().name() : null,
                offer.getMessage(),
                counterpart != null ? counterpart.getName() : null,
                counterpart != null ? counterpart.getInitials() : null,
                toBookDto(takeBook),
                toBookDto(giveBook),
                mineHandedOver,
                theirsHandedOver,
                addressVisible,
                toMeetingDto(offer.getMeeting()),
                conversationId,
                toIso(offer.getCreatedAt())
        );
    }

    public static NotificationDto toNotificationDto(Notification notification) {
        if (notification == null) return null;
        return new NotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.isReadFlag(),
                toIso(notification.getCreatedAt())
        );
    }

    public static ConversationDto toConversationDto(Conversation conversation, User me, long unread) {
        if (conversation == null) return null;
        User counterpart = conversation.other(me);
        String title = switch (conversation.getKind()) {
            case CLAIM -> "Bağış Talebi";
            case REQUEST -> "Kitap İsteği";
            case SWAP -> "Kitap Takası";
            case REPORT -> "Şikâyet Destek (#" + conversation.getRefId() + ")";
        };
        String counterpartName = counterpart != null ? counterpart.getName() : null;
        String counterpartInitials = counterpart != null ? counterpart.getInitials() : null;
        if (conversation.getKind() == ConversationKind.REPORT && !me.isAdmin()) {
            counterpartName = "Kitapla Destek";
            counterpartInitials = "KD";
        }
        return new ConversationDto(
                conversation.getId(),
                conversation.getKind() != null ? conversation.getKind().name() : null,
                conversation.getRefId(),
                title,
                counterpartName,
                counterpartInitials,
                conversation.getLastMessage(),
                toIso(conversation.getLastMessageAt()),
                unread
        );
    }

    public static ChatMessageDto toChatMessageDto(Message message, User me) {
        if (message == null) return null;
        boolean mine = message.getSender() != null && message.getSender().getId().equals(me.getId());
        return new ChatMessageDto(
                message.getId(),
                message.getBody(),
                mine,
                message.getSender() != null ? message.getSender().getName() : null,
                toIso(message.getCreatedAt())
        );
    }

    public static BookMetadataDto toBookMetadataDto(BookMetadata metadata, boolean found) {
        if (metadata == null) return new BookMetadataDto(false, null, null, null, null, null);
        return new BookMetadataDto(
                found,
                metadata.title(),
                metadata.author(),
                metadata.imageUrl(),
                null,
                metadata.description()
        );
    }

    public static AdminStatsDto toAdminStatsDto(AdminStats stats) {
        if (stats == null) return new AdminStatsDto(0, 0, 0, 0);
        return new AdminStatsDto(
                (int) stats.users(),
                (int) stats.pendingDocuments(),
                (int) stats.openDonations(),
                (int) stats.deliveredClaims()
        );
    }
}
