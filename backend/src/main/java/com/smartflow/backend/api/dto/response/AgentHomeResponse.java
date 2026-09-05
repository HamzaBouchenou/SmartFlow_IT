package com.smartflow.backend.api.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * §6.9 - "Vue agent : charge actuelle, éléments en retard et priorités hautes." Les trois
 * sections lisent la même file personnelle que TaskQueueService.myTasks (§6.6) - jamais une
 * deuxième notion de "charge" recalculée séparément de "Mes tâches".
 */
public record AgentHomeResponse(
        long currentLoad,
        List<AgentTaskItem> overdue,
        List<AgentTaskItem> highPriority) {

    public record AgentTaskItem(
            Long id, String reference, String title, String priority, String slaStatus, Instant slaDueAtResolution) {
    }
}
