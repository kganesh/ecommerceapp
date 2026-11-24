package com.kganesh.ecommerce.security;

import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.util.UrlUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for sign-in failing in Codespaces: the backend saw the callback as
 * http://localhost:8080/... while redirect_uri was https://<codespace>-5173.app.github.dev/...
 */
class PublicBaseUrlFilterTest {

    private static String seenUrl(String publicBaseUrl, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setQueryString("code=abc&state=xyz");
        AtomicReference<ServletRequest> seen = new AtomicReference<>();
        new PublicBaseUrlFilter(publicBaseUrl)
                .doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set(req));
        jakarta.servlet.http.HttpServletRequest r = (jakarta.servlet.http.HttpServletRequest) seen.get();
        return UrlUtils.buildFullRequestUrl(r.getScheme(), r.getServerName(), r.getServerPort(), r.getRequestURI(), null);
    }

    @Test
    void callbackLooksLikeTheCodespacesUrlTheBrowserUsed() throws Exception {
        assertThat(seenUrl("https://redesigned-parakeet-5173.app.github.dev", "/login/oauth2/code/idp"))
                .isEqualTo("https://redesigned-parakeet-5173.app.github.dev/login/oauth2/code/idp");
    }

    @Test
    void callbackLooksLikeLocalViteUrl() throws Exception {
        assertThat(seenUrl("http://localhost:5173", "/login/oauth2/code/idp"))
                .isEqualTo("http://localhost:5173/login/oauth2/code/idp");
    }

    @Test
    void otherPathsAreLeftAlone() throws Exception {
        assertThat(seenUrl("https://redesigned-parakeet-5173.app.github.dev", "/api/v1/me"))
                .isEqualTo("http://localhost:8080/api/v1/me");
    }
}
