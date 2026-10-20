# Reto Técnico Backend - API Gateway + Microservicios + OAuth 2.0

API backend para registrar y consultar pedidos de productos. El único punto público es un API Gateway que valida seguridad (JWT), enruta solicitudes y propaga un identificador de tracking (X-Trace-Id) hasta cada microservicio.

## Arquitectura

Postman / Cliente API --> API Gateway (:8080) --> Order Service (:8081) --> Inventory Service (:8082)
^
Keycloak (:8180)  (emite y valida tokens JWT)

| Componente        | Responsabilidad                                                              | Puerto | Expuesto |
|-------------------|------------------------------------------------------------------------------|--------|----------|
| api-gateway       | Validar JWT, enrutar, generar/propagar X-Trace-Id. Sin reglas de negocio.    | 8080   | Sí       |
| order-service     | Pedidos, estados, historial de cambios de estado.                            | 8081   | No       |
| inventory-service | Productos, stock y disponibilidad.                                           | 8082   | No       |
| keycloak          | Servidor de autenticación OAuth 2.0 / OpenID Connect.                        | 8180   | Sí       |
| postgres-order    | Base de datos propia de Order Service.                                       | 5434   | Solo inspección local |
| postgres-inventory| Base de datos propia de Inventory Service.                                   | 5433   | Solo inspección local |

Decisiones principales:
- Cada microservicio es dueño de su información y de su base de datos PostgreSQL (database per service).
- Order Service consulta la disponibilidad en Inventory Service de forma síncrona con WebClient (HTTP reactivo), con timeout de 5 segundos.
- El gateway solo enruta y valida seguridad; no compone respuestas ni contiene lógica de negocio.
- Order e Inventory no publican puertos: solo son alcanzables dentro de la red Docker (reto-network), por lo tanto solo se accede a través del gateway.

## Tecnologías

- Java 21, Spring Boot
- Spring Cloud Gateway (WebFlux) para el API Gateway
- Spring Security (OAuth 2.0 Resource Server con JWT)
- Spring Data JPA + PostgreSQL 16
- WebClient (WebFlux) para la comunicación entre servicios
- Spring AOP (TraceLoggingAspect)
- MapStruct y Lombok
- Logback con X-Trace-Id en cada línea de log
- SpringDoc OpenAPI / Swagger UI
- JUnit 5
- Docker y Docker Compose
- Keycloak 26.7

## Requisitos previos

- Docker y Docker Compose
- Puertos libres: 8080, 8180, 5433 y 5434
- (Opcional) Postman para probar la colección incluida

No se necesita tener Java ni Maven instalados: las imágenes se compilan dentro de Docker.

## Cómo ejecutar

1. Crear el archivo de entorno a partir del ejemplo y definir las contraseñas:

```bash
cp .env.example .env        # PowerShell: Copy-Item .env.example .env
```

2. Levantar todo desde la raíz del proyecto:

```bash
docker compose up --build
```

Esto levanta las dos bases de datos, Keycloak (importa automáticamente el realm reto-realm), Inventory Service, Order Service y el API Gateway. La primera ejecución puede tardar unos minutos por la descarga de dependencias y la compilación.

Para detener y limpiar:

```bash
docker compose down          # detiene los contenedores
docker compose down -v       # además borra los datos de las bases de datos
```

> Si cambias las contraseñas de las bases de datos después del primer arranque, ejecuta `docker compose down -v` para que Postgres las recree.

## Autenticación (OAuth 2.0 / JWT con Keycloak)

