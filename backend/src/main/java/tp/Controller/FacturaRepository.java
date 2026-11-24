package tp.Controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import tp.DTO.FacturaReporteDTO;
import tp.DTO.PaginaDTO;
import tp.Entidad.CondicionIva;
import tp.Entidad.FacturaVenta;
import tp.Entidad.ListaPrecioArticulo;

import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class FacturaRepository {

    @PersistenceContext
    private EntityManager em;

    public PaginaDTO<FacturaReporteDTO> buscarFacturasFiltradas(Date fechaDesde, Date fechaHasta, String estado,
            Double montoMinimo, Long clienteId, Long articuloId, Long numero, Boolean informada,
            int pagina, int tamanio) {
        // Las eliminadas (borrado logico) no se listan
        StringBuilder where = new StringBuilder("WHERE f.estado <> 'ELIMINADA' ");
        Map<String, Object> params = new HashMap<>();

        if (fechaDesde != null) {
            where.append("AND f.fechaEmision >= :fechaDesde ");
            params.put("fechaDesde", fechaDesde);
        }
        if (fechaHasta != null) {
            where.append("AND f.fechaEmision <= :fechaHasta ");
            params.put("fechaHasta", fechaHasta);
        }
        if (estado != null && !estado.trim().isEmpty()) {
            where.append("AND f.estado = :estado ");
            params.put("estado", estado);
        }
        if (montoMinimo != null) {
            where.append("AND f.importeTotal >= :montoMinimo ");
            params.put("montoMinimo", montoMinimo);
        }
        if (numero != null) {
            where.append("AND f.numero = :numero ");
            params.put("numero", numero);
        }
        if (informada != null) {
            // Informada a ARCA = tiene CAE
            where.append(informada ? "AND f.cae IS NOT NULL " : "AND f.cae IS NULL ");
        }
        if (clienteId != null) {
            where.append("AND f.cliente.id = :clienteId ");
            params.put("clienteId", clienteId);
        }
        if (articuloId != null) {
            where.append("AND EXISTS (SELECT 1 FROM FacturaVentaDetalle d2 WHERE d2.factura = f "
                    + "AND d2.listaPrecioArticulo.articulo.id = :articuloId) ");
            params.put("articuloId", articuloId);
        }

        TypedQuery<Long> count = em.createQuery("SELECT COUNT(f) FROM FacturaVenta f " + where, Long.class);
        params.forEach(count::setParameter);
        long total = count.getSingleResult();

        // Ultima cargada arriba: fecha de alta (creacion) e id descendentes
        String jpql = "SELECT new tp.DTO.FacturaReporteDTO("
                + "f.id, f.numero, f.fechaEmision, COALESCE(c.denominacion, 'Consumidor Final'), "
                + "ci.denominacion, pv.descripcion, f.importeTotal, COUNT(d), f.estado, f.tipoComprobante, f.motivoRechazo) "
                + "FROM FacturaVenta f LEFT JOIN f.cliente c JOIN f.condicionIva ci JOIN f.puntoVenta pv "
                + "LEFT JOIN f.detalles d " + where
                + "GROUP BY f.id, f.numero, f.fechaEmision, f.fechaAlta, c.denominacion, ci.denominacion, "
                + "pv.descripcion, f.importeTotal, f.estado, f.tipoComprobante, f.motivoRechazo "
                + "ORDER BY f.fechaAlta DESC, f.id DESC";
        TypedQuery<FacturaReporteDTO> query = em.createQuery(jpql, FacturaReporteDTO.class);
        params.forEach(query::setParameter);
        query.setFirstResult(pagina * tamanio);
        query.setMaxResults(tamanio);

        List<FacturaReporteDTO> filas = query.getResultList();
        completarArticulos(filas);
        return PaginaDTO.de(filas, pagina, tamanio, total);
    }

    /** Carga primer articulo y cantidad total de cada factura de la pagina (1 sola consulta). */
    private void completarArticulos(List<FacturaReporteDTO> filas) {
        if (filas.isEmpty()) {
            return;
        }
        List<Long> ids = filas.stream().map(FacturaReporteDTO::getId).toList();
        List<Object[]> renglones = em.createQuery(
                "SELECT d.factura.id, d.listaPrecioArticulo.articulo.denominacion, d.cantidad "
                        + "FROM FacturaVentaDetalle d WHERE d.factura.id IN :ids ORDER BY d.id",
                Object[].class).setParameter("ids", ids).getResultList();
        Map<Long, FacturaReporteDTO> porId = new HashMap<>();
        filas.forEach(f -> porId.put(f.getId(), f));
        for (Object[] r : renglones) {
            FacturaReporteDTO f = porId.get((Long) r[0]);
            if (f.getArticulo() == null) {
                f.setArticulo((String) r[1]);
            } else {
                f.setItemsAdicionales(f.getItemsAdicionales() + 1);
            }
            f.setCantidadTotal(f.getCantidadTotal() + ((Number) r[2]).doubleValue());
        }
    }

    public Optional<CondicionIva> buscarCondicionConsumidorFinal() {
        List<CondicionIva> r = em.createQuery(
                "SELECT c FROM CondicionIva c WHERE LOWER(c.denominacion) = 'consumidor final' AND c.fechaBaja IS NULL ORDER BY c.id",
                CondicionIva.class).setMaxResults(1).getResultList();
        return r.stream().findFirst();
    }

    public Optional<FacturaVenta> buscarPorId(Long id) {
        return Optional.ofNullable(em.find(FacturaVenta.class, id));
    }

    public FacturaVenta guardar(FacturaVenta factura) {
        if (factura.getId() == null) {
            em.persist(factura);
            return factura;
        }
        return em.merge(factura);
    }

    public void eliminar(FacturaVenta factura) {
        em.remove(factura);
    }
}
