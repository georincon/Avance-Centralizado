package co.registraduria.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/** Registra el autenticador "issuer-state-authenticator" (login automático con issuer_state de OpenID4VCI). */
public class IssuerStateAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "issuer-state-authenticator";
    private static final IssuerStateAuthenticator SINGLETON = new IssuerStateAuthenticator();

    @Override public String getId() { return PROVIDER_ID; }
    @Override public String getDisplayType() { return "Issuer State (login automático OpenID4VCI)"; }
    @Override public String getReferenceCategory() { return "issuer-state"; }
    @Override public boolean isConfigurable() { return false; }
    @Override public boolean isUserSetupAllowed() { return false; }
    @Override public Requirement[] getRequirementChoices() {
        return new Requirement[] { Requirement.ALTERNATIVE, Requirement.DISABLED };
    }
    @Override public String getHelpText() {
        return "Si la petición de autorización trae issuer_state, lo canjea en el issuer y autentica al titular sin formulario.";
    }
    @Override public List<ProviderConfigProperty> getConfigProperties() { return List.of(); }
    @Override public Authenticator create(KeycloakSession session) { return SINGLETON; }
    @Override public void init(Config.Scope config) { }
    @Override public void postInit(KeycloakSessionFactory factory) { }
    @Override public void close() { }
}
