-- ============================================================
-- ELIMINACIÓN DE TABLAS ACADÉMICAS LEGACY
-- ============================================================

-- pgcrypto: hashes bcrypt de las claves (crypt / gen_salt), compatibles con BCryptPasswordEncoder de Spring.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DROP TABLE IF EXISTS academic_selections CASCADE;
DROP TABLE IF EXISTS historial_estudiantes CASCADE;
DROP TABLE IF EXISTS programas_academicos CASCADE;
DROP TABLE IF EXISTS datos_estudiantes CASCADE;


-- TABLA: PROGRAMAS ACADÉMICOS (con duración y créditos)
-- ============================================================

CREATE TABLE programas_academicos (
    id_programa      BIGSERIAL PRIMARY KEY,
    codigo           VARCHAR(50)  NOT NULL UNIQUE,
    nombre           VARCHAR(300) NOT NULL,
    nivel_academico  VARCHAR(30)  NOT NULL,
    modalidad        VARCHAR(30)  NOT NULL,
    facultad         VARCHAR(300),
    unidad_academica VARCHAR(300),
    titulo_otorgado  VARCHAR(300) NOT NULL,
    duracion_semestres INTEGER NOT NULL,  -- según el cuadro
    creditos          INTEGER NOT NULL,   -- según el cuadro
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT ck_programa_nivel CHECK (
        nivel_academico IN (
            'TECNICO',
            'TECNOLOGICO',
            'PREGRADO',
            'ESPECIALIZACION',
            'MAESTRIA',
            'DOCTORADO'
        )
    ),

    CONSTRAINT ck_programa_modalidad CHECK (
        modalidad IN (
            'PRESENCIAL',
            'DISTANCIA',
            'VIRTUAL',
            'HIBRIDA'
        )
    )
);

-- ============================================================
-- TABLA: DATOS DE ESTUDIANTES (con tipo_identificacion por defecto CC)
-- ============================================================

CREATE TABLE datos_estudiantes (
    id_estudiante          BIGSERIAL PRIMARY KEY,
    usuario_autenticacion  VARCHAR(50)  NOT NULL UNIQUE,
    -- clave_autenticacion es HEREDADA (texto plano): solo existe en bases migradas hasta que se ejecute el paso 2
    -- de la migración. Las instalaciones nuevas no la usan.
    clave_autenticacion    VARCHAR(50),
    clave_hash             VARCHAR(100),
    nombres                VARCHAR(200) NOT NULL,
    apellidos              VARCHAR(200) NOT NULL,
    tipo_identificacion    VARCHAR(3)   NOT NULL DEFAULT 'CC',
    numero_identificacion  VARCHAR(30)  NOT NULL UNIQUE,
    numero_contacto        VARCHAR(30),
    correo_electronico     VARCHAR(50),

    CONSTRAINT ck_estudiante_clave CHECK (
        clave_hash IS NOT NULL OR clave_autenticacion IS NOT NULL
    ),

    CONSTRAINT ck_estudiante_tipo CHECK (
        tipo_identificacion IN (
            'CC',
            'CE',
            'TI',
            'PAS',
            'PPT',
            'PEP'
        )
    )
);

-- ============================================================
-- TABLA: HISTORIAL DE ESTUDIANTES (antes listado_graduados)
-- ============================================================

CREATE TABLE historial_estudiantes (
    id_historial         BIGSERIAL PRIMARY KEY,
    programa_id          BIGINT NOT NULL,
    estudiante_id        BIGINT NOT NULL,
    fecha_ingreso        DATE,
    fecha_de_grado       DATE,
    promedio_acumulado   VARCHAR(30),
    creditos_aprobados   INTEGER,
    semestres_cursados   INTEGER,
    estado_academico     VARCHAR(30) NOT NULL DEFAULT 'ACTIVO',

    CONSTRAINT fk_historial_programa
        FOREIGN KEY (programa_id)
        REFERENCES programas_academicos(id_programa)
        ON DELETE CASCADE,

    CONSTRAINT fk_historial_estudiante
        FOREIGN KEY (estudiante_id)
        REFERENCES datos_estudiantes(id_estudiante)
        ON DELETE CASCADE,

    CONSTRAINT ck_historial_estado CHECK (
        estado_academico IN (
            'GRADUADO',
            'ACTIVO',
            'RETIRADO',
            'PAUSA'   -- reemplaza a EGRESADO
        )
    )
);

