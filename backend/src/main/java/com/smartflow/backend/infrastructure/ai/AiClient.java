package com.smartflow.backend.infrastructure.ai;

import com.smartflow.backend.domain.exception.InvalidRequestStateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

/**
 * ADR-16 (docs/DECISIONS.md) - HTTP client for the Flask AI service (§12). "Mode
 * désactivé" is a hard gate enforced here, before any network attempt:
 * SMARTFLOW_AI_ENABLED=false (docker-compose.yml) throws AI_DISABLED synchronously,
 * exactly what §12.2 means by "l'application principale doit rester utilisable sans le
 * service IA" - a caller never waits on a connection attempt to a service that was
 * deliberately turned off.
 */
@Component
public class AiClient {

    private final RestClient restClient;
    private final boolean enabled;

    public AiClient(@Value("${smartflow.ai.base-url}") String baseUrl,
                     @Value("${smartflow.ai.enabled:false}") boolean enabled) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        // RestClient.builder() directly, not an injected RestClient.Builder bean:
        // RestClientAutoConfiguration's own bean is not reliably present in every test
        // slice, and this component needs nothing from it beyond a plain builder.
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * §6.10/§15.3 - "page de diagnostic affichant l'état des services techniques", jamais
     * une exception : contrairement à classify/summarize, un appelant de diagnostic veut
     * savoir que le service est injoignable, pas se le faire lancer comme une erreur.
     * `false` sans appel réseau si le mode désactivé (§12.2) est actif - la page de
     * diagnostic doit distinguer "désactivé" de "injoignable" (DiagnosticsService's own
     * javadoc), donc n'appelle jamais cette méthode quand isEnabled() est déjà false.
     */
    public boolean ping() {
        if (!enabled) {
            return false;
        }
        try {
            restClient.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    /** §12.1 - catégorie + priorité suggérées ; ADR-16 puis ADR-21 (EVALUATION.md) - côté
     * Flask, la catégorie vient du MlClassifier et la priorité du RuleBasedClassifier. */
    public ClassificationResult classify(String title, String description) {
        requireEnabled();
        ClassifyApiResponse response = call(() -> restClient.post().uri("/classify")
                .body(Map.of("title", title == null ? "" : title, "description", description == null ? "" : description))
                .retrieve().body(ClassifyApiResponse.class));
        if (response == null) {
            throw new InvalidRequestStateException("AI_SERVICE_UNAVAILABLE", "Le service IA n'a renvoyé aucun résultat.");
        }
        return new ClassificationResult(response.category(), response.categoryConfidence(),
                response.priority(), response.priorityConfidence(),
                response.categoryMethod(), response.priorityMethod());
    }

    /** §12.1 - résumé court d'une demande longue et de ses derniers échanges. */
    public String summarize(String text) {
        requireEnabled();
        SummarizeApiResponse response = call(() -> restClient.post().uri("/summarize")
                .body(Map.of("text", text == null ? "" : text))
                .retrieve().body(SummarizeApiResponse.class));
        if (response == null) {
            throw new InvalidRequestStateException("AI_SERVICE_UNAVAILABLE", "Le service IA n'a renvoyé aucun résultat.");
        }
        return response.summary();
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new InvalidRequestStateException("AI_DISABLED",
                    "Le module d'intelligence artificielle est désactivé (§12.2).");
        }
    }

    private <T> T call(java.util.function.Supplier<T> httpCall) {
        try {
            return httpCall.get();
        } catch (RestClientException e) {
            throw new InvalidRequestStateException("AI_SERVICE_UNAVAILABLE",
                    "Le service IA est momentanément indisponible.");
        }
    }

    /**
     * ADR-21 - les deux suggestions ne viennent plus de la même stratégie côté Flask
     * (catégorie : ML ; priorité : règles), donc chacune porte sa propre confiance *et* sa
     * propre provenance. Les fondre en un seul chiffre reviendrait à moyenner deux mesures
     * qui n'ont pas la même échelle ni la même fiabilité - or c'est précisément ce qu'un
     * agent doit pouvoir départager avant d'accepter l'une sans l'autre (RG-10).
     */
    public record ClassificationResult(String category, double categoryConfidence, String priority,
                                        double priorityConfidence, String categoryMethod,
                                        String priorityMethod) {
    }

    /**
     * ADR-21 - `method` dit quelle stratégie a répondu ("hybrid"), `categoryMethod` et
     * `priorityMethod` d'où vient chacune des deux suggestions prises séparément : depuis
     * que les deux cibles n'ont plus la même source côté Flask, un seul mot ne suffit plus.
     * Ces trois champs sont déclarés pour que ce client décrive fidèlement le corps qu'il
     * reçoit ; ils ne sont pas encore stockés sur AiAnalysis, ce qui demanderait une
     * migration et une passe à part.
     */
    private record ClassifyApiResponse(String category, double categoryConfidence, String priority,
                                        double priorityConfidence, String method, String categoryMethod,
                                        String priorityMethod) {
    }

    private record SummarizeApiResponse(String summary, String method) {
    }
}
