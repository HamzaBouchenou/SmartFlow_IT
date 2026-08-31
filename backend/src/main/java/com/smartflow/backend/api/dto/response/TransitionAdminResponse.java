package com.smartflow.backend.api.dto.response;

/** §6.5/§6.10 - une transition légale, vue administration. `toStepId` est `null` pour
 * CLOSE (action terminale, §6.4). */
public record TransitionAdminResponse(
        Long id,
        Long fromStepId,
        String action,
        Long toStepId,
        String conditionPriority,
        Long conditionDepartmentId,
        String conditionDepartmentName,
        String conditionFieldCode,
        String conditionFieldValue) {
}
