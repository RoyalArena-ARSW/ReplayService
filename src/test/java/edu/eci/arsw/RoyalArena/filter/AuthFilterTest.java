package edu.eci.arsw.RoyalArena.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;

import edu.eci.arsw.RoyalArena.config.SecurityConfig;
import edu.eci.arsw.RoyalArena.filter.AuthFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

/**
 * Tests del filtro de autenticación del Gateway.
 *
 * Es el punto más crítico de todo el sistema: si deja pasar un token inválido,
 * cualquiera puede hacerse pasar por cualquiera. Si bloquea uno válido, nadie
 * entra. Y es el único lugar donde se valida el JWT — los microservicios de
 * atrás confían ciegamente en el header X-User-Id que este filtro inyecta.
 *
 * Ajusta los nombres de los campos (jwtSecret, internalSecret, publicPaths) si
 * en tu AuthFilter se llaman distinto.
 */
class AuthFilterTest {

    private static final String SECRET =
            "RoyalArena2026SuperLongSecretKeyForHmacSha512JwtValidation!!!!!";
    private static final String INTERNAL_SECRET = "royalarena-internal-secret-dev";

    private AuthFilter authFilter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityConfig config = new SecurityConfig();
        authFilter = new AuthFilter(config);
        ReflectionTestUtils.setField(authFilter, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(authFilter, "internalSecret", INTERNAL_SECRET);
        ReflectionTestUtils.setField(authFilter, "publicPaths", List.of(
                "POST /api/auth/register",
                "POST /api/auth/login",
                "GET /api/cards",
                "GET /api/profiles/leaderboard"));

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // ===== Helpers =====

    private SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    /** Token con userId como STRING (el formato que el filtro sabe leer hoy). */
    private String tokenWithStringUserId(long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userId", String.valueOf(userId))
                .claim("username", "diegoortiz")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key())
                .compact();
    }

    private String expiredToken() {
        long past = System.currentTimeMillis() - 7_200_000;
        return Jwts.builder()
                .subject("42")
                .claim("userId", "42")
                .claim("role", "PLAYER")
                .issuedAt(new Date(past))
                .expiration(new Date(past + 3_600_000)) // venció hace una hora
                .signWith(key())
                .compact();
    }

