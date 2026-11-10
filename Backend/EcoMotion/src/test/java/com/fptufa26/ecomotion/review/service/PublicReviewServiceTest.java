package com.fptufa26.ecomotion.review.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicReviewServiceTest {

    @Test
    void reviewerNameIsMaskedForPublicPages() {
        assertThat(PublicReviewService.maskName("Nguyễn Văn An")).isEqualTo("Nguyễn V. A.");
        assertThat(PublicReviewService.maskName("Hà")).isEqualTo("H.");
        assertThat(PublicReviewService.maskName("  ")).isEqualTo("Khách thuê");
        assertThat(PublicReviewService.maskName(null)).isEqualTo("Khách thuê");
    }
}
