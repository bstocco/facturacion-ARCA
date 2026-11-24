# Trabajo Práctico: CRUD Factura de Venta con conexión a ARCA (AFIP)

Aplicación integral (Backend Spring Boot + Frontend TypeScript/HTML5 con Bootstrap) para la gestión de Facturas de Venta, con persistencia en PostgreSQL (Supabase), API REST y emisión de comprobantes electrónicos (CAE) contra ARCA en **entorno de homologación**.

## Tecnologías
- **Backend:** Java 21, Spring Boot 3.2.4, Spring Data JPA / Hibernate, PostgreSQL, Gradle, Lombok, iText (PDF), BouncyCastle (firma CMS del WSAA).
- **Frontend:** TypeScript 5, Node.js / Express (servidor estático), Bootstrap 5.3 (CDN).

## Arquitectura
```
backend/   Spring Boot (puerto 8080)
  tp/Controller  REST (facturas, catálogos, exploradores), servicios y repositorio JPQL
  tp/ClasesArca  WsaaClient (token y sign) y WsfeClient (último autorizado y CAE)
  tp/Negocio     reglas de factura, cálculo de importes, tipo de comprobante, PDF y exportación
  tp/Entidad     entidades JPA          tp/DTO  objetos de entrada y salida
  arca/          certificado, clave privada y ticket de acceso (homologación)
frontend/  TypeScript (puerto 3000): app.ts (pantalla), explorador.ts (ventana de búsqueda), servicios HTTP
```

## Puesta en marcha
1. Backend: `cd backend` y `.\gradlew.bat bootRun` (Windows) o `./gradlew bootRun`. → http://localhost:8080
2. Frontend: `cd frontend`, `npm install`, `npm run build`, `npm start`. → http://localhost:3000/index.html
3. ARCA se configura en `backend/src/main/resources/application.properties` (`arca.habilitado`, `arca.cuit`, rutas de clave/certificado, URLs de homologación). Para operar, el punto de venta de la base debe estar habilitado en ARCA para Web Services.

## Ciclo de vida de una factura
| Estado | Significado | Se puede |
|---|---|---|
| BORRADOR | Guardada, **sin número** ni CAE | Editar, Enviar a ARCA, Eliminar |
| RECHAZADA | ARCA la rechazó (sin número; el motivo se ve al pasar el mouse) | Editar, volver a enviar, Eliminar |
| INFORMADA | Aprobada por ARCA: tiene **número y CAE** | Solo Anular y ver/imprimir (edición bloqueada) |
| ANULADA | Anulada luego de informada | Solo ver/imprimir |
| ELIMINADA | Borrado lógico de un borrador (no se lista) | — |

Regla central: **tener número = ya informada a ARCA**; desde ese momento no se puede editar ni eliminar.

## Pantalla
1. **Filtros**: Nº de factura, Fecha desde/hasta, Estado, **Informada a ARCA (Sí/No, según exista CAE)**, Monto mínimo, **Cliente** y **Artículo** (ventanas de búsqueda).
2. **Alta / edición**: fecha, punto de venta, cliente (ventana de búsqueda o switch Consumidor Final), moneda, **lista de precios** (mayorista, minorista, etc.), observaciones e ítems. Los artículos se eligen con el explorador y solo se ofrecen los de la lista elegida; el **precio unitario ya incluye IVA** y sale siempre de la lista. El tipo de comprobante (A/B/C) se deduce del emisor (switch Monotributista / Responsable Inscripto) y de la condición IVA del cliente.
3. **Comprobantes registrados**: listado **paginado en el backend** (la última cargada arriba) con acciones Editar, **Enviar a ARCA**, Eliminar, Anular, ver detalle y PDF; exportación del listado filtrado a PDF y Excel (CSV).

### Exploradores (ventana emergente)
Cliente: Nº, nombre/razón social, CUIT y condición IVA. Artículo (ListaPrecioArticulo): código, denominación, marca, rubro y orden por precio. Ambos paginados en el backend.

### Validaciones al enviar a ARCA
- La fecha de emisión debe estar entre 5 días antes y 5 después de hoy (regla de ARCA); si no, el sistema avisa y no envía.
- Solo facturas en pesos.
- Si ARCA no responde, la factura queda como estaba (BORRADOR) y se muestra el error.

## Integración con ARCA (según la guía)
WSAA: se firma el TRA (CMS/SHA256withRSA, BouncyCastle) con la clave y el certificado de homologación y se guarda el ticket (`arca/ta-wsfe.xml`) hasta que vence. WSFEv1: `FECompUltimoAutorizado` para el siguiente número y `FECAESolicitar` para el CAE (incluye `CondicionIVAReceptorId`; A/B discriminan IVA 21 %, C no).

## API REST
Base: `http://localhost:8080/api`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/facturas?numero&informada&fechaDesde&fechaHasta&estado&montoMinimo&clienteId&articuloId&pagina&tamanio` | Listado paginado (`contenido`, `pagina`, `totalElementos`, `totalPaginas`) |
| GET | `/facturas/exportar?formato=pdf\|excel` + mismos filtros | Exporta el listado filtrado |
| GET | `/facturas/{id}` · `/facturas/{id}/pdf` | Detalle · PDF del comprobante |
| POST | `/facturas` | Crea un borrador |
| PUT | `/facturas/{id}` | Edita (409 si ya tiene número) |
| POST | `/facturas/{id}/informar` | Envía a ARCA y asigna número y CAE |
| POST | `/facturas/{id}/anular` | Anula una factura informada |
| DELETE | `/facturas/{id}` | Elimina un borrador (409 si tiene número) |
| GET | `/clientes/buscar`, `/lista-precio-articulos/buscar` | Exploradores con filtros y paginación |
| GET | `/listas-precio`, `/puntos-venta`, `/tipos-moneda`, `/comprobante-tipo` | Catálogos |

Errores: 404 recurso inexistente, 409 regla de negocio, 502 falla de ARCA, con cuerpo `{status, mensaje, fecha}`.

## Pruebas
`cd backend` y `.\gradlew.bat test` (reglas de factura, importes, tipos de comprobante, servicio, PDF y exportación).
