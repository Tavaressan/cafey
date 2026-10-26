#!/usr/bin/env python3
"""
Script de Fábrica: Identidade AWS IoT com CSR gerado no ESP32 (INFRA-10, Issue #274).

Fluxo em que a chave privada NUNCA sai do microcontrolador:
1. Conecta na porta serial e solicita a geração do par de chaves ECC P-256 e extração do CSR PEM.
2. Cria a Thing no AWS IoT Core com UUID em minúsculas.
3. Assina o CSR no AWS IoT (CreateCertificateFromCsr) e anexa a política 'cafey-device-policy'.
4. Gera a partição NVS contendo apenas endpoint, client_id, root CA e certificado de cliente (SEM CHAVE PRIVADA).
5. Grava a partição NVS no offset 0x9000.
6. Emite o comando SQL para registro do dispositivo sem proprietário no inventário do backend.
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
import uuid

def log(msg: str):
    print(f"[CAFEY FACTORY] {msg}")

def read_csr_from_serial(port: str, baudrate: int, thing_name: str, timeout_sec: int = 15) -> str:
    try:
        import serial
    except ImportError:
        log("ERRO: Pacote 'pyserial' não instalado. Execute: pip install pyserial")
        sys.exit(1)

    log(f"Abrindo porta serial {port} a {baudrate} bps...")
    with serial.Serial(port, baudrate, timeout=1) as ser:
        time.sleep(1.5)  # Aguarda estabilização do DTR/RTS
        cmd = f"factory_csr {thing_name}\n"
        log(f"Enviando comando de console: {cmd.strip()}")
        ser.write(cmd.encode("utf-8"))

        start_time = time.time()
        in_csr = False
        csr_lines = []

        while time.time() - start_time < timeout_sec:
            line = ser.readline().decode("utf-8", errors="ignore").strip()
            if not line:
                continue
            if "BEGIN CAFEY FACTORY CSR" in line or "BEGIN CERTIFICATE REQUEST" in line:
                in_csr = True
                if "BEGIN CERTIFICATE REQUEST" in line:
                    csr_lines.append(line)
                continue
            if in_csr:
                if "END CAFEY FACTORY CSR" in line:
                    break
                csr_lines.append(line)
                if "END CERTIFICATE REQUEST" in line:
                    break

        if not csr_lines:
            raise TimeoutError(f"Timeout aguardando CSR na porta {port}.")

        return "\n".join(csr_lines) + "\n"

def run_cmd(cmd: list, check: bool = True) -> str:
    log(f"Executando: {' '.join(cmd)}")
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if check and res.returncode != 0:
        log(f"FALHA no comando: {res.stderr}")
        raise RuntimeError(res.stderr)
    return res.stdout.strip()

def main():
    parser = argparse.ArgumentParser(description="Provisionamento de fábrica Cafey via CSR no hardware")
    parser.add_argument("--port", help="Porta serial do ESP32 (ex.: /dev/ttyUSB0 ou COM3)")
    parser.add_argument("--baud", type=int, default=115200, help="Baudrate serial (default 115200)")
    parser.add_argument("--csr-file", help="Arquivo CSR existente (caso não leia via serial)")
    parser.add_argument("--thing-name", help="UUID da Thing (opcional; gera UUIDv4 se omitido)")
    parser.add_argument("--region", default="sa-east-1", help="Região AWS (default sa-east-1)")
    parser.add_argument("--policy", default="cafey-device-policy", help="Nome da política IoT")
    parser.add_argument("--root-ca", default="firmware/certs/amazon-root-ca-1.pem", help="Path da CA raiz da AWS")
    parser.add_argument("--out-dir", default="firmware/certs/factory_out", help="Diretório de artefatos gerados")
    parser.add_argument("--dry-run", action="store_true", help="Simula operações sem chamada real à AWS/esptool")
    args = parser.parse_args()

    thing_uuid = (args.thing_name or str(uuid.uuid4())).lower()
    os.makedirs(args.out_dir, exist_ok=True)
    log(f"Iniciando provisionamento para Thing UUID: {thing_uuid}")

    # 1. Obtenção do CSR
    csr_pem_path = os.path.join(args.out_dir, f"{thing_uuid}.csr")
    if args.csr_file and os.path.exists(args.csr_file):
        log(f"Utilizando arquivo CSR fornecido: {args.csr_file}")
        with open(args.csr_file, "r") as f:
            csr_content = f.read()
    elif args.port:
        csr_content = read_csr_from_serial(args.port, args.baud, thing_uuid)
    elif args.dry_run:
        log("Modo dry-run: gerando CSR simulado.")
        csr_content = f"-----BEGIN CERTIFICATE REQUEST-----\nMOCK_CSR_DATA_{thing_uuid}\n-----END CERTIFICATE REQUEST-----\n"
    else:
        log("ERRO: É necessário informar --port ou --csr-file.")
        sys.exit(1)

    with open(csr_pem_path, "w") as f:
        f.write(csr_content)
    log(f"CSR salvo em: {csr_pem_path}")

    # 2. Chamadas AWS IoT
    cert_pem_path = os.path.join(args.out_dir, f"{thing_uuid}-cert.pem")
    cert_arn = f"arn:aws:iot:{args.region}:076248672901:cert/mock_{thing_uuid[:8]}"
    endpoint_ats = f"a3gha475fc91p-ats.iot.{args.region}.amazonaws.com"

    if not args.dry_run:
        # Cria a Thing no AWS IoT Core
        run_cmd(["aws", "iot", "create-thing", "--thing-name", thing_uuid, "--region", args.region])

        # Cria o certificado assinado a partir do CSR
        cert_res = json.loads(run_cmd([
            "aws", "iot", "create-certificate-from-csr",
            "--certificate-signing-request", f"file://{csr_pem_path}",
            "--set-as-active",
            "--region", args.region
        ]))
        cert_arn = cert_res["certificateArn"]
        cert_pem = cert_res["certificatePem"]
        with open(cert_pem_path, "w") as f:
            f.write(cert_pem)

        # Anexa Thing ao Certificado e Política ao Certificado
        run_cmd(["aws", "iot", "attach-thing-principal", "--thing-name", thing_uuid, "--principal", cert_arn, "--region", args.region])
        run_cmd(["aws", "iot", "attach-policy", "--policy-name", args.policy, "--target", cert_arn, "--region", args.region])
    else:
        log(f"[DRY-RUN] Simulado: aws iot create-thing --thing-name {thing_uuid}")
        log(f"[DRY-RUN] Simulado: aws iot create-certificate-from-csr com {csr_pem_path}")
        with open(cert_pem_path, "w") as f:
            f.write("-----BEGIN CERTIFICATE-----\nMOCK_AWS_CERT\n-----END CERTIFICATE-----\n")

    log(f"Certificado AWS IoT emitido e salvo em {cert_pem_path}")

    # 3. Geração do CSV de provisionamento da NVS (SEM chave privada)
    csv_path = os.path.join(args.out_dir, f"{thing_uuid}_nvs.csv")
    bin_path = os.path.join(args.out_dir, f"{thing_uuid}_nvs.bin")
    abs_root_ca = os.path.abspath(args.root_ca)
    abs_cert = os.path.abspath(cert_pem_path)

    csv_content = f"""key,type,encoding,value
