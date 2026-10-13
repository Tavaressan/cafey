-- Duração de preparo por agendamento (segundos). NULL = usa dispositivos.duracao_preparo_s.
-- A validação dos valores aceitos (240/360/480/600) fica no request, não no banco.
ALTER TABLE agendamentos ADD COLUMN duracao_preparo_s integer;
