package tp.Controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import tp.DTO.PaginaDTO;
import tp.Negocio.FormatoCuit;
import tp.Negocio.TipoComprobanteResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Busquedas con filtros y paginacion para los exploradores (pop-up) del frontend. */
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api")
public class ExploradorRestController {

    public record ClienteExplorador(Long id, String denominacion, String cuitCuil, String condicionIva) {
    }

    public record ArticuloExplorador(Long listaPrecioArticuloId, Long articuloId, String codigo,
            String denominacion, String marca, String rubro, String listaPrecio, double precioVenta) {
    }

    public record ComprobanteTipo(int tipo, String letra, boolean discriminaIva,
            boolean emisorResponsableInscripto, String condicionIva) {
    }

    public record OpcionesArticulo(List<String> codigos, List<String> denominaciones, List<String> marcas,
            List<String> rubros) {
    }

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private ConfiguracionService configuracion;

    @GetMapping("/clientes/buscar")
    public PaginaDTO<ClienteExplorador> buscarClientes(
            @RequestParam(required = false) Long numero,
            @RequestParam(required = false) String denominacion,
            @RequestParam(required = false) String cuit,
            @RequestParam(required = false) String condicionIva,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanio) {

        StringBuilder where = new StringBuilder("WHERE c.fechaBaja IS NULL ");
        Map<String, Object> p = new HashMap<>();
        if (numero != null) {
            where.append("AND c.id = :numero ");
            p.put("numero", numero);
        }
        if (hay(denominacion)) {
            where.append("AND LOWER(c.denominacion) LIKE :denominacion ");
            p.put("denominacion", like(denominacion));
        }
        String cuitDigitos = FormatoCuit.soloDigitos(cuit);
        if (!cuitDigitos.isEmpty()) {
            // Se compara sin guiones: los guiones son solo visuales
            where.append("AND REPLACE(c.cuitCuil, '-', '') LIKE :cuit ");
            p.put("cuit", "%" + cuitDigitos + "%");
        }
        if (hay(condicionIva)) {
            where.append("AND LOWER(ci.denominacion) LIKE :condicionIva ");
            p.put("condicionIva", like(condicionIva));
        }
        int t = tam(tamanio), pg = Math.max(pagina, 0);
        String from = "FROM Cliente c LEFT JOIN c.condicionIva ci ";
        long total = contar("SELECT COUNT(c) " + from + where, p);
        // Siempre por numero de cliente ascendente
        TypedQuery<Object[]> q = em.createQuery(
                "SELECT c.id, c.denominacion, c.cuitCuil, ci.denominacion " + from + where + "ORDER BY c.id ASC",
                Object[].class);
        p.forEach(q::setParameter);
        q.setFirstResult(pg * t).setMaxResults(t);
        List<ClienteExplorador> filas = q.getResultList().stream()
                .map(o -> new ClienteExplorador((Long) o[0], (String) o[1], (String) o[2], (String) o[3])).toList();
        return PaginaDTO.de(filas, pg, t, total);
    }