    private MockServerWebExchange getWithToken(String path, String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header("Authorization", "Bearer " + token)
                        .build());
    }

    private String bodyOf(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }

    // ===== Rutas públicas =====

    @Test
    @DisplayName("Una ruta publica pasa sin token")
    void publicPathPassesWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cards").build());

        authFilter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("El login es publico: sin el, seria imposible autenticarse")
    void loginIsPublic() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build());

        authFilter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Una ruta protegida sin token: 401 y no llega al microservicio")
    void protectedPathWithoutTokenIsBlocked() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/profiles/me").build());

        authFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(exchange)).contains("TOKEN_NO_ENCONTRADO");
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Un header Authorization malformado se rechaza")
    void malformedAuthHeaderIsRejected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/profiles/me")
                        .header("Authorization", "eyJhbGciOiJIUzI1NiJ9.abc") // sin "Bearer "
                        .build());

        authFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    // ===== Bypass interno =====

    @Test
    @DisplayName("El X-Internal-Secret correcto deja pasar sin token")
    void validInternalSecretBypassesAuth() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/troops")
                        .header("X-Internal-Secret", INTERNAL_SECRET)
                        .build());

        authFilter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Un X-Internal-Secret incorrecto NO abre la puerta")
    void wrongInternalSecretDoesNotBypass() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/troops")
                        .header("X-Internal-Secret", "adivinanza-del-atacante")
                        .build());

        authFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    // ===== Validación del token =====

    @Test
    @DisplayName("Un token expirado da TOKEN_EXPIRADO, no TOKEN_INVALIDO")
    void expiredTokenGivesSpecificError() {
        MockServerWebExchange exchange = getWithToken("/api/profiles/me", expiredToken());

        authFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Distinguirlos importa: el frontend puede renovar sesión en vez de
        // mandar al usuario al login.
        assertThat(bodyOf(exchange)).contains("TOKEN_EXPIRADO");
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Un token firmado con otro secreto se rechaza")
    void tokenSignedWithForeignSecretIsRejected() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "SecretoDeUnAtacanteQueTieneSesentaYCuatroCaracteresParaHS512XX"
                        .getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject("1").claim("userId", "1").claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(otherKey)
                .compact();

        MockServerWebExchange exchange = getWithToken("/api/profiles/me", forged);
        authFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyOf(exchange)).contains("TOKEN_INVALIDO");
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Basura en vez de token se rechaza sin tumbar el Gateway")
    void garbageTokenIsRejectedGracefully() {
        MockServerWebExchange exchange = getWithToken("/api/profiles/me", "esto-no-es-un-jwt");

        authFilter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    // ===== Inyección de headers =====

    /**
     * El corazón del contrato: los microservicios de atrás leen X-User-Id sin
     * validar nada. Si el filtro no lo inyecta bien, o Profile devuelve el
     * perfil equivocado, o revienta.
     */
    @Test
    @DisplayName("Un token valido inyecta X-User-Id y X-User-Role al request")
    void validTokenInjectsIdentityHeaders() {
        MockServerWebExchange exchange =
                getWithToken("/api/profiles/me", tokenWithStringUserId(42L, "PLAYER"));

        authFilter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor =
                ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        var headers = captor.getValue().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("42");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("PLAYER");
    }

    /**
     * Un usuario no debe poder inyectarse su propia identidad. Si mandara
     * X-User-Id: 1 a mano y el filtro no lo sobreescribiera, se haría pasar
     * por otro con solo un header.
     */
    @Test
    @DisplayName("Un X-User-Id enviado por el cliente NO sobrevive al filtro")
    void clientSuppliedUserIdIsOverwritten() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/profiles/me")
                        .header("Authorization", "Bearer " + tokenWithStringUserId(42L, "PLAYER"))
                        .header("X-User-Id", "1")        // ← intento de suplantación
                        .header("X-User-Role", "ADMIN")  // ← intento de escalar privilegios
                        .build());

        authFilter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor =
                ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        var headers = captor.getValue().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id"))
                .as("debe mandar el token, no lo que diga el cliente")
                .isEqualTo("42");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("PLAYER");
    }

    // ===== ⚠️ EL BUG DEL /me =====

    /**
     * ESTE TEST FALLA HOY. Es su trabajo: señalar el bug.
     *
     * Auth emite el token con .claim("userId", user.getId()) → un Long → que
     * viaja como NÚMERO en el JSON. Al parsearlo, JJWT lo devuelve como
     * Integer, nunca como String.
     *
     * Y el filtro hace (línea ~113):
     *     String userId = claims.get("userId", String.class);
     *
     * Eso NO devuelve null: lanza RequiredTypeException. Y como esa línea está
     * FUERA del try-catch que envuelve el parseo, la excepción propaga sin
     * manejar → el GlobalErrorHandler la convierte en 500.
     *
     * Ese es, exactamente, el 500 de GET /api/profiles/me que llevamos días
     * arrastrando.
     *
     * FIX en AuthFilter, reemplazar la línea 113-116 por:
     *     Object userIdClaim = claims.get("userId");
     *     String userId = (userIdClaim != null)
     *             ? String.valueOf(userIdClaim)
     *             : claims.getSubject();
     *
     * Con eso este test pasa.
     */
    @Test
    @DisplayName("Un token REAL de Auth (userId numerico) debe funcionar")
    void tokenFromAuthWithNumericUserIdMustWork() {
        // Token idéntico al que emite tu JwtService de Auth
        String realToken = Jwts.builder()
                .subject("42")
                .claim("userId", 42L)          // ← Long: así lo pone Auth
                .claim("username", "diegoortiz")
                .claim("role", "PLAYER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key())
                .compact();

        MockServerWebExchange exchange = getWithToken("/api/profiles/me", realToken);

        authFilter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor =
                ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-User-Id"))
                .as("el userId numerico debe traducirse a String, no explotar")
                .isEqualTo("42");
    }

    /**
     * Documenta el mecanismo exacto de la falla. Este SÍ pasa hoy — y cuando
     * apliques el fix, hay que borrarlo (dejará de aplicar).
     */
    @Test
    @DisplayName("BUG documentado: el claim numerico lanza RequiredTypeException sin manejar")
    void numericClaimThrowsUnhandled() {
        String realToken = Jwts.builder()
                .subject("42").claim("userId", 42L).claim("role", "PLAYER")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key()).compact();

        MockServerWebExchange exchange = getWithToken("/api/profiles/me", realToken);

        // RequiredTypeException extiende JwtException, pero se lanza FUERA del
        // try-catch → propaga → 500 en vez de 401 o de funcionar.
        assertThatThrownBy(() -> authFilter.filter(exchange, chain).block())
                .isInstanceOf(io.jsonwebtoken.RequiredTypeException.class);
    }
}