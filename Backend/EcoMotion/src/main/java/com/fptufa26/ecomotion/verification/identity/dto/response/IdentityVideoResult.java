package com.fptufa26.ecomotion.verification.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IdentityVideoResult(
        Integer sampledFrames,
        MotionChallenge motionChallenge,
        FaceMatching faceMatching,
        String identityDecision,
        Boolean automaticApproval,
        Workflow workflow, MatchAssessment matchAssessment) {
    public IdentityVideoResult(Integer sampledFrames, MotionChallenge motionChallenge, FaceMatching faceMatching,
            String identityDecision, Boolean automaticApproval, Workflow workflow) {
        this(sampledFrames,motionChallenge,faceMatching,identityDecision,automaticApproval,workflow,null);
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchAssessment(String status,Double score,Double lowThreshold,Double highThreshold,
            Integer comparedFrames,Integer minimumFrames,String metric,String modelSha256,String policyVersion,String calibrationStatus,List<String> reasons) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MotionChallenge(String status, List<String> expected, List<CompletedStep> completed) {
    }

    public record CompletedStep(String pose, Double from, Double to) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FaceMatching(
            Integer candidateFrames, Integer scoredFrames, Integer unusableFrames,
            Double minimum, Double median, Double maximum, List<FrameScore> frames) {
    }

    public record FrameScore(
            Integer frameIndex, Double timestamp, Double yaw,
            String status, Double similarity, String reason) {
    }

    public record Workflow(String status, List<String> reasons) {
    }
}
