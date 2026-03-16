package com.example.demo;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.util.Base64;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/safe")
public class BuenaPracVulnerable {

    // SOLUCIÓN 2: Los secretos se inyectan desde variables de entorno o Vault, nunca en el código.
    @Value("${app.aws.key:NOT_SET}")
    private String awsKey;

    /**
     * 1. SOLUCIÓN A SQL INJECTION: Consultas Parametrizadas.
     */
    @GetMapping("/user")
    public String getUser(@RequestParam String id) {
        // En un escenario real con JPA/Hibernate: userRepository.findById(id);
        // Aquí simulamos el uso de parámetros '?' que el driver SQL limpia automáticamente.
        String safeQuery = "SELECT * FROM users WHERE id = ?"; 
        return "Log: Usando PreparedStatement con el parámetro seguro: " + id;
    }

    /**
     * 2. SOLUCIÓN A SECRETS: Uso de anotaciones @Value.
     */
    @GetMapping("/config")
    public String getConfig() {
        return "Configuración cargada de forma segura desde el entorno.";
    }

    /**
     * 3. SOLUCIÓN A COMMAND INJECTION: Whitelisting y uso de APIs nativas.
     */
    @GetMapping("/ping")
    public String safePing(@RequestParam String host) {
        // Validamos que la entrada sea estrictamente una IP o nombre de host con Regex
        if (!Pattern.matches("^[a-zA-Z0-9.-]+$", host)) {
            return "Error: Caracteres no permitidos detectados.";
        }
        
        // En lugar de Runtime.exec(), se deberían usar librerías nativas como InetAddress
        return "Simulando ping seguro a: " + host;
    }

    /**
     * 4. SOLUCIÓN A XSS: Escapado de caracteres HTML (Sanitización).
     */
    @GetMapping("/welcome")
    public String welcome(@RequestParam String name) {
        // HtmlUtils.htmlEscape convierte <script> en &lt;script&gt;
        // Esto hace que el navegador lo imprima como texto en lugar de ejecutarlo.
        String cleanName = HtmlUtils.htmlEscape(name);
        return "<html><body><h1>Bienvenido " + cleanName + "</h1></body></html>";
    }

    /**
     * 5. SOLUCIÓN A DATA PROCESSING: Validación de entrada Base64.
     */
    @GetMapping("/decode")
    public String decode(@RequestParam String data) {
        try {
            // Validamos que sea un formato Base64 válido antes de procesar
            byte[] decodedBytes = Base64.getDecoder().decode(data);
            String result = new String(decodedBytes);
            
            // Sanitizamos el resultado por si el contenido decodificado trae scripts (XSS secundario)
            return "Dato decodificado y sanitizado: " + HtmlUtils.htmlEscape(result);
        } catch (IllegalArgumentException e) {
            return "Error: Formato de datos inválido.";
        }
    }
}