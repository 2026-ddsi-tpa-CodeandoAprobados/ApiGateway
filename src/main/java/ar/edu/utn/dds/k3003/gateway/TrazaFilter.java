package ar.edu.utn.dds.k3003.gateway;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Mantiene la traza de punta a punta. Si el pedido trae un X-Trace-Id valido lo respeta; si no,
 * genera uno. Lo reenvia al modulo y lo devuelve en la respuesta, de modo que el mismo identificador
 * aparece en los logs del gateway y en los de cada modulo. Usa la misma regla que los modulos:
 * de 1 a 64 caracteres entre letras, numeros, punto, guion y guion bajo.
 */
@Component
public class TrazaFilter implements GlobalFilter, Ordered {

  public static final String ENCABEZADO = "X-Trace-Id";
  private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Logger log = LoggerFactory.getLogger(TrazaFilter.class);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String recibido = exchange.getRequest().getHeaders().getFirst(ENCABEZADO);
    String traceId = esValido(recibido) ? recibido : UUID.randomUUID().toString();

    ServerHttpRequest pedido = exchange.getRequest().mutate()
        .headers(h -> h.set(ENCABEZADO, traceId))
        .build();

    // La respuesta del modulo ya puede traer su propio X-Trace-Id: se pisa justo antes de enviarla.
    exchange.getResponse().beforeCommit(() -> {
      exchange.getResponse().getHeaders().set(ENCABEZADO, traceId);
      return Mono.empty();
    });

    long inicio = System.currentTimeMillis();
    return chain.filter(exchange.mutate().request(pedido).build())
        .doFinally(senal -> {
          if (!pedido.getPath().value().startsWith("/actuator")) {
            log.info("traceId={} {} {} -> {} en {} ms", traceId, pedido.getMethod(),
                pedido.getURI().getRawPath(), exchange.getResponse().getStatusCode(),
                System.currentTimeMillis() - inicio);
          }
        });
  }

  static boolean esValido(String valor) {
    return valor != null && VALIDO.matcher(valor).matches();
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
