-- Tablas propias de la ADMINISTRACION del publicador de listas.
--
-- POR QUÉ EXISTEN, SI EL ESQUEMA DE TS 119 612 YA LO CUENTA TODO
--
-- `trust_service_providers` y `trust_services` son la FORMA PUBLICADA: lo que
-- acaba en los XML y los JWS firmados. El back-office necesita mas cosas que
-- el esquema publicado no guarda: quien creo el actor, su correo, su pais,
-- cuando se emitio cada certificado, cuando se retiro, y si el actor lo pone el
-- propio servicio (la CA de acceso de la PKI) o una persona (y entonces se
-- puede editar y retirar).
--
-- Aqui se guarda ESO. Al publicar, `admin.sincronizar()` proyecta estas tablas
-- sobre las publicadas, de modo que el modelo publicado y el de administracion
-- nunca divergen.
--
-- UN ACTOR ES UN SERVICIO, NO UN PROVEEDOR
--
-- En el esquema publicado, un proveedor (`trust_service_providers`) puede
-- prestar servicios de varios roles: "Civica EUDI" aparece a la vez en la
-- lista de wallet providers, en la de PID y en la de QEAA. En la interfaz, cada
-- uno de esos es un actor distinto, con su nombre, sus certificados y su
-- estado. Por eso `admin_actores` cuelga de `service_id` y no de `tsp_id`: el
-- actor es el servicio.
--
-- Idempotente: se ejecuta en cada arranque.

CREATE TABLE IF NOT EXISTS `admin_actores` (
  `actor_id` int(11) NOT NULL,
  `service_id` int(11) NOT NULL,
  `rol` varchar(32) NOT NULL,
  `nombre` text DEFAULT NULL,
  `pais` varchar(2) DEFAULT NULL,
  `correo` varchar(250) DEFAULT NULL,
  `url_info` text DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `sistema` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`actor_id`),
  UNIQUE KEY `uq_admin_actores_servicio` (`service_id`),
  KEY `ix_admin_actores_rol` (`rol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Los certificados que un actor presenta. `id` es el que ve la interfaz.
CREATE TABLE IF NOT EXISTS `admin_certificados` (
  `cert_id` int(11) NOT NULL AUTO_INCREMENT,
  `actor_id` int(11) NOT NULL,
  `pem` longtext NOT NULL,
  `sha256` char(64) NOT NULL,
  `sujeto` text DEFAULT NULL,
  `emisor` text DEFAULT NULL,
  `no_antes` datetime DEFAULT NULL,
  `no_despues` datetime DEFAULT NULL,
  `es_ca` tinyint(1) NOT NULL DEFAULT 0,
  `autofirmado` tinyint(1) NOT NULL DEFAULT 0,
  `origen` varchar(16) NOT NULL DEFAULT 'actor',
  `serie_pki` varchar(64) DEFAULT NULL,
  `estado` varchar(16) NOT NULL DEFAULT 'otorgado',
  `desde` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `retirado_en` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`cert_id`),
  KEY `ix_admin_certificados_actor` (`actor_id`),
  UNIQUE KEY `uq_admin_certificados_sha` (`actor_id`,`sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Los supply points (URLs) que el actor declara.
CREATE TABLE IF NOT EXISTS `admin_puntos` (
  `punto_id` int(11) NOT NULL AUTO_INCREMENT,
  `actor_id` int(11) NOT NULL,
  `uri` varchar(500) NOT NULL,
  `orden` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`punto_id`),
  KEY `ix_admin_puntos_actor` (`actor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
