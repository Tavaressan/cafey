-- Rótulo livre do agendamento (ex.: "Café da manhã"). NULL = sem descrição.
ALTER TABLE agendamentos ADD COLUMN descricao varchar(100);
