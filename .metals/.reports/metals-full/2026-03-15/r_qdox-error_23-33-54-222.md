error id: file:///D:/Proyectos/Diplomado/Modulo%205/modulofinal/src/main/java/com/example/demo/VulnerableController.java
file:///D:/Proyectos/Diplomado/Modulo%205/modulofinal/src/main/java/com/example/demo/VulnerableController.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[1,1]

error in qdox parser
file content:
```java
offset: 1
uri: file:///D:/Proyectos/Diplomado/Modulo%205/modulofinal/src/main/java/com/example/demo/VulnerableController.java
text:
```scala
a@@ckage com.example.demo;

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
```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:560)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3(Indexer.scala:691)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3$adapted(Indexer.scala:688)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1313)
	scala.meta.internal.metals.Indexer.reindexWorkspaceSources(Indexer.scala:688)
	scala.meta.internal.metals.MetalsLspService.$anonfun$onChange$2(MetalsLspService.scala:940)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:691)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:500)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	java.base/java.lang.Thread.run(Thread.java:840)
```
#### Short summary: 

QDox parse error in file:///D:/Proyectos/Diplomado/Modulo%205/modulofinal/src/main/java/com/example/demo/VulnerableController.java