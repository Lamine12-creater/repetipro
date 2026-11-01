package bj.repetipro.paiement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Appels REST à FedaPay, sans SDK (il n'existe pas de SDK Java officiel).
 *   POST /v1/transactions              crée la transaction
 *   POST /v1/transactions/{id}/token   donne le lien de la page de paiement
 *   GET  /v1/transactions/{id}         relit le statut réel
 * Doc : https://docs.fedapay.com/fr/integration-api/collects-management.md
 *       https://docs.fedapay.com/api-reference/transactions/create.md
 *       https://docs.fedapay.com/api-reference/transactions/create-token.md
 */
@Component
public class FedaPayClient implements FournisseurPaiement {

    public static final String CODE = "FEDAPAY";

    private final RestClient http;
    private final ConfigPaiement config;
    private final ObjectMapper json;

    public FedaPayClient(RestClient.Builder builder, ConfigPaiement config, ObjectMapper json) {
        this.http = builder.baseUrl(config.urlApiFedaPay()).build();
        this.config = config;
        this.json = json;
    }

    @Override public String code() { return CODE; }

    @Override
    public Checkout creer(Demande d) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("description", d.description());
        corps.put("amount", d.montant());
        corps.put("currency", Map.of("iso", "XOF"));
        corps.put("callback_url", d.urlRetour());
        corps.put("merchant_reference", d.reference());
        corps.put("custom_metadata", Map.of("repetiteur_id", String.valueOf(d.repetiteurId()), "formule", "solo"));
        JsonNode transaction = transaction(appel(() -> http.post().uri("/transactions")
                .headers(h -> h.setBearerAuth(config.cleSecrete()))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(corps).retrieve().body(String.class)));
        String id = texte(transaction, "id");
        if (id == null) throw new FournisseurException("Réponse FedaPay sans identifiant de transaction");

        JsonNode jeton = lire(appel(() -> http.post().uri("/transactions/{id}/token", id)
                .headers(h -> h.setBearerAuth(config.cleSecrete()))
                .accept(MediaType.APPLICATION_JSON).retrieve().body(String.class)));
        String url = texte(jeton, "url");
        if (url == null || !url.startsWith("https://")) throw new FournisseurException("Réponse FedaPay sans lien de paiement");
        return new Checkout(id, url);
    }

    @Override
    public Optional<EtatDistant> consulter(String referenceFournisseur) {
        JsonNode t = transaction(appel(() -> http.get().uri("/transactions/{id}", referenceFournisseur)
                .headers(h -> h.setBearerAuth(config.cleSecrete()))
                .accept(MediaType.APPLICATION_JSON).retrieve().body(String.class)));
        return Optional.of(new EtatDistant(texte(t, "status"), t.path("amount").asLong(-1), texte(t, "mode")));
    }

    // --- outils ---

    private String appel(java.util.function.Supplier<String> requete) {
        try {
            return requete.get();
        } catch (RestClientException e) {
            // Jamais la clé dans les messages : seulement le type d'erreur.
            throw new FournisseurException("Appel FedaPay impossible : " + e.getClass().getSimpleName(), e);
        }
    }

    private JsonNode lire(String brut) {
        try {
            return json.readTree(brut == null ? "{}" : brut);
        } catch (Exception e) {
            throw new FournisseurException("Réponse FedaPay illisible", e);
        }
    }

    /** FedaPay renvoie {"v1/transaction": {...}} ; on accepte aussi l'objet nu. */
    JsonNode transaction(String brut) {
        JsonNode n = lire(brut);
        return n.has("v1/transaction") ? n.get("v1/transaction") : n;
    }

    static String texte(JsonNode n, String champ) {
        JsonNode v = n == null ? null : n.get(champ);
        return v == null || v.isNull() ? null : v.asText();
    }
}
