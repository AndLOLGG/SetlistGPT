// java
package dk.ek.setlistgpt.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.Map;

public class JsonUsernamePasswordAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        if (request.getContentType() != null && request.getContentType().toLowerCase().contains("application/json")) {
            try {
                Map<String, Object> data = mapper.readValue(request.getInputStream(), Map.class);
                String username = data.getOrDefault("username", "").toString();
                String password = data.getOrDefault("password", "").toString();
                username = username == null ? "" : username.trim();
                UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(username, password);
                setDetails(request, authRequest);
                return this.getAuthenticationManager().authenticate(authRequest);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to parse login JSON", ex);
            }
        }
        return super.attemptAuthentication(request, response);
    }
}
