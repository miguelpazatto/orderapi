package com.miguelpazatto.orderapi.infra.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityFilter securityFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // 1. Autenticação (Público)
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()

                        // 2. Vitrine de Produtos (Leitura pública, mutações exclusivas para ADMIN)
                        .requestMatchers(HttpMethod.GET, "/products").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/products").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/products/**").hasRole("ADMIN")

                        // 3. Gestão Logística de Pedidos (Apenas ADMIN envia e entrega)
                        .requestMatchers(HttpMethod.PATCH, "/orders/*/ship").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/orders/*/deliver").hasRole("CUSTOMER")

                        // 4. Pedidos Gerais (Clientes logados podem criar, listar os seus e cancelar)
                        .requestMatchers("/orders/**").authenticated()

                        // 5. Clientes / Backoffice (Regras mais rígidas para gestão de clientes)
                        .requestMatchers(HttpMethod.GET, "/customers").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/customers/**").hasRole("ADMIN")
                        .requestMatchers("/customers/**").authenticated()

                        // 6. Stripe / Recebimento de pagamentos
                        .requestMatchers(HttpMethod.POST, "/webhooks/stripe").permitAll()

                        // 6. Fechadura de segurança padrão (Qualquer rota não mapeada acima exige token)
                        .anyRequest().authenticated()
                )
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}