cafey_mqtt,namespace,,
broker,data,string,mqtts://{endpoint_ats}:8883
clientid,data,string,{thing_uuid}
rootca,file,string,{abs_root_ca}
cert,file,string,{abs_cert}
"""
    with open(csv_path, "w") as f:
        f.write(csv_content)
    log(f"CSV de partição NVS gerado (sem chave privada): {csv_path}")

    # 4. Geração do binário NVS via nvs_partition_gen.py
    nvs_gen = os.path.expandvars("$IDF_PATH/components/nvs_flash/nvs_partition_generator/nvs_partition_gen.py")
    if os.path.exists(nvs_gen) and not args.dry_run:
        run_cmd([sys.executable, nvs_gen, "generate", csv_path, bin_path, "0x6000"])
        log(f"Binário NVS gerado: {bin_path}")
    else:
        log(f"[AVISO] nvs_partition_gen.py não executado (arquivo={nvs_gen}).")

    # 5. Gravação na placa via esptool
    if args.port and not args.dry_run and os.path.exists(bin_path):
        log(f"Gravando NVS no offset 0x9000 na porta {args.port}...")
        run_cmd([sys.executable, "-m", "esptool", "--port", args.port, "write_flash", "0x9000", bin_path])
        log("Gravação concluída com sucesso!")

    # 6. SQL de inventário para o backend (sem proprietário)
    log("==================================================================")
    log("DISPOSITIVO PROVISIONADO DE FÁBRICA COM SUCESSO!")
    log(f"Thing UUID: {thing_uuid}")
    log(f"Certificado ARN: {cert_arn}")
    log("Chave Privada: Mantida EXCLUSIVAMENTE na NVS do hardware (segurança máxima).")
    log("")
    log("Para registrar a placa no inventário do backend (dispositivo sem dono):")
    sql = f"""INSERT INTO dispositivos (id, nome, timezone, estado, online, versao_agendamentos, criado_em, atualizado_em)
VALUES ('{thing_uuid}', 'Caféy', 'America/Sao_Paulo', 'PRONTO', false, 1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;"""
    print(sql)
    log("==================================================================")

if __name__ == "__main__":
    main()
