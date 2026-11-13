-- --------------------------------------------------------
-- Anfitrião:                    127.0.0.1
-- Versão do servidor:           11.4.2-MariaDB - mariadb.org binary distribution
-- SO do servidor:               Win64
-- HeidiSQL Versão:              12.6.0.6765
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


-- A despejar estrutura da base de dados para trusted_lists
CREATE DATABASE IF NOT EXISTS `trusted_lists` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */;
USE `trusted_lists`;

-- A despejar estrutura para tabela trusted_lists.countries
CREATE TABLE IF NOT EXISTS `countries` (
  `country_id` int(11) NOT NULL AUTO_INCREMENT,
  `country_code` varchar(2) DEFAULT NULL,
  `country_name` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`country_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Datos semilla de `countries`.
--
-- POR QUÉ: el volcado original excluyó los datos ("Exportação de dados não
-- seleccionada"), así que la tabla se creaba vacía. `check_country()` busca aquí
-- el `issuing_country` del PID del operador y, al no encontrarlo, devuelve None:
-- el login terminaba en "país invalido" y NINGÚN operador podía entrar al TLP.
-- Sin operador no se puede crear ni firmar una Trusted List, así que esto
-- bloqueaba toda la capa de confianza del ecosistema.
--
-- IDEMPOTENCIA: este script se ejecuta en CADA arranque (app/__init__.py), así
-- que las inserciones no pueden duplicar filas. Se usa `WHERE NOT EXISTS` en
-- vez de una clave única con `IF NOT EXISTS`, porque esa forma es de MariaDB y
-- MySQL 8 la rechaza con un error de sintaxis.

INSERT INTO `countries` (`country_code`, `country_name`)
SELECT 'CO', 'Colombia' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `countries` WHERE `country_code` = 'CO');

INSERT INTO `countries` (`country_code`, `country_name`)
SELECT 'EU', 'European Union' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `countries` WHERE `country_code` = 'EU');

INSERT INTO `countries` (`country_code`, `country_name`)
SELECT 'UT', 'Utopia' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `countries` WHERE `country_code` = 'UT');

-- Exportação de dados não seleccionada.

-- A despejar estrutura para tabela trusted_lists.lotl_old_certificates
CREATE TABLE IF NOT EXISTS `lotl_old_certificates` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `cert` text DEFAULT NULL,
  `tsl_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `tsl` (`tsl_id`),
  CONSTRAINT `tsl` FOREIGN KEY (`tsl_id`) REFERENCES `trusted_lists` (`tsl_id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Exportação de dados não seleccionada.

-- A despejar estrutura para tabela trusted_lists.scheme_operators
CREATE TABLE IF NOT EXISTS `scheme_operators` (
  `operator_id` int(11) NOT NULL AUTO_INCREMENT,
  `country_id` int(11) DEFAULT NULL,
  `pid_hash` varchar(255) DEFAULT NULL,
  `operator_role` text DEFAULT NULL,
  `operator_name` text DEFAULT NULL,
  `EletronicAddress` text DEFAULT NULL,
  `country` varchar(255) DEFAULT NULL,
  `postal_address` text DEFAULT NULL,
  PRIMARY KEY (`operator_id`),
  KEY `country_id` (`country_id`),
  CONSTRAINT `scheme_operators_ibfk_2` FOREIGN KEY (`country_id`) REFERENCES `countries` (`country_id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Exportação de dados não seleccionada.

-- A despejar estrutura para tabela trusted_lists.service_status_history
CREATE TABLE IF NOT EXISTS `service_status_history` (
  `history_id` int(11) NOT NULL AUTO_INCREMENT,
  `service_id` int(11) DEFAULT NULL,
  `previous_status` varchar(20) DEFAULT NULL,
  `status_start_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`history_id`),
  KEY `FK_service_status_history_trust_services` (`service_id`),
  CONSTRAINT `FK_service_status_history_trust_services` FOREIGN KEY (`service_id`) REFERENCES `trust_services` (`service_id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Exportação de dados não seleccionada.

-- A despejar estrutura para tabela trusted_lists.trusted_lists
CREATE TABLE IF NOT EXISTS `trusted_lists` (
  `tsl_id` int(11) NOT NULL AUTO_INCREMENT,
  `country_id` int(11) DEFAULT NULL,
  `Version` int(11) DEFAULT NULL,
  `SequenceNumber` int(11) DEFAULT NULL,
  `TSLType` varchar(255) DEFAULT NULL,
  `SchemeName_lang` varchar(255) DEFAULT NULL,
  `Uri_lang` varchar(255) DEFAULT NULL,
  `SchemeTypeCommunityRules_lang` varchar(255) DEFAULT NULL,
  `schemeTerritory` varchar(10) DEFAULT NULL,
  `PolicyOrLegalNotice_lang` varchar(255) DEFAULT NULL,
  `pointers_to_other_tsl` text DEFAULT NULL,
  `DistributionPoints` varchar(255) DEFAULT NULL,
  `issue_date` datetime DEFAULT NULL,
  `next_update` datetime DEFAULT NULL,
  `status` varchar(250) DEFAULT NULL,
  `signature` binary(255) DEFAULT NULL,
  `Additional_Information` varchar(255) DEFAULT NULL,
  `lotl` int(11) DEFAULT NULL,
  `operator_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`tsl_id`),
  KEY `country_id` (`country_id`),
  KEY `tl_op` (`operator_id`),
  CONSTRAINT `tl_op` FOREIGN KEY (`operator_id`) REFERENCES `scheme_operators` (`operator_id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `trusted_lists_ibfk_1` FOREIGN KEY (`country_id`) REFERENCES `countries` (`country_id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Exportação de dados não seleccionada.

-- A despejar estrutura para tabela trusted_lists.trusted_list_updates
CREATE TABLE IF NOT EXISTS `trusted_list_updates` (
  `update_id` int(11) NOT NULL AUTO_INCREMENT,
  `tsl_id` int(11) DEFAULT NULL,
  `update_date` timestamp NULL DEFAULT NULL,
  `description` text DEFAULT NULL,
  `signature` binary(255) DEFAULT NULL,
  PRIMARY KEY (`update_id`),
  KEY `tsl_id` (`tsl_id`),
  CONSTRAINT `trusted_list_updates_ibfk_1` FOREIGN KEY (`tsl_id`) REFERENCES `trusted_lists` (`tsl_id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Exportação de dados não seleccionada.

-- A despejar estrutura para tabela trusted_lists.trust_services
CREATE TABLE IF NOT EXISTS `trust_services` (
  `service_id` int(11) NOT NULL AUTO_INCREMENT,
  `service_type` varchar(50) DEFAULT NULL,
  `digital_identity` text DEFAULT NULL,
  `status` varchar(250) DEFAULT NULL,
  `status_start_date` datetime DEFAULT NULL,
  `qualifier` text DEFAULT NULL,
  `ServiceName` text DEFAULT NULL,
  `SchemeServiceDefinitionURI` text DEFAULT NULL,
  `operator_id` int(11) DEFAULT NULL,
  `tsp_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`service_id`),
  KEY `ts_op_fk` (`operator_id`),
  KEY `tsp_fk` (`tsp_id`),
  CONSTRAINT `ts_op_fk` FOREIGN KEY (`operator_id`) REFERENCES `scheme_operators` (`operator_id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `tsp_fk` FOREIGN KEY (`tsp_id`) REFERENCES `trust_service_providers` (`tsp_id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Exportação de dados não seleccionada.

-- A despejar estrutura para tabela trusted_lists.trust_service_providers
CREATE TABLE IF NOT EXISTS `trust_service_providers` (
  `tsp_id` int(11) NOT NULL AUTO_INCREMENT,
  `tsl_id` int(11) DEFAULT NULL,
  `name` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `postal_address` text DEFAULT NULL,
  `trade_name` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `EletronicAddress` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `TSPInformationURI` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `operator_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`tsp_id`),
  KEY `trust_service_providers_ibfk_1` (`tsl_id`),
  KEY `tsp_op_fk` (`operator_id`),
  CONSTRAINT `trust_service_providers_ibfk_1` FOREIGN KEY (`tsl_id`) REFERENCES `trusted_lists` (`tsl_id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `tsp_op_fk` FOREIGN KEY (`operator_id`) REFERENCES `scheme_operators` (`operator_id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Exportação de dados não seleccionada.

-- ===========================================================================
-- Tablas de PUBLICACION (Fase 1.5)
--
-- El TLP es un editor: genera el XML de la lista y lo devuelve como descarga
-- del navegador. No lo persiste, no lo versiona y no lo sirve. Estas tres
-- tablas son el estado que necesita el servicio `tl-publisher` para convertir
-- eso en una publicacion de verdad.
--
-- Se crean aqui, y no desde el publicador, para que haya UN solo dueno del
-- esquema. Dos servicios ejecutando DDL sobre la misma base es una carrera
-- esperando a pasar.
-- ===========================================================================

-- Que esquemas de lista existen y que fila de `trusted_lists` los respalda.
--
-- POR QUE POR ESQUEMA Y NO POR PAIS: el art. 22a del Reglamento define las
-- listas por ROL (proveedores de wallet, emisores de PID, QTSPs, organismos
-- del sector publico, registradores de RP), no por territorio. El modelo del
-- upstream es por pais, asi que este mapeo es la traduccion entre los dos.
CREATE TABLE IF NOT EXISTS `tl_schemes` (
  `scheme_slug` VARCHAR(32) NOT NULL,
  `tsl_id`      INT(11) NOT NULL,
  `scheme_name` VARCHAR(255) NOT NULL,
  `tsl_type`    VARCHAR(255) NOT NULL,
  `is_lotl`     TINYINT(1) NOT NULL DEFAULT 0,
  `description` TEXT DEFAULT NULL,
  PRIMARY KEY (`scheme_slug`),
  KEY `tl_schemes_tsl` (`tsl_id`),
  CONSTRAINT `tl_schemes_tsl` FOREIGN KEY (`tsl_id`) REFERENCES `trusted_lists` (`tsl_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- A que lista pertenece cada tipo de servicio, y con que URI se publica.
--
-- POR QUE `canonical_uri`: tres de los ServiceTypeIdentifier que trae el
-- upstream NO son URIs de ETSI. Les falta el segmento `TrstSvc`:
--
--   http://uri.etsi.org/Svc/Svctype/Provider/Wallet      <- errata del upstream
--   http://uri.etsi.org/TrstSvc/Svctype/EAA/Q            <- correcto, misma lista
--
-- Cambiarlos en `trust_services` romperia las filas que el operador ya haya
-- dado de alta desde la interfaz del TLP. En vez de eso se guarda aqui la
-- forma canonica: el TLP sigue viendo lo que escribio, y lo que se PUBLICA es
-- la forma correcta. Un cambio futuro es una fila, no una migracion.
CREATE TABLE IF NOT EXISTS `service_type_registry` (
  `service_type`        VARCHAR(255) NOT NULL,
  `scheme_slug`         VARCHAR(32) NOT NULL,
  `canonical_uri`       VARCHAR(255) DEFAULT NULL,
  `additional_info_uri` VARCHAR(255) DEFAULT NULL,
  `supply_points`       TEXT DEFAULT NULL,
  `notes`               TEXT DEFAULT NULL,
  PRIMARY KEY (`service_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Cada version publicada de cada lista.
--
-- `UNIQUE (scheme_slug, sequence_number)` no es higiene: es lo que impide que
-- dos publicaciones concurrentes calculen el mismo numero y una sobrescriba a
-- la otra. La segunda falla con clave duplicada, que es el comportamiento
-- correcto para algo que se promete inmutable.
CREATE TABLE IF NOT EXISTS `published_lists` (
  `publication_id`     INT(11) NOT NULL AUTO_INCREMENT,
  `tsl_id`             INT(11) NOT NULL,
  `scheme_slug`        VARCHAR(32) NOT NULL,
  `sequence_number`    INT(11) NOT NULL,
  `version`            INT(11) NOT NULL DEFAULT 6,
  `issue_date`         DATETIME NOT NULL,
  `next_update`        DATETIME NOT NULL,
  `xml_sha256`         CHAR(64) NOT NULL,
  `xml_path`           VARCHAR(255) NOT NULL,
  `signer_cert_sha256` CHAR(64) NOT NULL,
  `signer_serial`      VARCHAR(64) NOT NULL,
  `published_by`       INT(11) DEFAULT NULL,
  `published_at`       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `superseded_at`      TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (`publication_id`),
  UNIQUE KEY `uq_scheme_seq` (`scheme_slug`, `sequence_number`),
  KEY `idx_vigente` (`scheme_slug`, `superseded_at`),
  KEY `pub_tsl` (`tsl_id`),
  CONSTRAINT `pub_tsl` FOREIGN KEY (`tsl_id`) REFERENCES `trusted_lists` (`tsl_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

/*!40103 SET TIME_ZONE=IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
