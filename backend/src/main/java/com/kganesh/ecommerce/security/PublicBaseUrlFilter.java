package com.kganesh.ecommerce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

/**
 * Makes OIDC login requests look as the browser sent them.
 *
 * <p>Spring Security checks that the callback URL it receives equals the {@code redirect_uri}
 * it sent to the identity provider, and rebuilds that URL from the request. Behind the Vite
 * proxy, the Codespaces port forwarder or (later) CloudFront, the backend sees
 * {@code http://localhost:8080/...} while the browser used e.g.
 * {@code https://<codespace>-5173.app.github.dev/...}, so sign-in failed with
 * {@code invalid_redirect_uri_parameter}. For the login paths only, this filter reports
 * scheme, host and port from {@code app.public-base-url} instead of trusting forwarded
 * headers.
 */
final class PublicBaseUrlFilter extends OncePerRequestFilter {

    private final String scheme;
    private final String host;
    private final int port;

    PublicBaseUrlFilter(String publicBaseUrl) {
        URI base = URI.create(publicBaseUrl);
        this.scheme = base.getScheme();
        this.host = base.getHost();
        this.port = base.getPort() != -1 ? base.getPort() : ("https".equalsIgnoreCase(scheme) ? 443 : 80);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(new PublicRequest(request), response);
    }

    private final class PublicRequest extends HttpServletRequestWrapper {

        PublicRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getScheme() {
            return scheme;
        }

        @Override
        public String getServerName() {
            return host;
        }

        @Override
        public int getServerPort() {
            return port;
        }

        @Override
        public boolean isSecure() {
            return "https".equalsIgnoreCase(scheme);
        }

        @Override
        public StringBuffer getRequestURL() {
            boolean defaultPort = ("https".equalsIgnoreCase(scheme) && port == 443)
                    || ("http".equalsIgnoreCase(scheme) && port == 80);
            StringBuffer url = new StringBuffer(scheme).append("://").append(host);
            if (!defaultPort) {
                url.append(':').append(port);
            }
            return url.append(getRequestURI());
        }
    }
}
