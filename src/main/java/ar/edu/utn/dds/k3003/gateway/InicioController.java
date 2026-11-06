package ar.edu.utn.dds.k3003.gateway;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Pagina de inicio del gateway: lista a que modulo va cada prefijo. */
@RestController
public class InicioController {

  private final RouteDefinitionLocator rutas;

  public InicioController(RouteDefinitionLocator rutas) {
    this.rutas = rutas;
  }

  @GetMapping("/")
  public Mono<Map<String, Object>> inicio() {
    return rutas.getRouteDefinitions()
        .map(InicioController::describir)
        .collectList()
        .map(lista -> {
          Map<String, Object> respuesta = new LinkedHashMap<>();
          respuesta.put("servicio", "DonaTrack API Gateway");
          respuesta.put("uso", "Anteponer el prefijo del modulo: /api/{modulo}/{ruta del modulo}");
          respuesta.put("rutas", (List<Map<String, String>>) lista);
          return respuesta;
        });
  }

  private static Map<String, String> describir(RouteDefinition definicion) {
    Map<String, String> fila = new LinkedHashMap<>();
    fila.put("modulo", definicion.getId());
    fila.put("prefijo", definicion.getPredicates().stream()
        .flatMap(p -> p.getArgs().values().stream())
        .findFirst().orElse(""));
    fila.put("destino", definicion.getUri().toString());
    return fila;
  }
}