Todas las rutas /api/** requieren un token Bearer. El token se obtiene directamente de Keycloak con el flujo Resource Owner Password:

curl -X POST http://localhost:8180/realms/reto-realm/protocol/openid-connect/token \
-d "grant_type=password" \
-d "client_id=reto-api" \
-d "client_secret=reto-api-dev-secret" \
-d "username=testuser" \
-d "password=test1234"

Usar el access_token de la respuesta en el header:

Authorization: Bearer <access_token>

Credenciales de desarrollo (definidas en keycloak/realm-export.json):

| Elemento            | Valor                  |
|---------------------|------------------------|
| Realm               | reto-realm             |
| Client ID           | reto-api               |
| Client secret       | reto-api-dev-secret    |
| Usuario de prueba   | testuser / test1234    |
| Admin de Keycloak   | Definido en `.env` (http://localhost:8180) |
| Vigencia del token  | 30 minutos             |

Nota: estas credenciales son solo para desarrollo/evaluación local.

## Endpoints

Todas las llamadas se hacen contra el gateway: http://localhost:8080

### Inventory (productos)

| Método | Ruta                     | Descripción                | Respuestas          |
|--------|--------------------------|----------------------------|---------------------|
| POST   | /api/v1/products         | Crear un producto          | 201, 400, 409 (SKU duplicado) |
| GET    | /api/v1/products         | Listar productos           | 200                 |
| GET    | /api/v1/products/{id}    | Obtener un producto        | 200, 404            |

Ejemplo de body para crear un producto:

{
    "sku": "SKU-001",
    "name": "Producto de prueba",
    "stockAvailable": 100,
    "price": 49.90
}

### Orders (pedidos)

| Método | Ruta                            | Descripción                                   | Respuestas               |
|--------|---------------------------------|-----------------------------------------------|--------------------------|
| POST   | /api/v1/orders                  | Registrar un pedido                           | 201, 400, 409, 503       |
| GET    | /api/v1/orders                  | Listar pedidos                                | 200                      |
| GET    | /api/v1/orders/{id}             | Consultar un pedido y su estado               | 200, 400, 404            |
| GET    | /api/v1/orders/{id}/history     | Consultar el historial de estados             | 200, 404                 |
| PATCH  | /api/v1/orders/{id}/cancel      | Cancelar un pedido                            | 200, 404, 409            |

Ejemplo de body para crear un pedido:

{
    "customerId": "c-001",
    "items": [
        { "productId": "<uuid-del-producto>", "quantity": 2 }
    ]
}

## Flujo y estados del pedido

1. Se recibe la solicitud de creación y se validan los datos.
2. Order Service consulta la disponibilidad en Inventory Service.
3. Si hay stock suficiente para todos los items, el pedido se guarda y queda en estado CONFIRMED.
4. Si no hay stock, se responde 409 con código STOCK_INSUFFICIENT. El pedido no se confirma: queda registrado como FAILED junto con su historial.
5. Cada cambio de estado se registra en el historial (estado anterior, estado nuevo, motivo, traceId, fecha y número de secuencia).

Máquina de estados:

PENDING   --> CONFIRMED | FAILED | CANCELLED
CONFIRMED --> CANCELLED
CANCELLED --> (estado final)
FAILED    --> (estado final)

- La cancelación solo se permite desde PENDING o CONFIRMED.
- Cancelar un pedido ya cancelado responde 409 ORDER_ALREADY_CANCELLED.
- Cancelar un pedido FAILED responde 409 INVALID_STATE_TRANSITION.
- Las transiciones se validan en OrderStateMachine, que es la única fuente de reglas de transición.

## Tracking con X-Trace-Id

- El gateway lee el header X-Trace-Id de la solicitud. Si no viene o su formato no es válido (solo letras, números, guion y guion bajo, hasta 64 caracteres), genera un UUID.
- El identificador se propaga a Order e Inventory (incluida la llamada interna Order -> Inventory).
- Se devuelve en el header de la respuesta y en el campo traceId de cualquier error.
- Cada microservicio lo coloca en el MDC y Logback lo imprime en todos los logs con el formato:

2026-01-01 10:00:00.000 INFO  [traceId=abc-123] com.reto.order... - mensaje

- Se persiste también en el historial de estados de cada pedido.

## Formato de errores

Todas las respuestas de error (gateway y microservicios) usan el mismo formato:

{
    "timestamp": "2026-01-01T10:00:00Z",
    "status": 409,
    "code": "STOCK_INSUFFICIENT",
    "message": "Stock insuficiente para uno o más productos del pedido.",
    "traceId": "abc-123"
}

| Código                    | HTTP | Cuándo ocurre                                          |
|---------------------------|------|--------------------------------------------------------|
| VALIDATION_ERROR          | 400  | Datos de entrada inválidos                             |
| INVALID_REQUEST           | 400  | Solicitud mal formada (por ejemplo, id que no es UUID) |
| TOKEN_INVALID_OR_EXPIRED  | 401  | Token ausente, inválido o expirado                     |
| ACCESS_DENIED             | 403  | Sin permisos                                           |
| ORDER_NOT_FOUND           | 404  | El pedido no existe                                    |
| PRODUCT_NOT_FOUND         | 404  | El producto no existe                                  |
| STOCK_INSUFFICIENT        | 409  | No hay stock suficiente                                |
| ORDER_ALREADY_CANCELLED   | 409  | El pedido ya estaba cancelado                          |
| INVALID_STATE_TRANSITION  | 409  | Transición de estado no permitida                      |
| DUPLICATE_SKU             | 409  | Ya existe un producto con ese SKU                      |
| INVENTORY_UNAVAILABLE     | 503  | Inventory no responde o falla                          |
| INTERNAL_ERROR            | 500  | Error interno                                          |

## Documentación de la API (OpenAPI / Swagger)

Swagger UI unificado, servido por el gateway (sin autenticación para consultar la documentación):

http://localhost:8080/swagger-ui.html

Permite alternar entre la documentación de Inventory Service y Order Service. Para probar endpoints desde Swagger UI, usar el botón de autorización con un token obtenido de Keycloak.

## Pruebas con Postman

1. Importar el archivo postman/reto-tecnico.postman_collection.json.
2. Ejecutar primero "0 - Autenticación > Obtener token". El token se guarda automáticamente en una variable de la colección.
3. Ejecutar el resto de carpetas en orden (Inventory, Orders, Seguridad). Los ids (productId, orderId, failedOrderId) se guardan solos entre requests.
4. Para ejecutar toda la colección, usar Collection Runner respetando el orden.

La colección incluye tests automáticos para el flujo feliz y para los errores: pedido inexistente, datos inválidos, stock insuficiente, cancelación duplicada, transición inválida, y token ausente o inválido. Cada request agrega automáticamente un X-Trace-Id.

## Pruebas unitarias

Los tests están escritos con JUnit 5. Para ejecutarlos por servicio:

cd order-service && ./mvnw test
cd inventory-service && ./mvnw test

Los tests unitarios cubren la máquina de estados de pedidos (transiciones permitidas, prohibidas y cancelaciones).

## Estructura del proyecto

.
├── api-gateway          # Spring Cloud Gateway: seguridad, rutas, X-Trace-Id
├── order-service        # Pedidos, estados e historial
├── inventory-service    # Productos, stock y disponibilidad
├── keycloak             # realm-export.json (realm, cliente y usuario de prueba)
├── postman              # Colección de Postman
├── .env.example         # Plantilla de variables de entorno (copiar a .env)
└── docker-compose.yml   # Orquestación de todo el sistema

Cada microservicio se organiza en capas: controller, service (interfaz + implementación), domain (modelo y repositorios), dto, mapper (MapStruct), exception, config y util.

## Patrones y principios aplicados

- State (máquina de estados): OrderStateMachine centraliza las transiciones válidas.
- Mapper (MapStruct): separación entre entidades y DTOs.
- Cliente/Adapter: InventoryClient encapsula la comunicación con Inventory.
- Interfaz + implementación en servicios (inversión de dependencias).
- Manejo centralizado de excepciones con @RestControllerAdvice y códigos de error tipados.
- AOP para trazabilidad transversal (TraceLoggingAspect).
- SOLID: responsabilidad única por clase, dependencias inyectadas por constructor y servicios dependientes de abstracciones.

## Decisiones y limitaciones conocidas

- FAILED: un pedido sin stock se persiste como FAILED (con su historial) y se responde 409 STOCK_INSUFFICIENT. Así queda trazabilidad del intento.
- Stock: la creación de un pedido consulta disponibilidad, pero no reserva ni descuenta stock. No se implementaron Saga, compensaciones ni reservas distribuidas, por no ser requeridos.
- Endpoint interno: /internal/v1/products/check-availability en Inventory no exige token, porque solo es alcanzable dentro de la red Docker y el gateway no lo enruta.
- Esquema de base de datos: se usa spring.jpa.hibernate.ddl-auto=update para simplificar el despliegue del reto. En un entorno productivo se usaría Flyway o Liquibase.
- Credenciales y secretos: las de las bases de datos y del admin de Keycloak se leen desde `.env` (no versionado; ver `.env.example`). Las del realm de Keycloak (client secret y usuario de prueba) son de desarrollo y están en `keycloak/realm-export.json`. En producción deberían gestionarse con un gestor de secretos.
- No hay datos precargados: los productos se crean desde la API (ver colección de Postman).

## Puertos y accesos rápidos

| Recurso                   | URL                                     |
|---------------------------|-----------------------------------------|
| API Gateway               | http://localhost:8080                   |
| Swagger UI                | http://localhost:8080/swagger-ui.html   |
| Keycloak (consola admin)  | http://localhost:8180                   |
| Token endpoint            | http://localhost:8180/realms/reto-realm/protocol/openid-connect/token |
| PostgreSQL Inventory      | localhost:5433 (usuario y contraseña definidos en `.env`) |
| PostgreSQL Order          | localhost:5434 (usuario y contraseña definidos en `.env`) |


## Seguridad y pipeline CI

[![CI](https://github.com/Moises1705/spring-microservices-gateway/actions/workflows/ci.yml/badge.svg)](https://github.com/Moises1705/spring-microservices-gateway/actions/workflows/ci.yml)

Workflow: `.github/workflows/ci.yml`. Corre en cada push a `main` y `cicd`, en pull requests y de forma manual.

| Job | Qué hace |
|-----|----------|
| Tests (x3) | `./mvnw test` por servicio. En order-service e inventory-service se excluyen los `*ApplicationTests`, porque levantan el contexto completo y necesitan PostgreSQL y Keycloak. |
| Build + Trivy (x3) | Construye la imagen Docker del servicio y la escanea con Trivy: reporte completo (informativo), SARIF y artefactos. Un último paso falla el job si hay CRITICAL o HIGH con corrección disponible (`--ignore-unfixed`). |
| Trivy fs | Escanea Dockerfiles (configuración) y secretos del repositorio. Excluye los `pom.xml`: Trivy consultaba Maven Central y recibía 429; las dependencias de las apps ya se analizan al escanear el JAR dentro de la imagen. |

Estado: el pipeline corre en verde (run #8, commit `c355891`, 2026-10-19). Escáner: Trivy 0.76.0 (imagen `aquasec/trivy@sha256:2416d1f74045b57274df1a340eee6b510cacd73c66a6ccb8da092b117eb7d222`, fijada por digest para que el escaneo sea reproducible). Los reportes completos de cada run están en la pestaña Summary y en los artefactos `trivy-*`.

### Hallazgos corregidos al integrar el escáner

| Hallazgo | Dónde | Severidad | Corrección |
|----------|-------|-----------|------------|
| 3 CVE en la stdlib de Go (CVE-2026-78667, CVE-2026-78669, CVE-2026-97031) | Binario `/usr/bin/pebble` de la imagen base `eclipse-temurin:21-jre` | HIGH | Se elimina el binario en los 3 Dockerfiles; la aplicación no lo usa. |
| Jackson 2.21.5 y 3.1.5 (CVE-2026-89407, 89425, 68497, 91776, 91777) | Los 3 servicios | HIGH | Se fuerzan `jackson-2-bom.version` = 2.21.7 y `jackson-bom.version` = 3.1.7 en los `pom.xml`. Quitar el override cuando Spring Boot incorpore estas versiones (issues #52012 y #52013, hito 4.1.2). |
| Bouncy Castle `bcprov-jdk18on` 1.81.1 (CVE-2026-8763, CVE-2026-13506) | api-gateway, vía `spring-cloud-starter` | CRITICAL y HIGH | Se fija la versión 1.85 en el `dependencyManagement`. |
| Tomcat embebido 11.0.24 (CVE-2026-65182, CVE-2026-65905, CVE-2026-68525) | Servicios con Spring MVC | CRITICAL | Se fuerza `tomcat.version` = 11.0.25 en los `pom.xml`. |

### Hallazgos abiertos (último run)

- **Aplicación (`app.jar`):** 0 hallazgos en los 3 servicios.
- **Imágenes:** 57 hallazgos en paquetes de Ubuntu 26.04 de la imagen base, idénticos en los 3 servicios: 53 MEDIUM, 4 LOW, 0 HIGH, 0 CRITICAL (unos 46 CVE distintos; algunos afectan a varios paquetes). Todos tienen estado `affected` y ninguno tiene versión corregida, así que no bloquean el pipeline y no se pueden resolver desde el proyecto hasta que Ubuntu o Temurin publiquen parches.
  - `rust-coreutils` (uutils): 20 CVE MEDIUM en utilidades de línea de comandos (cp, mv, dd, etc.).
  - `pcre2`: 8 CVE MEDIUM (lecturas y escrituras fuera de límites con expresiones regulares manipuladas).
  - `gnutls`: 5; `glibc`: 2 CVE repetidos en 4 paquetes; `tar`: 2; `dash`: 2; y 1 cada uno en `zlib1g`, `libexpat1`, `wget` (CVE-2021-31879, antiguo), `p11-kit` y `cyrus-sasl`. En LOW: `systemd/udev` y `shadow-utils`.
  - Valoración: la aplicación se ejecuta con `java -jar` y no invoca esas utilidades. glibc y zlib sí los carga la JVM; no se analizó si los defectos son alcanzables, por lo que se recomienda reconstruir la imagen periódicamente para recoger parches.
- **Dockerfiles:** DS-0026 (LOW) en los 3: falta la instrucción `HEALTHCHECK`. Queda como mejora pendiente.
- **Secretos:** el escáner no reportó ninguno.

Para actualizar Trivy, se cambia el digest en `TRIVY_IMAGE` del workflow y se revisa el resultado.