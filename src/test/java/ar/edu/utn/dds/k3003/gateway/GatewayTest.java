package ar.edu.utn.dds.k3003.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayTest {

  /** Un servidor falso por modulo. Cada uno responde con su propio nombre para saber quien atendio. */
  private static final MockWebServer DONACIONES = modulo("donaciones");
  private static final MockWebServer DONADORES = modulo("donadores");
  private static final MockWebServer INCENTIVOS = modulo("incentivos");
  private static final MockWebServer LOGISTICA = modulo("logistica");
  private static final MockWebServer CONSULTAS = modulo("consultas");

  private static MockWebServer modulo(String nombre) {
    MockWebServer servidor = new MockWebServer();
    servidor.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest pedido) {
        return new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Trace-Id", "traza-del-modulo")
            .setBody("{\"atendio\":\"" + nombre + "\"}");
      }
    });
    try {
      servidor.start();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return servidor;
  }

  @DynamicPropertySource
  static void destinos(DynamicPropertyRegistry registro) {
    registro.add("DONACIONES_URL", () -> DONACIONES.url("/").toString());
    registro.add("DONADORES_URL", () -> DONADORES.url("/").toString());
    registro.add("INCENTIVOS_URL", () -> INCENTIVOS.url("/").toString());
    registro.add("LOGISTICA_URL", () -> LOGISTICA.url("/").toString());
    registro.add("CONSULTAS_URL", () -> CONSULTAS.url("/").toString());
  }

  @AfterAll
  static void apagar() throws IOException {
    for (MockWebServer s : new MockWebServer[] {DONACIONES, DONADORES, INCENTIVOS, LOGISTICA, CONSULTAS}) {
      s.shutdown();
    }
  }

  @Autowired
  private WebTestClient web;

  private static RecordedRequest ultimo(MockWebServer servidor) throws InterruptedException {
    RecordedRequest r = servidor.takeRequest(5, TimeUnit.SECONDS);
    assertNotNull(r, "el modulo no recibio ningun pedido");
    return r;
  }

  @Test
  void cadaPrefijoVaAlModuloCorrectoYSeLeSacaElPrefijo() throws Exception {
    verificar("/api/donaciones/productos/7", "donaciones", DONACIONES, "/productos/7");
    verificar("/api/donadores/necesidades/12", "donadores", DONADORES, "/necesidades/12");
    verificar("/api/incentivos/incentivos-donador/3/mision", "incentivos", INCENTIVOS,
        "/incentivos-donador/3/mision");
    verificar("/api/logistica/depositos", "logistica", LOGISTICA, "/depositos");
    verificar("/api/consultas/consultas/donadores/12", "consultas", CONSULTAS, "/consultas/donadores/12");
  }

  private void verificar(String ruta, String esperado, MockWebServer servidor, String rutaEnModulo)
      throws Exception {
    web.get().uri(ruta).exchange()
        .expectStatus().isOk()
        .expectBody().jsonPath("$.atendio").isEqualTo(esperado);
    assertEquals(rutaEnModulo, ultimo(servidor).getPath());
  }

  @Test
  void lasRutasRepetidasEntreModulosNoSeMezclan() throws Exception {
    web.post().uri("/api/donaciones/donaciones").contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"x\":1}").exchange()
        .expectBody().jsonPath("$.atendio").isEqualTo("donaciones");
    web.post().uri("/api/logistica/donaciones").contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"x\":1}").exchange()
        .expectBody().jsonPath("$.atendio").isEqualTo("logistica");
    assertEquals("/donaciones", ultimo(DONACIONES).getPath());
    assertEquals("/donaciones", ultimo(LOGISTICA).getPath());
  }

  @Test
  void conservaMetodoCuerpoYParametros() throws Exception {
    web.put().uri("/api/logistica/depositos/5?forzar=si").contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"nombre\":\"Central\"}").exchange().expectStatus().isOk();
    RecordedRequest r = ultimo(LOGISTICA);
    assertEquals("PUT", r.getMethod());
    assertEquals("/depositos/5?forzar=si", r.getPath());
    assertEquals("{\"nombre\":\"Central\"}", r.getBody().readUtf8());

    web.patch().uri("/api/logistica/depositos/5/algoritmo").contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"algoritmo\":\"SUB_ATENDIDOS\"}").exchange().expectStatus().isOk();
    assertEquals("PATCH", ultimo(LOGISTICA).getMethod());

    web.delete().uri("/api/logistica/testing/reset").exchange().expectStatus().isOk();
    assertEquals("DELETE", ultimo(LOGISTICA).getMethod());
  }

  @Test
  void generaUnaTrazaSiElPedidoNoLaTrae() throws Exception {
    String devuelta = web.get().uri("/api/donadores/donadores/1").exchange()
        .returnResult(String.class).getResponseHeaders().getFirst(TrazaFilter.ENCABEZADO);
    assertNotNull(devuelta);
    assertTrue(TrazaFilter.esValido(devuelta));
    // el modulo respondio "traza-del-modulo", pero el gateway devuelve la suya
    assertEquals(devuelta, ultimo(DONADORES).getHeader(TrazaFilter.ENCABEZADO));
  }

  @Test
  void respetaLaTrazaValidaYReemplazaLaInvalida() throws Exception {
    String devuelta = web.get().uri("/api/incentivos/x").header(TrazaFilter.ENCABEZADO, "demo-123")
        .exchange().returnResult(String.class).getResponseHeaders().getFirst(TrazaFilter.ENCABEZADO);
    assertEquals("demo-123", devuelta);
    assertEquals("demo-123", ultimo(INCENTIVOS).getHeader(TrazaFilter.ENCABEZADO));

    String nueva = web.get().uri("/api/incentivos/x").header(TrazaFilter.ENCABEZADO, "con espacios y $%")
        .exchange().returnResult(String.class).getResponseHeaders().getFirst(TrazaFilter.ENCABEZADO);
    assertTrue(TrazaFilter.esValido(nueva));
    assertTrue(!"con espacios y $%".equals(nueva));
    ultimo(INCENTIVOS);
  }

  @Test
  void unaRutaSinPrefijoNoLlegaAAningunModulo() {
    web.get().uri("/depositos").exchange().expectStatus().isNotFound();
    web.get().uri("/api/inexistente/x").exchange().expectStatus().isNotFound();
  }

  @Test
  void respondeSaludYLaPaginaDeInicio() {
    web.get().uri("/actuator/health").exchange().expectStatus().isOk()
        .expectBody().jsonPath("$.status").isEqualTo("UP");
    web.get().uri("/").exchange().expectStatus().isOk()
        .expectBody().jsonPath("$.rutas.length()").isEqualTo(5);
  }
}
