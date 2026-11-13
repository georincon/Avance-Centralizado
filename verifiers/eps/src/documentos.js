// Los dos documentos que emite la EPS a la wallet.
//
//   Carné de afiliado            quién es el afiliado, régimen, plan, IPS asignada y
//                                si la afiliación está activa. Lo pide una farmacia o
//                                una IPS antes de atender.
//   Resumen de historia clínica  lo esencial para que otro médico atienda bien:
//                                grupo sanguíneo, alergias, enfermedades crónicas,
//                                medicamentos, vacunas y la última consulta.
//
// Son SD-JWT VC: cada dato se divulga por separado, así que el paciente puede
// enseñar solo sus alergias a un médico de urgencias sin revelar el resto. Las
// listas (alergias, medicamentos…) van como texto separado por «; » para que
// cualquier wallet las muestre bien.

export const DOCUMENTOS = {
  carne: {
    // Fondo de la tarjeta en la wallet (scripts/generar-fondos.py).
    fondo: 'eps-carne.jpg',
    configuracion: 'co.eps.carne_afiliado',
    vct: 'urn:co:eps:carne-afiliado:1',
    nombre: 'Carnet IPS Avance',
    descripcion: 'Afiliación al sistema de salud',
    color: '#0B6E4F',
    datos: [
      ['eps', 'EPS'],
      ['afiliado_nombre', 'Afiliado'],
      ['afiliado_documento', 'Documento'],
      ['numero_afiliacion', 'Número de afiliación'],
      ['regimen', 'Régimen'],
      ['tipo_afiliado', 'Tipo de afiliado'],
      ['plan', 'Plan'],
      ['ips_primaria', 'IPS asignada'],
      ['estado', 'Estado de la afiliación'],
      ['afiliado_desde', 'Afiliado desde'],
    ],
  },
  historia: {
    // Fondo de la tarjeta en la wallet (scripts/generar-fondos.py).
    fondo: 'eps-historia.jpg',
    configuracion: 'co.eps.historia_clinica',
    vct: 'urn:co:eps:resumen-historia-clinica:1',
    nombre: 'Historia Clínica',
    descripcion: 'Lo esencial de tu historia clínica para quien te atienda',
    color: '#7A1F3D',
    datos: [
      ['eps', 'EPS'],
      ['paciente_nombre', 'Paciente'],
      ['paciente_documento', 'Documento'],
      ['fecha_nacimiento', 'Fecha de nacimiento'],
      ['grupo_sanguineo', 'Grupo sanguíneo y RH'],
      ['alergias', 'Alergias'],
      ['condiciones_cronicas', 'Enfermedades crónicas'],
      ['medicamentos', 'Medicamentos actuales'],
      ['vacunas', 'Vacunas'],
      ['ultima_consulta', 'Última consulta'],
      ['observaciones', 'Observaciones'],
      ['actualizada', 'Historia actualizada el'],
    ],
  },
}

export const porConfiguracion = (id) => Object.entries(DOCUMENTOS).find(([, d]) => d.configuracion === id)?.[0]
export const porVct = (vct) => Object.entries(DOCUMENTOS).find(([, d]) => d.vct === vct)?.[0]

const lista = (xs) => (xs?.length ? xs.join('; ') : 'Ninguna registrada')

/** Los datos del documento `tipo` para un afiliado, en el orden de la definición. */
export function datosDelDocumento(tipo, a, eps) {
  const nombre = `${a.nombres} ${a.apellidos}`
  if (tipo === 'carne') {
    const f = a.afiliacion
    return {
      eps, afiliado_nombre: nombre, afiliado_documento: a.documento, numero_afiliacion: f.numero,
      regimen: f.regimen, tipo_afiliado: f.tipo, plan: f.plan, ips_primaria: f.ips, estado: f.estado, afiliado_desde: f.desde,
    }
  }
  const h = a.historia
  const c = h.ultima_consulta
  return {
    eps, paciente_nombre: nombre, paciente_documento: a.documento, fecha_nacimiento: a.fecha_nacimiento,
    grupo_sanguineo: h.grupo_sanguineo || 'Sin registrar',
    alergias: lista(h.alergias),
    condiciones_cronicas: lista(h.condiciones),
    medicamentos: lista(h.medicamentos),
    vacunas: lista(h.vacunas),
    ultima_consulta: c?.fecha ? `${c.fecha} · ${c.servicio} · ${c.motivo}${c.diagnostico ? ` · Dx: ${c.diagnostico}` : ''}` : 'Sin consultas',
    observaciones: h.observaciones || 'Ninguna',
    actualizada: (h.actualizada ?? '').slice(0, 10),
  }
}