    @GetMapping("/lista-precio-articulos/buscar")
    public PaginaDTO<ArticuloExplorador> buscarArticulos(
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) String denominacion,
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) String rubro,
            @RequestParam(required = false) Long listaPrecioId,
            @RequestParam(defaultValue = "asc") String orden,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanio) {

        String from = "FROM ListaPrecioArticulo lpa JOIN lpa.articulo a JOIN lpa.listaPrecio lp "
                + "LEFT JOIN a.marca m LEFT JOIN a.rubro r ";
        StringBuilder where = new StringBuilder("WHERE a.fechaBaja IS NULL AND lpa.fechaBaja IS NULL ");
        Map<String, Object> p = new HashMap<>();
        if (listaPrecioId != null) {
            where.append("AND lp.id = :listaPrecioId ");
            p.put("listaPrecioId", listaPrecioId);
        }
        if (hay(codigo)) {
            where.append("AND LOWER(a.codigo) LIKE :codigo ");
            p.put("codigo", like(codigo));
        }
        if (hay(denominacion)) {
            where.append("AND LOWER(a.denominacion) LIKE :denominacion ");
            p.put("denominacion", like(denominacion));
        }
        if (hay(marca)) {
            where.append("AND LOWER(m.denominacion) LIKE :marca ");
            p.put("marca", like(marca));
        }
        if (hay(rubro)) {
            where.append("AND LOWER(r.denominacion) LIKE :rubro ");
            p.put("rubro", like(rubro));
        }
        int t = tam(tamanio), pg = Math.max(pagina, 0);
        long total = contar("SELECT COUNT(lpa) " + from + where, p);
        TypedQuery<Object[]> q = em.createQuery(
                "SELECT lpa.id, a.id, a.codigo, a.denominacion, m.denominacion, r.denominacion, "
                        + "lp.denominacion, lpa.precioVenta " + from + where
                        + "ORDER BY lpa.precioVenta " + ("desc".equalsIgnoreCase(orden) ? "DESC" : "ASC")
                        + ", a.denominacion, lpa.id",
                Object[].class);
        p.forEach(q::setParameter);
        q.setFirstResult(pg * t).setMaxResults(t);
        List<ArticuloExplorador> filas = q.getResultList().stream()
                .map(o -> new ArticuloExplorador((Long) o[0], (Long) o[1], (String) o[2], (String) o[3],
                        (String) o[4], (String) o[5], (String) o[6], ((Number) o[7]).doubleValue()))
                .toList();
        return PaginaDTO.de(filas, pg, t, total);
    }

    /** Opciones para los combos con sugerencias del explorador de articulos. */
    @GetMapping("/lista-precio-articulos/opciones")
    public OpcionesArticulo opcionesArticulos() {
        String base = "FROM ListaPrecioArticulo lpa JOIN lpa.articulo a LEFT JOIN a.marca m LEFT JOIN a.rubro r "
                + "WHERE a.fechaBaja IS NULL AND lpa.fechaBaja IS NULL ";
        return new OpcionesArticulo(
                distintos("SELECT DISTINCT a.codigo " + base + "ORDER BY a.codigo"),
                distintos("SELECT DISTINCT a.denominacion " + base + "ORDER BY a.denominacion"),
                distintos("SELECT DISTINCT m.denominacion " + base + "AND m.denominacion IS NOT NULL ORDER BY m.denominacion"),
                distintos("SELECT DISTINCT r.denominacion " + base + "AND r.denominacion IS NOT NULL ORDER BY r.denominacion"));
    }

    /** Tipo de comprobante que se emitira segun el cliente (sin cliente = Consumidor Final). */
    @GetMapping("/comprobante-tipo")
    public ComprobanteTipo comprobanteTipo(@RequestParam(required = false) Long clienteId) {
        tp.Entidad.CondicionIva ci = null;
        if (clienteId != null) {
            tp.Entidad.Cliente c = em.find(tp.Entidad.Cliente.class, clienteId);
            if (c == null) {
                throw new tp.Exceptions.RecursoNoEncontradoException("No existe el cliente " + clienteId);
            }
            ci = c.getCondicionIva();
        }
        if (ci == null) {
            ci = em.createQuery("SELECT c FROM CondicionIva c WHERE LOWER(c.denominacion) = 'consumidor final' AND c.fechaBaja IS NULL ORDER BY c.id",
                    tp.Entidad.CondicionIva.class).setMaxResults(1).getResultList().stream().findFirst()
                    .orElseThrow(() -> new tp.Exceptions.RecursoNoEncontradoException("Falta la condicion Consumidor Final"));
        }
        boolean ri = configuracion.isEmisorResponsableInscripto();
        int tipo = TipoComprobanteResolver.resolver(ri, ci.getCodigoAfip());
        return new ComprobanteTipo(tipo, TipoComprobanteResolver.letra(tipo),
                TipoComprobanteResolver.discriminaIva(tipo), ri, ci.getDenominacion());
    }

    public record EmisorDTO(boolean responsableInscripto) {
    }

    @GetMapping("/configuracion/emisor")
    public EmisorDTO obtenerEmisor() {
        return new EmisorDTO(configuracion.isEmisorResponsableInscripto());
    }

    @PutMapping("/configuracion/emisor")
    public EmisorDTO cambiarEmisor(@RequestBody EmisorDTO dto) {
        configuracion.setEmisorResponsableInscripto(dto.responsableInscripto());
        return dto;
    }

    private List<String> distintos(String jpql) {
        return em.createQuery(jpql, String.class).getResultList();
    }

    private long contar(String jpql, Map<String, Object> p) {
        TypedQuery<Long> q = em.createQuery(jpql, Long.class);
        p.forEach(q::setParameter);
        return q.getSingleResult();
    }

    private static boolean hay(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String like(String s) {
        return "%" + s.trim().toLowerCase() + "%";
    }

    private static int tam(int t) {
        return Math.min(Math.max(t, 1), 100);
    }
}