-- ============================================================
-- TRIGGER PARA VALIDAR CONSISTENCIA DE SEMESTRES Y CRÉDITOS
-- ============================================================

CREATE OR REPLACE FUNCTION validar_historial_estudiante()
RETURNS TRIGGER AS $$
DECLARE
    v_semestres_prog INTEGER;
    v_creditos_prog  INTEGER;
BEGIN
    -- Obtener duración y créditos del programa
    SELECT duracion_semestres, creditos
    INTO v_semestres_prog, v_creditos_prog
    FROM programas_academicos
    WHERE id_programa = NEW.programa_id;

    -- Si el estado es GRADUADO, los semestres cursados y créditos deben coincidir
    IF NEW.estado_academico = 'GRADUADO' THEN
        IF NEW.semestres_cursados != v_semestres_prog OR NEW.creditos_aprobados != v_creditos_prog THEN
            RAISE EXCEPTION 'Para estado GRADUADO, los semestres cursados (%) y créditos (%) deben coincidir con la duración (%) y créditos (%) del programa.',
                NEW.semestres_cursados, NEW.creditos_aprobados, v_semestres_prog, v_creditos_prog;
        END IF;
    END IF;

    -- Si el estado es ACTIVO, se permite que los semestres cursados sean menores o iguales a la duración
    -- y los créditos aprobados menores o iguales a los créditos totales (opcional)
    IF NEW.estado_academico = 'ACTIVO' THEN
        IF NEW.semestres_cursados > v_semestres_prog OR NEW.creditos_aprobados > v_creditos_prog THEN
            RAISE EXCEPTION 'Para estado ACTIVO, los semestres cursados (%) no pueden exceder la duración (%) y los créditos (%) no pueden exceder los créditos totales (%).',
                NEW.semestres_cursados, v_semestres_prog, NEW.creditos_aprobados, v_creditos_prog;
        END IF;
    END IF;

    -- Pueden agregarse más validaciones para RETIRADO o PAUSA si se desea

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validar_historial
BEFORE INSERT OR UPDATE ON historial_estudiantes
FOR EACH ROW EXECUTE FUNCTION validar_historial_estudiante();

-- ============================================================
-- ÍNDICES
-- ============================================================

CREATE INDEX idx_programa_nivel ON programas_academicos(nivel_academico);
CREATE INDEX idx_programa_modalidad ON programas_academicos(modalidad);

CREATE INDEX idx_estudiante_tipo_id ON datos_estudiantes(tipo_identificacion, numero_identificacion);
CREATE INDEX idx_estudiante_usuario ON datos_estudiantes(usuario_autenticacion);

CREATE INDEX idx_historial_programa ON historial_estudiantes(programa_id);
CREATE INDEX idx_historial_estudiante ON historial_estudiantes(estudiante_id);
CREATE INDEX idx_historial_fecha_grado ON historial_estudiantes(fecha_de_grado);
CREATE INDEX idx_historial_estado ON historial_estudiantes(estado_academico);
CREATE INDEX idx_historial_estudiante_estado ON historial_estudiantes(estudiante_id, estado_academico);

-- ============================================================
-- TABLA: SELECCIONES ACADÉMICAS (para persistir la elección del usuario)
-- ============================================================

CREATE TABLE academic_selections (
    id_selection      BIGSERIAL PRIMARY KEY,
    usuario_autenticacion VARCHAR(50) NOT NULL,
    numero_identificacion VARCHAR(30) NOT NULL,
    programa_id       BIGINT NOT NULL,
    historial_id      BIGINT NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_selection_programa
        FOREIGN KEY (programa_id)
        REFERENCES programas_academicos(id_programa)
        ON DELETE CASCADE,

    CONSTRAINT fk_selection_historial
        FOREIGN KEY (historial_id)
        REFERENCES historial_estudiantes(id_historial)
        ON DELETE CASCADE
);

