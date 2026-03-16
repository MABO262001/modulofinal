package com.example.demo;

import org.springframework.web.bind.annotation.*;
import java.sql.*;
import java.io.*;
import java.util.Base64;

@RestController
public class VulnerableController {

    /**
     * 1. SQL INJECTION (Vulnerabilidad de Código)
     * Detectado por: Semgrep y OWASP ZAP
     */
    @GetMapping("/user")
    public String getUser(@RequestParam String id) {
        // Simulamos la query. Semgrep detectará la concatenación insegura.
        String query = "SELECT * FROM users WHERE id = '" + id + "'";
        return "Query ejecutada internamente: " + query;
    }

    /**
     * 2. HARDCODED SECRETS (Vulnerabilidad de Configuración)
     * Detectado por: Semgrep (Secret Scanning)
     */
    @GetMapping("/config")
    public String getConfig() {
        // NUNCA dejes llaves o passwords en el código
        String awsKey = "AKIAIM7654321EXAMPLE"; 
        String secret = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
        return "Configuración cargada correctamente.";
    }

    /**
     * 3. OS COMMAND INJECTION (Vulnerabilidad Crítica)
     * Detectado por: Semgrep
     */
    @GetMapping("/ping")
    public String ping(@RequestParam String host) throws IOException {
        // El usuario puede inyectar "; rm -rf /"
        Process process = Runtime.getRuntime().exec("ping -c 1 " + host);
        return "Comando enviado al sistema operativo.";
    }

    /**
     * 4. REFLECTED XSS (Vulnerabilidad de Interfaz)
     * Detectado por: OWASP ZAP
     */
    @GetMapping("/welcome")
    public String welcome(@RequestParam String name) {
        // No hay sanitización. Si envían <script>alert(1)</script>, se ejecutará en el navegador.
        return "<html><body><h1>Bienvenido " + name + "</h1></body></html>";
    }

    /**
     * 5. INSECURE DESERIALIZATION / BASE64 (Vulnerabilidad de Datos)
     * Detectado por: Semgrep / Snyk
     */
    @GetMapping("/decode")
    public String decode(@RequestParam String data) {
        // Decodificar datos directamente del usuario sin validar contenido es peligroso
        byte[] decodedBytes = Base64.getDecoder().decode(data);
        return "Dato procesado: " + new String(decodedBytes);
    }
}