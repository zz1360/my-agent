package com.superagent.logistics.config;

import com.superagent.logistics.security.AgentApiKeyAuthenticationFilter;
import com.superagent.logistics.security.AgentAuthenticationResolver;
import com.superagent.logistics.security.AgentOidcUserService;
import com.superagent.logistics.security.TrustedAgentIdentityFilter;
import com.superagent.logistics.security.EnterpriseSecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({EnterpriseSecurityProperties.class, DeploymentProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AgentApiKeyAuthenticationFilter apiKeyFilter,
                                                   TrustedAgentIdentityFilter trustedIdentityFilter,
                                                   AgentOidcUserService oidcUserService,
                                                   EnterpriseSecurityProperties properties) throws Exception {
        if (properties.isOidcEnabled()) {
            AuthenticationEntryPoint apiEntryPoint = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
            CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
            csrfRepository.setCookiePath("/");
            return http
                    .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                            .ignoringRequestMatchers("/api/ops/frontend-events"))
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/api/agent/security/**", "/api/ops/frontend-events",
                                    "/oauth2/**", "/login/**", "/error").permitAll()
                            .requestMatchers("/assets/**", "/", "/index.html", "/favicon.ico").permitAll()
                            .anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                            apiEntryPoint, request -> request.getRequestURI().startsWith("/api/")))
                    .oauth2Login(oauth -> oauth
                            .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                            .defaultSuccessUrl(properties.getPostLoginRedirect(), true))
                    .logout(logout -> logout
                            .logoutUrl("/api/agent/security/logout")
                            .clearAuthentication(true)
                            .invalidateHttpSession(true)
                            .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                            .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                    .addFilterAfter(trustedIdentityFilter, AnonymousAuthenticationFilter.class)
                    .build();
        }
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AgentApiKeyAuthenticationFilter apiKeyAuthenticationFilter(EnterpriseSecurityProperties properties) {
        return new AgentApiKeyAuthenticationFilter(properties);
    }

    @Bean
    public AgentOidcUserService agentOidcUserService(EnterpriseSecurityProperties properties,
                                                     AgentAuthenticationResolver resolver) {
        return new AgentOidcUserService(properties, resolver);
    }

    @Bean
    public TrustedAgentIdentityFilter trustedAgentIdentityFilter(EnterpriseSecurityProperties properties,
                                                                 AgentAuthenticationResolver resolver) {
        return new TrustedAgentIdentityFilter(properties, resolver);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }
}