CREATE INDEX idx_selection_usuario ON academic_selections(usuario_autenticacion);
CREATE INDEX idx_selection_documento ON academic_selections(numero_identificacion);

-- ============================================================
-- DATOS DE EJEMPLO
-- ============================================================

-- 1. PROGRAMAS ACADÉMICOS
INSERT INTO programas_academicos (codigo, nombre, nivel_academico, modalidad, facultad, unidad_academica, titulo_otorgado, duracion_semestres, creditos) VALUES
('TEC-001', 'Técnico en Desarrollo de Software',        'TECNICO',       'VIRTUAL',    'Ingenierías', 'Escuela de Sistemas', 'Técnico Profesional en Desarrollo de Software', 4, 65),
('TEC-002', 'Técnico en Mantenimiento de Computadores', 'TECNICO',       'PRESENCIAL', 'Ingenierías', 'Escuela de Sistemas', 'Técnico Profesional en Mantenimiento', 4, 65),
('TECN-001', 'Tecnólogo en Análisis de Sistemas',       'TECNOLOGICO',   'PRESENCIAL', 'Ingenierías', 'Escuela de Sistemas', 'Tecnólogo en Análisis de Sistemas', 7, 105),
('PRE-001', 'Ingeniería de Sistemas',                   'PREGRADO',      'PRESENCIAL', 'Ingenierías', 'Escuela de Sistemas', 'Ingeniero de Sistemas', 10, 170),
('PRE-002', 'Ingeniería Civil',                         'PREGRADO',      'PRESENCIAL', 'Ingenierías', 'Escuela de Civil',   'Ingeniero Civil', 10, 170),
('PRE-003', 'Administración de Empresas',               'PREGRADO',      'PRESENCIAL', 'Ciencias Económicas', 'Escuela de Administración', 'Administrador de Empresas', 10, 170),
('ESP-001', 'Especialización en Desarrollo de Software','ESPECIALIZACION','VIRTUAL',    'Ingenierías', 'Escuela de Sistemas', 'Especialista en Desarrollo de Software', 3, 32),
('ESP-002', 'Especialización en Estructuras',           'ESPECIALIZACION','PRESENCIAL', 'Ingenierías', 'Escuela de Civil',   'Especialista en Estructuras', 3, 32),
('MAE-001', 'Maestría en Ingeniería de Sistemas',       'MAESTRIA',      'PRESENCIAL', 'Ingenierías', 'Escuela de Sistemas', 'Magíster en Ingeniería de Sistemas', 4, 50),
('MAE-002', 'Maestría en Administración (MBA)',         'MAESTRIA',      'VIRTUAL',    'Ciencias Económicas', 'Escuela de Administración', 'MBA', 4, 50),
('DOC-001', 'Doctorado en Ciencias de la Computación',  'DOCTORADO',     'PRESENCIAL', 'Ingenierías', 'Escuela de Sistemas', 'Doctor en Ciencias de la Computación', 10, 100),
('DOC-002', 'Doctorado en Ingeniería',                  'DOCTORADO',     'PRESENCIAL', 'Ingenierías', 'Escuela de Civil',   'Doctor en Ingeniería', 10, 100);

