package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.IncidentReportRequest;
import com.fptufa26.ecomotion.booking.entity.TripIncident;
import com.fptufa26.ecomotion.booking.enums.TripIncidentType;
import com.fptufa26.ecomotion.booking.enums.TripIncidentUrgency;
import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentEvidenceUrlsTest {

    private static String url(String name) {
        return "https://res.cloudinary.test/ecomotion/" + name + ".jpg";
    }

    @Test
    void noPhotoNormalizesToEmptyList() {
        assertThat(IncidentEvidenceUrls.normalize(null)).isEmpty();
        assertThat(IncidentEvidenceUrls.normalize(List.of())).isEmpty();
        assertThat(IncidentEvidenceUrls.normalize(List.of("  ", ""))).isEmpty();
    }

    @Test
    void onePhotoIsKept() {
        assertThat(IncidentEvidenceUrls.normalize(List.of(url("a")))).containsExactly(url("a"));
    }

    @Test
    void manyPhotosKeepOrderAndDropDuplicatesAndBlanks() {
        List<String> input = List.of(url("b"), " ", url("a"), url("b"), url("c"));
        assertThat(IncidentEvidenceUrls.normalize(input)).containsExactly(url("b"), url("a"), url("c"));
    }

    @Test
    void exactlyTheMaximumNumberOfPhotosIsAccepted() {
        List<String> ten = new ArrayList<>();
        for (int i = 0; i < IncidentEvidenceUrls.MAX_COUNT; i++) ten.add(url("p" + i));
        assertThat(IncidentEvidenceUrls.normalize(ten)).hasSize(IncidentEvidenceUrls.MAX_COUNT);
    }

    @Test
    void moreThanTheMaximumIsRejected() {
        List<String> eleven = new ArrayList<>();
        for (int i = 0; i <= IncidentEvidenceUrls.MAX_COUNT; i++) eleven.add(url("p" + i));
        assertThatThrownBy(() -> IncidentEvidenceUrls.normalize(eleven)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void nonHttpsOrUnsafeUrlsAreRejected() {
        for (String bad : List.of(
                "http://insecure.test/a.jpg",
                "javascript:alert(1)",
                "data:image/png;base64,AAAA",
                "https://ok.test/with space.jpg",
                "ftp://x/a.jpg")) {
            assertThatThrownBy(() -> IncidentEvidenceUrls.normalize(List.of(bad)))
                    .as(bad).isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void overlongUrlIsRejected() {
        String tooLong = "https://res.cloudinary.test/" + "x".repeat(IncidentEvidenceUrls.MAX_URL_LENGTH) + ".jpg";
        assertThatThrownBy(() -> IncidentEvidenceUrls.normalize(List.of(tooLong))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void entityRoundTripsListAsJsonAndNullForEmpty() {
        TripIncident incident = new TripIncident();
        incident.setEvidenceUrlList(List.of(url("a"), url("b")));
        assertThat(incident.getEvidenceUrls()).startsWith("[");
        assertThat(incident.getEvidenceUrlList()).containsExactly(url("a"), url("b"));

        incident.setEvidenceUrlList(List.of());
        assertThat(incident.getEvidenceUrls()).isNull();
        assertThat(incident.getEvidenceUrlList()).isEmpty();
    }

    @Test
    void maximumPhotoListFitsTheExistingTwoThousandCharacterColumn() {
        List<String> ten = new ArrayList<>();
        for (int i = 0; i < IncidentEvidenceUrls.MAX_COUNT; i++) {
            ten.add("https://" + "a".repeat(IncidentEvidenceUrls.MAX_URL_LENGTH - "https://".length()));
        }
        TripIncident incident = new TripIncident();
        incident.setEvidenceUrlList(ten);
        assertThat(incident.getEvidenceUrls().length()).isLessThanOrEqualTo(2000);
    }

    @Test
    void legacySingleUrlStoredAsPlainStringIsReadAsOneItem() {
        TripIncident legacy = new TripIncident();
        legacy.setEvidenceUrls(url("old"));
        assertThat(legacy.getEvidenceUrlList()).containsExactly(url("old"));
    }

    @Test
    void legacyCommaSeparatedStringIsReadAsSeveralItems() {
        TripIncident legacy = new TripIncident();
        legacy.setEvidenceUrls(url("x") + "," + url("y"));
        assertThat(legacy.getEvidenceUrlList()).containsExactly(url("x"), url("y"));
    }

    @Test
    void legacyBlankValueIsReadAsEmpty() {
        TripIncident legacy = new TripIncident();
        legacy.setEvidenceUrls("   ");
        assertThat(legacy.getEvidenceUrlList()).isEmpty();
    }

    @Test
    void requestAcceptsTheOldSingleStringFormAndTheNewArrayFormAndNoPhoto() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String base = "\"incidentType\":\"ACCIDENT\",\"description\":\"d\",\"urgency\":\"HIGH\"";

        IncidentReportRequest none = mapper.readValue("{" + base + "}", IncidentReportRequest.class);
        assertThat(none.evidenceUrls()).isNull();

        IncidentReportRequest oldClient = mapper.readValue(
                "{" + base + ",\"evidenceUrls\":\"" + url("one") + "\"}", IncidentReportRequest.class);
        assertThat(oldClient.evidenceUrls()).containsExactly(url("one"));

        IncidentReportRequest arrayClient = mapper.readValue(
                "{" + base + ",\"evidenceUrls\":[\"" + url("a") + "\",\"" + url("b") + "\"]}", IncidentReportRequest.class);
        assertThat(arrayClient.evidenceUrls()).containsExactly(url("a"), url("b"));
    }

    // Đảm bảo hai enum dùng trong test vẫn tồn tại trong nghiệp vụ.
    @Test
    void referencedEnumsExist() {
        assertThat(TripIncidentType.ACCIDENT).isNotNull();
        assertThat(TripIncidentUrgency.HIGH).isNotNull();
    }
}
