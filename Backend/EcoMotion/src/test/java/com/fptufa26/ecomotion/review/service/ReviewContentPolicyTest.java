package com.fptufa26.ecomotion.review.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewContentPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Tài xế lái quá ẩu, đm",
            "Xe bẩn vãi lồn",
            "thằng ngu này đến trễ",
            "ĐỤUUUU má dịch vụ tệ",
            "dịch vụ như cc",
            "vcl xe hôi",
            "What the FUCK",
            "dit me tai xe",
    })
    void rejectsProfanity(String comment) {
        assertThat(ReviewContentPolicy.isClean(comment)).as(comment).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Bỏ dấu sẽ thành lon/cac/buoi/deo/du — phải KHÔNG bị chặn.
            "Xe lớn, rộng rãi, các buổi đi đều đúng giờ",
            "Tài xế đeo khẩu trang, chuyến du lịch rất vui",
            "Dù trời mưa nhưng tài xế vẫn đến sớm, dự định sẽ thuê lại",
            "Xe 150cc chạy êm, máy lạnh tốt",
            "Cục sạc trên xe hơi lỏng, cần sửa",
            "Đánh giá 5 sao, rất hài lòng!",
            "",
    })
    void acceptsNormalVietnamese(String comment) {
        assertThat(ReviewContentPolicy.isClean(comment)).as(comment).isTrue();
    }
}