-- 2. ESTUDIANTES (12 estudiantes con distintos tipos de identificación)
INSERT INTO datos_estudiantes (usuario_autenticacion, clave_hash, nombres, apellidos, tipo_identificacion, numero_identificacion, numero_contacto, correo_electronico) VALUES
('jperez',    crypt('pass123', gen_salt('bf', 10)), 'Juan',      'Pérez',     'CC',  '1012345678', '3123456789', 'jperez@mail.com'),
('mgomez',    crypt('pass123', gen_salt('bf', 10)), 'María',     'Gómez',     'CC',  '1023456789', '3134567890', 'mgomez@mail.com'),
('crodriguez', crypt('pass123', gen_salt('bf', 10)),'Carlos',    'Rodríguez', 'CE',  '1034567890', '3145678901', 'crodriguez@mail.com'),
('alopez',    crypt('pass123', gen_salt('bf', 10)), 'Ana',       'López',     'TI',  '1045678901', '3156789012', 'alopez@mail.com'),
('pramirez',  crypt('pass123', gen_salt('bf', 10)), 'Pedro',     'Ramírez',   'CC',  '1056789012', '3167890123', 'pramirez@mail.com'),
('lfernandez', crypt('pass123', gen_salt('bf', 10)),'Laura',     'Fernández', 'CC',  '1067890123', '3178901234', 'lfernandez@mail.com'),
('dcastro',   crypt('pass123', gen_salt('bf', 10)), 'Diego',     'Castro',    'PAS', '1078901234', '3189012345', 'dcastro@mail.com'),
('smartinez', crypt('pass123', gen_salt('bf', 10)), 'Sofía',     'Martínez',  'CC',  '1089012345', '3190123456', 'smartinez@mail.com'),
('atorres',   crypt('pass123', gen_salt('bf', 10)), 'Andrés',    'Torres',    'PPT', '1090123456', '3101234567', 'atorres@mail.com'),
('vmoreno',   crypt('pass123', gen_salt('bf', 10)), 'Valentina', 'Moreno',    'PEP', '1101234567', '3112345678', 'vmoreno@mail.com'),
('rvega',     crypt('pass123', gen_salt('bf', 10)), 'Ricardo',   'Vega',      'CC',  '1112345678', '3129876543', 'rvega@mail.com'),
('cortiz',    crypt('pass123', gen_salt('bf', 10)), 'Camila',    'Ortiz',     'TI',  '1123456789', '3139876543', 'cortiz@mail.com'),
('georincon',  crypt('asdfgh', gen_salt('bf', 10)), 'Geovani', 'Rincon',  'CC',  '91296304', '3043730066', 'georincon@gmail.com'),
('luis123',   crypt('123456789', gen_salt('bf', 10)), 'Luis',    'Gonzalez',  'CC',  '1111111111', '3153137328', 'luisangoco26@gmail.com');

-- 3. REGISTROS EN HISTORIAL_ESTUDIANTES (aprox 25 registros)
-- Se asignan a cada estudiante entre 1 y 3 programas, con estados variados.
-- La mayoría graduados, algunos activos, algunos retirados, algunos en pausa.

-- Función auxiliar para generar fechas aleatorias (se usa en los inserts)

-- Insertamos manualmente para tener control sobre los valores y estados.

-- Estudiante 1: Juan Pérez - graduado en PRE-001 (Ingeniería de Sistemas) y MAE-001 (Maestría)
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='PRE-001'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='jperez'), '2015-02-01', '2020-06-30', '4.5', 170, 10, 'GRADUADO'),
((SELECT id_programa FROM programas_academicos WHERE codigo='MAE-001'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='jperez'), '2021-01-15', '2023-06-30', '4.7', 50, 4, 'GRADUADO');

-- Estudiante 2: María Gómez - graduada en PRE-002 (Ingeniería Civil) y activa en ESP-002 (Estructuras)
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='PRE-002'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='mgomez'), '2016-02-01', '2021-06-30', '4.2', 170, 10, 'GRADUADO'),
((SELECT id_programa FROM programas_academicos WHERE codigo='ESP-002'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='mgomez'), '2022-01-15', NULL, '4.3', 16, 2, 'ACTIVO'); -- activo, aún no graduado

-- Estudiante 3: Carlos Rodríguez - graduado en PRE-003 (Administración) y retirado de MAE-002 (MBA)
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='PRE-003'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='crodriguez'), '2014-02-01', '2019-06-30', '4.1', 170, 10, 'GRADUADO'),
((SELECT id_programa FROM programas_academicos WHERE codigo='MAE-002'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='crodriguez'), '2020-01-15', NULL, '3.8', 20, 2, 'RETIRADO'); -- retirado

-- Estudiante 4: Ana López - solo técnico TEC-001, graduada
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='TEC-001'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='alopez'), '2019-02-01', '2021-12-15', '4.0', 65, 4, 'GRADUADO');

