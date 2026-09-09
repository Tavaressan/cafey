-- O H2 não conhece o tipo `citext` (extensão específica do PostgreSQL, usada em Usuario.email).
-- Declaramos um domínio equivalente antes da criação do schema pelo Hibernate (ddl-auto: create-drop)
-- para que os testes de integração consigam persistir entidades reais em vez de mocks.
CREATE DOMAIN IF NOT EXISTS CITEXT AS VARCHAR;
