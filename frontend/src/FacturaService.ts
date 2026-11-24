import {
    FacturaDetalleResponseDTO,
    FacturaFiltrosDTO,
    FacturaReporteDTO,
    FacturaRequestDTO,
    PaginaDTO
} from './models/factura.dto.js';

const API_BASE_URL = 'http://localhost:8080/api/facturas';

export class FacturaService {

    private async manejarRespuesta<T>(respuesta: Response): Promise<T> {
        if (!respuesta.ok) {
            let mensaje = await respuesta.text();
            try {
                const json = JSON.parse(mensaje);
                mensaje = json.mensaje ?? json.message ?? mensaje;
            } catch { /* texto plano */ }
            throw new Error(mensaje || respuesta.statusText);
        }
        if (respuesta.status === 204) {
            return undefined as T;
        }
        return respuesta.json() as Promise<T>;
    }

    private construirQueryString(filtros: FacturaFiltrosDTO): string {
        const params = new URLSearchParams();
        Object.entries(filtros).forEach(([clave, valor]) => {
            if (valor !== undefined && valor !== null && String(valor).trim() !== '') {
                params.append(clave, String(valor));
            }
        });
        const query = params.toString();
        return query ? `?${query}` : '';
    }

    async obtenerFacturas(filtros: FacturaFiltrosDTO = {}): Promise<PaginaDTO<FacturaReporteDTO>> {
        const respuesta = await fetch(`${API_BASE_URL}${this.construirQueryString(filtros)}`);
        return this.manejarRespuesta(respuesta);
    }

    /** Listado completo con los filtros aplicados, en PDF o Excel (CSV). */
    urlExportar(formato: 'pdf' | 'excel', filtros: FacturaFiltrosDTO): string {
        const q = this.construirQueryString({ ...filtros, pagina: undefined, tamanio: undefined });
        return `${API_BASE_URL}/exportar${q ? q + '&' : '?'}formato=${formato}`;
    }

    urlPdf(id: number): string {
        return `${API_BASE_URL}/${id}/pdf`;
    }

    async obtenerFactura(id: number): Promise<FacturaDetalleResponseDTO> {
        return this.manejarRespuesta(await fetch(`${API_BASE_URL}/${id}`));
    }

    private enviarJson<T>(url: string, metodo: string, cuerpo?: unknown): Promise<T> {
        return fetch(url, {
            method: metodo,
            headers: { 'Content-Type': 'application/json' },
            body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo)
        }).then(r => this.manejarRespuesta<T>(r));
    }

    crearFactura(factura: FacturaRequestDTO): Promise<FacturaReporteDTO> {
        return this.enviarJson(API_BASE_URL, 'POST', factura);
    }

    editarFactura(id: number, factura: FacturaRequestDTO): Promise<FacturaReporteDTO> {
        return this.enviarJson(`${API_BASE_URL}/${id}`, 'PUT', factura);
    }

    informarFactura(id: number): Promise<FacturaReporteDTO> {
        return this.enviarJson(`${API_BASE_URL}/${id}/informar`, 'POST');
    }

    anularFactura(id: number): Promise<FacturaReporteDTO> {
        return this.enviarJson(`${API_BASE_URL}/${id}/anular`, 'POST');
    }

    eliminarFactura(id: number): Promise<void> {
        return this.enviarJson(`${API_BASE_URL}/${id}`, 'DELETE');
    }
}
