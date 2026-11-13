package co.registraduria.keycloak;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.jboss.logging.Logger;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.util.JsonSerialization;

/**
 * Login automático para OpenID4VCI: la oferta de credencial lleva un {@code issuer_state} de un solo uso; la wallet lo
 * reenvía en la petición de autorización (directa o vía PAR). Aquí se canjea en el issuer (que lo consume) y se autentica al
 * titular de esa cédula sin mostrar formulario. El flujo es SOLO por ticket: sin issuer_state, o con un ticket inválido,
 * vencido o ya usado, el acceso se niega (no hay formulario de usuario y contraseña de respaldo: la cédula no es un
 * secreto). La identidad la prueba quien crea el ticket (onboarding aprobado o código de activación de la Registraduría).
 */
public class IssuerStateAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(IssuerStateAuthenticator.class);
    private static final String PARAM = "issuer_state";
    private static final String CLIENT_PARAM_PREFIX = "client_request_param_";
    private static final String DEFAULT_URL = "http://pid-issuer:8080/pid-issuer/internal/v1/tickets/redeem";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String ticket = authSession.getClientNote(CLIENT_PARAM_PREFIX + PARAM);
        if (ticket == null || ticket.isBlank()) {
            LOG.warnf("Sin %s en la petición: acceso denegado", PARAM);
            deny(context);
            return;
        }
        try {
            Map<String, Object> holder = redeem(ticket);
            if (holder == null) {
                deny(context);
                return;
            }
            UserModel user = findOrCreateUser(context.getSession(), context.getRealm(), holder);
            if (user == null || !user.isEnabled()) {
                LOG.warn("Titular del ticket no disponible o deshabilitado: acceso denegado");
                deny(context);
                return;
            }
            context.setUser(user);
            context.success();
        } catch (Exception e) {
            LOG.warnf(e, "No se pudo canjear el issuer_state (%s): acceso denegado", e.getClass().getSimpleName());
            deny(context);
        }
    }

    /** Sin ticket válido no hay sesión: error de acceso, nunca un formulario. */
    private static void deny(AuthenticationFlowContext context) {
        context.failureChallenge(AuthenticationFlowError.ACCESS_DENIED,
                context.form().setError("accessDenied").createErrorPage(Response.Status.FORBIDDEN));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> redeem(String ticket) throws Exception {
        String url = envOr("ISSUER_TICKET_REDEEM_URL", DEFAULT_URL);
        String apiKey = envOr("ISSUER_TICKET_API_KEY", "");
        String body = JsonSerialization.writeValueAsString(Map.of("ticket", ticket));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Api-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            LOG.warnf("El issuer rechazó el ticket (HTTP %d)", response.statusCode());
            return null;
        }
        Map<String, Object> holder = JsonSerialization.readValue(response.body(), Map.class);
        Object doc = holder.get("documentNumber");
        if (!(doc instanceof String s) || s.isBlank()) {
            LOG.warn("Respuesta del issuer sin documentNumber");
            return null;
        }
        return holder;
    }

    private UserModel findOrCreateUser(KeycloakSession session, RealmModel realm, Map<String, Object> holder) {
        String username = ((String) holder.get("documentNumber")).trim();
        UserModel user = session.users().getUserByUsername(realm, username);
        if (user != null) {
            return user;
        }
        // En emisores donde el usuario lo mantiene un panel (p. ej. la UIS) no se crea nada aquí: sin usuario, sin acceso.
        if ("false".equalsIgnoreCase(envOr("ISSUER_STATE_CREATE_USERS", "true"))) {
            LOG.warnf("El titular del ticket (%s) no existe en el realm y no se crean usuarios aquí", username);
            return null;
        }
        // Sin credenciales ni required actions: solo se puede entrar con un ticket válido del issuer.
        user = session.users().addUser(realm, null, username, true, false);
        user.setEnabled(true);
        user.setEmailVerified(true);
        setIfPresent(holder, "givenName", user::setFirstName);
        setIfPresent(holder, "familyName", user::setLastName);
        setAttr(user, "birthdate", holder.get("birthDate"));
        setAttr(user, "nationality", holder.getOrDefault("nationality", "CO"));
        setAttr(user, "birth_place", holder.get("birthPlace"));
        setAttr(user, "birth_city", holder.get("birthPlace"));
        setAttr(user, "birth_state", holder.get("birthRegion"));
        setAttr(user, "birth_country", "Colombia");
        setAttr(user, "gender", holder.get("gender"));
        setAttr(user, "gender_as_string", holder.get("gender"));
        LOG.infof("Usuario creado a partir del ticket del issuer (username=%s)", username);
        return user;
    }

    private static void setIfPresent(Map<String, Object> m, String key, java.util.function.Consumer<String> setter) {
        Object v = m.get(key);
        if (v instanceof String s && !s.isBlank()) {
            setter.accept(s);
        }
    }

    private static void setAttr(UserModel user, String name, Object value) {
        if (value instanceof String s && !s.isBlank()) {
            user.setSingleAttribute(name, s);
        }
    }

    private static String envOr(String name, String def) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? def : v;
    }

    @Override public void action(AuthenticationFlowContext context) { deny(context); }
    @Override public boolean requiresUser() { return false; }
    @Override public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) { return true; }
    @Override public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) { }
    @Override public void close() { }
}
