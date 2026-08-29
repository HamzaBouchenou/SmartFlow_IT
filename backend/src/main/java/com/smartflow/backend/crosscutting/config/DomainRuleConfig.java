package com.smartflow.backend.crosscutting.config;

import com.smartflow.backend.domain.rule.AttachmentValidationRule;
import com.smartflow.backend.domain.rule.CommentRequirementRule;
import com.smartflow.backend.domain.rule.FormValidationRule;
import com.smartflow.backend.domain.rule.MandatoryNotificationRule;
import com.smartflow.backend.domain.rule.RolePermissionRule;
import com.smartflow.backend.domain.rule.ScopeRule;
import com.smartflow.backend.domain.rule.SeparationOfDutiesRule;
import com.smartflow.backend.domain.rule.SlaCalculator;
import com.smartflow.backend.domain.rule.SlaSuspensionRule;
import com.smartflow.backend.domain.rule.SlaThresholdTransitionRule;
import com.smartflow.backend.domain.rule.TransitionResolutionRule;
import com.smartflow.backend.domain.rule.WorkflowActionAvailabilityRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires plain domain/rule classes into the Spring context as beans, without putting any
 * Spring annotation on the rule classes themselves - domain/rule must stay importable and
 * testable with zero framework dependency (CLAUDE.md).
 */
@Configuration
public class DomainRuleConfig {

    @Bean
    public SlaCalculator slaCalculator() {
        return new SlaCalculator();
    }

    @Bean
    public SlaSuspensionRule slaSuspensionRule() {
        return new SlaSuspensionRule();
    }

    @Bean
    public RolePermissionRule rolePermissionRule() {
        return new RolePermissionRule();
    }

    @Bean
    public ScopeRule scopeRule() {
        return new ScopeRule();
    }

    @Bean
    public WorkflowActionAvailabilityRule workflowActionAvailabilityRule() {
        return new WorkflowActionAvailabilityRule();
    }

    @Bean
    public SeparationOfDutiesRule separationOfDutiesRule() {
        return new SeparationOfDutiesRule();
    }

    @Bean
    public FormValidationRule formValidationRule() {
        return new FormValidationRule();
    }

    @Bean
    public TransitionResolutionRule transitionResolutionRule() {
        return new TransitionResolutionRule();
    }

    @Bean
    public CommentRequirementRule commentRequirementRule() {
        return new CommentRequirementRule();
    }

    @Bean
    public AttachmentValidationRule attachmentValidationRule() {
        return new AttachmentValidationRule();
    }

    @Bean
    public MandatoryNotificationRule mandatoryNotificationRule() {
        return new MandatoryNotificationRule();
    }

    @Bean
    public SlaThresholdTransitionRule slaThresholdTransitionRule() {
        return new SlaThresholdTransitionRule();
    }
}
