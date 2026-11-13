// Genera backend/app/metadata_config/credentials_supported/*.json a partir de los tipos de
// documento de la notaría de ejemplo (../verifiers/notaria/src/documentos.js), en el formato
// de eudi-srv-web-issuing-eudiw-py. Uso: node scripts/generar-metadata.mjs
import { mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { DOCUMENTOS } from './documentos.mjs'

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..')
const salida = join(raiz, 'backend/app/metadata_config/credentials_supported')
mkdirSync(salida, { recursive: true })
for (const f of readdirSync(salida)) rmSync(join(salida, f)) // los ejemplos de la UE no se emiten aquí

// Campos que la persona escribe y son fechas: el formulario usa el selector de fecha y el servidor las valida.
const FECHAS = new Set(['escritura_fecha', 'convivencia_desde', 'vigente_hasta', 'fecha_salida', 'fecha_regreso', 'fecha_nacimiento'])
const DE_LA_NOTARIA = new Set(['numero', 'fecha', 'notaria', 'notario'])
// numero/fecha/notaria/notario los pone el emisor al emitir (ver emulated_claims).
const COMUNES = { numero: '{consecutivo}', fecha: '{hoy}', notaria: 'Notaría Avance', notario: 'Andrea Restrepo Vélez', issuing_country: 'CO' }
const claveAtestada = {
  key_storage: ['iso_18045_high'],
  user_authentication: ['iso_18045_high'],
  preferred_key_storage_status_period: 2678400,
}
const prueba = { proof_signing_alg_values_supported: ['ES256'], key_attestations_required: claveAtestada }

for (const d of Object.values(DOCUMENTOS)) {
  const dias = d.validezDias ?? 3650
  const regla = (clave) => d.reglas[clave]
  const emulados = {}
  const claims = d.datos.map(([clave, nombre]) => {
    const r = regla(clave)
    const emulado = r && r.emulado !== null
    if (emulado) emulados[clave] = r.emulado
    if (clave in COMUNES) emulados[clave] = COMUNES[clave]
    return { clave, nombre, emulado, obligatorio: r ? r.obligatorio : true }
  }).map(({ clave, nombre, emulado, obligatorio }) => ({
    path: [clave],
    mandatory: obligatorio,
    value_type: FECHAS.has(clave) ? 'full-date' : 'string',
    source: DE_LA_NOTARIA.has(clave) || emulado ? 'issuer' : 'user',
    display: [{ name: nombre, locale: 'es' }],
  }))
  emulados.issuing_country = COMUNES.issuing_country // lo emulan el emisor, no el formulario
  for (const [clave, nombre] of [['issuing_authority', 'Autoridad emisora'], ['issuing_country', 'País emisor']]) {
    claims.push({ path: [clave], mandatory: true, source: 'issuer', display: [{ name: nombre, locale: 'es' }] })
  }
  const contenido = {
    [d.configuracion]: {
      format: 'dc+sd-jwt',
      scope: d.configuracion,
      cryptographic_binding_methods_supported: ['jwk'],
      credential_signing_alg_values_supported: ['ES256'],
      proof_types_supported: { jwt: prueba, attestation: prueba },
      vct: d.vct,
      credential_metadata: {
        display: [{
          name: d.nombre, locale: 'es', description: d.descripcion,
          background_color: d.color, text_color: '#FFFFFF',
          logo: { uri: 'https://civica-desarrollo.avance.org.co/notaria-issuer/static/notaria/logo.png', alt_text: 'Notaría Avance' },
          ...(d.fondo ? { background_image: { uri: `https://civica-desarrollo.avance.org.co/notaria-issuer/static/notaria/fondos/${d.fondo}` } } : {}),
        }],
        credential_reuse_policy: { id: 'arf_annex_ii', options: [{ details: ['limited_time'], reissue_trigger_lifetime_left: 7776000 }] },
        claims,
      },
      issuer_config: {
        issuing_authority: 'Notaría Avance',
        organization_id: 'notaria-avance',
        validity: dias,
        organization_name: 'Notaría Avance',
        doctype: d.vct,
        emulated_claims: emulados,
        persona_claims: d.persona,
      },
    },
  }
  writeFileSync(join(salida, `${d.configuracion.replace('co.notaria.', '')}.json`), JSON.stringify(contenido, null, 2) + '\n')
}
console.log(`${Object.keys(DOCUMENTOS).length} documentos en ${salida}`)