-- Estudiante 5: Pedro Ramírez - graduado en PRE-001 y DOCTORADO DOC-001
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='PRE-001'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='pramirez'), '2013-02-01', '2018-06-30', '4.6', 170, 10, 'GRADUADO'),
((SELECT id_programa FROM programas_academicos WHERE codigo='DOC-001'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='pramirez'), '2019-01-15', '2023-12-15', '4.8', 100, 10, 'GRADUADO');

-- Estudiante 6: Laura Fernández - graduada en PRE-002 y especialización ESP-001
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='PRE-002'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='lfernandez'), '2015-02-01', '2020-06-30', '4.3', 170, 10, 'GRADUADO'),
((SELECT id_programa FROM programas_academicos WHERE codigo='ESP-001'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='lfernandez'), '2021-01-15', '2022-12-15', '4.5', 32, 3, 'GRADUADO');

-- Estudiante 7: Diego Castro - activo en PRE-003, con tipo PAS
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='PRE-003'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='dcastro'), '2020-02-01', NULL, '4.0', 80, 5, 'ACTIVO'); -- activo, cursando

-- Estudiante 8: Sofía Martínez - graduada en PRE-001 y MAE-001
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='PRE-001'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='smartinez'), '2014-02-01', '2019-06-30', '4.4', 170, 10, 'GRADUADO'),
((SELECT id_programa FROM programas_academicos WHERE codigo='MAE-001'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='smartinez'), '2020-01-15', '2022-06-30', '4.6', 50, 4, 'GRADUADO');

-- Estudiante 9: Andrés Torres - solo técnico TEC-002, retirado
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='TEC-002'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='atorres'), '2021-02-01', NULL, '3.5', 30, 2, 'RETIRADO'); -- retirado

-- Estudiante 10: Valentina Moreno - graduada en PRE-003 y en pausa en MAE-002 (PEP)
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='PRE-003'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='vmoreno'), '2017-02-01', '2022-06-30', '4.2', 170, 10, 'GRADUADO'),
((SELECT id_programa FROM programas_academicos WHERE codigo='MAE-002'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='vmoreno'), '2023-01-15', NULL, '4.0', 25, 2, 'PAUSA'); -- en pausa

-- Estudiante 11: Ricardo Vega - activo en TECN-001 (Tecnólogo)
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='TECN-001'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='rvega'), '2022-02-01', NULL, '4.1', 60, 4, 'ACTIVO'); -- activo

-- Estudiante 12: Camila Ortiz - graduada en PRE-002 y especialización ESP-002
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='PRE-002'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='cortiz'), '2016-02-01', '2021-06-30', '4.3', 170, 10, 'GRADUADO'),
((SELECT id_programa FROM programas_academicos WHERE codigo='ESP-002'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='cortiz'), '2022-01-15', '2023-12-15', '4.6', 32, 3, 'GRADUADO');

-- Estudiante 13: Geovani Rincon - graduado en PRE-001 y especialización ESP-001
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='PRE-001'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='georincon'), '2016-02-01', '2023-09-12', '3.8', 170, 10, 'GRADUADO'),
((SELECT id_programa FROM programas_academicos WHERE codigo='ESP-001'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='georincon'), '2023-10-15', '2024-10-03', '4.6', 32, 3, 'GRADUADO');

-- Estudiante 14: Luis Gonzalez - graduado en PRE-001
INSERT INTO historial_estudiantes (programa_id, estudiante_id, fecha_ingreso, fecha_de_grado, promedio_acumulado, creditos_aprobados, semestres_cursados, estado_academico)
VALUES
((SELECT id_programa FROM programas_academicos WHERE codigo='PRE-001'), (SELECT id_estudiante FROM datos_estudiantes WHERE usuario_autenticacion='luis123'), '2018-01-15', '2024-12-15', '4.2', 170, 10, 'GRADUADO');


-- ============================================================
-- CONSULTA DE VERIFICACIÓN (opcional)
-- ============================================================
-- SELECT estado_academico, COUNT(*) FROM historial_estudiantes GROUP BY estado_academico;
-- Debería mostrar mayoría GRADUADO, algunos ACTIVO, RETIRADO, PAUSA.
