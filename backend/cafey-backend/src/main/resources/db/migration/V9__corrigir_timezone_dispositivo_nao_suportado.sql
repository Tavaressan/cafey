-- Mantém o timezone persistido coerente com os fusos que o firmware consegue avaliar.
-- Dispositivos criados antes da validação de entrada podiam gravar valores arbitrários (ex.: "BRT").
UPDATE dispositivos
SET timezone = 'America/Sao_Paulo',
    atualizado_em = now()
WHERE timezone NOT IN (
  'America/Noronha',
  'America/Sao_Paulo',
  'America/Bahia',
  'America/Fortaleza',
  'America/Recife',
  'America/Maceio',
  'America/Belem',
  'America/Araguaina',
  'America/Santarem',
  'America/Manaus',
  'America/Cuiaba',
  'America/Campo_Grande',
  'America/Porto_Velho',
  'America/Boa_Vista',
  'America/Rio_Branco',
  'America/Eirunepe',
  'UTC',
  'Etc/UTC'
);
