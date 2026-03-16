pipeline {
    agent any

    tools {
        maven 'maven3'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    triggers {
        githubPush()
    }

    parameters {
        booleanParam(name: 'ENABLE_DEPLOY', defaultValue: true, description: 'Desplegar a Ubuntu por SSH')
        booleanParam(name: 'ENABLE_DAST', defaultValue: true, description: 'Ejecutar ZAP contra la app desplegada')
        booleanParam(name: 'ENABLE_SNYK', defaultValue: true, description: 'Ejecutar Snyk para analisis SCA')
        booleanParam(name: 'ENABLE_DEFECTDOJO', defaultValue: true, description: 'Subir reportes a DefectDojo')
    }

    environment {
        GIT_URL     = 'https://github.com/MABO262001/modulofinal.git'
        GIT_BRANCH  = 'main'
        VM_IP       = 'host.docker.internal'
        VM_SSH_PORT = '2222'
        APP_PATH    = '/tmp'
        REPORTS_DIR = "${WORKSPACE}/modulofin"
        IMAGE_NAME  = 'app-segura:latest'
        APP_PORT    = '8081'
        SSH_OPTS    = '-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o BatchMode=yes -o ConnectTimeout=20'

        SNYK_TOKEN         = credentials('snyk-token')
        DOJO_API_KEY       = credentials('defectdojo-api-key')
        DOJO_ENGAGEMENT_ID = credentials('defectdojo-engagement-id')
        DOJO_URL           = 'http://host.docker.internal:9000'
    }

    stages {
        stage('Verificacion de Entorno') {
            steps {
                sh '''
                    set +e
                    echo "========== ENTORNO =========="
                    echo "Workspace: $WORKSPACE"
                    whoami
                    uname -a
                    java -version
                    git --version
                    docker --version
                    docker ps || true
                    getent hosts host.docker.internal || true

                    echo "========== PRUEBA DEFECTDOJO =========="
                    curl -I --max-time 15 "$DOJO_URL" || true
                '''
            }
        }

        stage('Descarga de Codigo') {
            steps {
                deleteDir()
                git branch: "${env.GIT_BRANCH}", url: "${env.GIT_URL}"
            }
        }

        stage('Preparar Reportes') {
            steps {
                sh '''
                    set -e
                    rm -rf "$REPORTS_DIR"
                    mkdir -p "$REPORTS_DIR"
                    chmod -R 777 "$REPORTS_DIR"
                    ls -lah "$REPORTS_DIR"
                '''
            }
        }

        stage('SAST - Semgrep') {
            steps {
                sh '''
                    set +e
                    docker rm -f semgrep_scan >/dev/null 2>&1 || true
                    rm -f "$REPORTS_DIR/semgrep_report.json" || true

                    docker run --name semgrep_scan \
                      -u 0:0 \
                      -v "$WORKSPACE:/src" \
                      -w /src \
                      returntocorp/semgrep \
                      sh -c 'semgrep --config auto . --json --output /tmp/semgrep_report.json'

                    SEMGREP_EXIT=$?
                    echo "Semgrep exit code: $SEMGREP_EXIT"

                    docker cp semgrep_scan:/tmp/semgrep_report.json "$REPORTS_DIR/semgrep_report.json" || true
                    docker rm -f semgrep_scan >/dev/null 2>&1 || true

                    echo "Contenido tras Semgrep:"
                    ls -lah "$REPORTS_DIR" || true
                    exit 0
                '''
            }
        }

        stage('Compilacion') {
            steps {
                sh '''
                    set -e
                    mvn -B clean package -DskipTests
                    ls -lah target
                '''
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    set -e
                    docker build -t "$IMAGE_NAME" .
                '''
            }
        }

        stage('Container Scan - Trivy') {
            steps {
                sh '''
                    set +e
                    docker rm -f trivy_scan >/dev/null 2>&1 || true
                    rm -f "$REPORTS_DIR/trivy_report.json" || true

                    docker run --name trivy_scan \
                      -u 0:0 \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      aquasec/trivy:latest image \
                      --timeout 15m \
                      --format json \
                      --output /tmp/trivy_report.json \
                      "$IMAGE_NAME"

                    TRIVY_EXIT=$?
                    echo "Trivy exit code: $TRIVY_EXIT"

                    docker cp trivy_scan:/tmp/trivy_report.json "$REPORTS_DIR/trivy_report.json" || true
                    docker rm -f trivy_scan >/dev/null 2>&1 || true

                    echo "Contenido tras Trivy:"
                    ls -lah "$REPORTS_DIR" || true
                    exit 0
                '''
            }
        }

        stage('SCA - Snyk') {
            when {
                expression { params.ENABLE_SNYK }
            }
            steps {
                sh '''
                    set +e
                    rm -f snyk snyk_report.json "$REPORTS_DIR/snyk_report.json" || true

                    curl -sL https://static.snyk.io/cli/latest/snyk-linux -o snyk
                    chmod +x snyk

                    ./snyk auth "$SNYK_TOKEN"
                    ./snyk test --file=pom.xml --json > snyk_report.json

                    SNYK_EXIT=$?
                    echo "Snyk exit code: $SNYK_EXIT"

                    cp snyk_report.json "$REPORTS_DIR/snyk_report.json" || true

                    echo "Contenido tras Snyk:"
                    ls -lah "$REPORTS_DIR" || true
                    exit 0
                '''
            }
        }

        stage('Despliegue') {
            when {
                expression { params.ENABLE_DEPLOY }
            }
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'vm-access',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {
                    sh '''
                        set -euxo pipefail

                        echo "========== PRUEBA SSH =========="
                        ssh $SSH_OPTS -p "$VM_SSH_PORT" -i "$SSH_KEY" \
                          "$SSH_USER@$VM_IP" 'echo CONEXION_OK && whoami && hostname && java -version'

                        echo "========== DETENER APP ANTERIOR =========="
                        ssh $SSH_OPTS -p "$VM_SSH_PORT" -i "$SSH_KEY" \
                          "$SSH_USER@$VM_IP" "
                            fuser -k ${APP_PORT}/tcp || true
                            sleep 2
                          "

                        echo "========== COPIAR JAR =========="
                        scp $SSH_OPTS -P "$VM_SSH_PORT" -i "$SSH_KEY" \
                          target/*.jar \
                          "$SSH_USER@$VM_IP:$APP_PATH/app.jar"

                        echo "========== ARRANCAR APP =========="
                        ssh $SSH_OPTS -p "$VM_SSH_PORT" -i "$SSH_KEY" \
                          "$SSH_USER@$VM_IP" "
                            nohup java -jar $APP_PATH/app.jar --server.port=${APP_PORT} > /tmp/app.log 2>&1 &
                          "

                        echo "========== VALIDAR ARRANQUE EN VM =========="
                        ssh $SSH_OPTS -p "$VM_SSH_PORT" -i "$SSH_KEY" \
                          "$SSH_USER@$VM_IP" "
                            sleep 10
                            echo '---- JAR ----'
                            ls -l $APP_PATH/app.jar
                            echo '---- PUERTO ----'
                            ss -tulpn | grep ${APP_PORT} || true
                            echo '---- PROCESO ----'
                            ps -ef | grep app.jar | grep -v grep || true
                            echo '---- LOG ----'
                            tail -n 50 /tmp/app.log || true
                            echo '---- HEALTH VM ----'
                            curl -f http://127.0.0.1:${APP_PORT}/health
                          "
                    '''
                }
            }
        }

        stage('Validar Acceso desde Jenkins') {
            when {
                expression { params.ENABLE_DEPLOY }
            }
            steps {
                sh '''
                    set -eux
                    echo "========== HEALTH DESDE JENKINS =========="
                    curl -v --max-time 20 http://host.docker.internal:8081/health
                '''
            }
        }

        stage('DAST - OWASP ZAP') {
            when {
                allOf {
                    expression { params.ENABLE_DEPLOY }
                    expression { params.ENABLE_DAST }
                }
            }
            steps {
                sh '''
                    set +e

                    docker rm -f zap_scan zap_export >/dev/null 2>&1 || true
                    docker volume rm zap_reports >/dev/null 2>&1 || true
                    rm -f "$REPORTS_DIR/zap_report.json" "$REPORTS_DIR/zap_report.html" || true

                    docker volume create zap_reports

                    docker run --name zap_scan \
                      -u 0:0 \
                      -v zap_reports:/zap/wrk \
                      ghcr.io/zaproxy/zaproxy:stable \
                      zap-baseline.py \
                      -t http://host.docker.internal:8081/health \
                      -J zap_report.json \
                      -r zap_report.html

                    ZAP_EXIT=$?
                    echo "ZAP exit code: $ZAP_EXIT"

                    docker create --name zap_export -v zap_reports:/from alpine:3.20 sh >/dev/null

                    echo "Intentando copiar reportes ZAP..."
                    docker cp zap_export:/from/zap_report.json "$REPORTS_DIR/zap_report.json" || true
                    docker cp zap_export:/from/zap_report.html "$REPORTS_DIR/zap_report.html" || true

                    echo "Listado del volumen ZAP:"
                    docker start zap_export >/dev/null 2>&1 || true
                    docker exec zap_export sh -c 'ls -lah /from || true' || true

                    docker rm -f zap_scan zap_export >/dev/null 2>&1 || true
                    docker volume rm zap_reports >/dev/null 2>&1 || true

                    echo "Contenido tras ZAP:"
                    ls -lah "$REPORTS_DIR" || true
                    exit 0
                '''
            }
        }

        stage('Subir a DefectDojo') {
            when {
                expression { params.ENABLE_DEFECTDOJO }
            }
            steps {
                sh '''
                    set +e

                    echo "========== VALIDAR API DEFECTDOJO =========="
                    curl -sS -H "Authorization: Token $DOJO_API_KEY" \
                      "$DOJO_URL/api/v2/products/" | head -c 500 || true
                    echo ""

                    echo "========== SUBIENDO REPORTES A DEFECTDOJO =========="

                    if [ -f "$REPORTS_DIR/semgrep_report.json" ]; then
                      echo "Subiendo Semgrep..."
                      curl -sS -X POST "$DOJO_URL/api/v2/import-scan/" \
                        -H "Authorization: Token $DOJO_API_KEY" \
                        -F "scan_type=Semgrep JSON Report" \
                        -F "file=@$REPORTS_DIR/semgrep_report.json" \
                        -F "engagement=$DOJO_ENGAGEMENT_ID" \
                        -F "active=true" \
                        -F "verified=true" \
                        -F "close_old_findings=false"
                      echo ""
                    fi

                    if [ -f "$REPORTS_DIR/trivy_report.json" ]; then
                      echo "Subiendo Trivy..."
                      curl -sS -X POST "$DOJO_URL/api/v2/import-scan/" \
                        -H "Authorization: Token $DOJO_API_KEY" \
                        -F "scan_type=Trivy Scan" \
                        -F "file=@$REPORTS_DIR/trivy_report.json" \
                        -F "engagement=$DOJO_ENGAGEMENT_ID" \
                        -F "active=true" \
                        -F "verified=true" \
                        -F "close_old_findings=false"
                      echo ""
                    fi

                    if [ -f "$REPORTS_DIR/zap_report.json" ]; then
                      echo "Subiendo ZAP..."
                      curl -sS -X POST "$DOJO_URL/api/v2/import-scan/" \
                        -H "Authorization: Token $DOJO_API_KEY" \
                        -F "scan_type=ZAP Scan" \
                        -F "file=@$REPORTS_DIR/zap_report.json" \
                        -F "engagement=$DOJO_ENGAGEMENT_ID" \
                        -F "active=true" \
                        -F "verified=true" \
                        -F "close_old_findings=false"
                      echo ""
                    fi

                    if [ -f "$REPORTS_DIR/snyk_report.json" ]; then
                      echo "Subiendo Snyk..."
                      curl -sS -X POST "$DOJO_URL/api/v2/import-scan/" \
                        -H "Authorization: Token $DOJO_API_KEY" \
                        -F "scan_type=Snyk Scan" \
                        -F "file=@$REPORTS_DIR/snyk_report.json" \
                        -F "engagement=$DOJO_ENGAGEMENT_ID" \
                        -F "active=true" \
                        -F "verified=true" \
                        -F "close_old_findings=false"
                      echo ""
                    fi

                    exit 0
                '''
            }
        }
    }

    post {
        always {
            echo 'Archivando reportes en Jenkins...'
            sh '''
                echo "========== REPORTS_DIR =========="
                ls -lah "$REPORTS_DIR" || true
                echo "========== WORKSPACE =========="
                ls -lah "$WORKSPACE" || true
            '''
            archiveArtifacts artifacts: 'modulofin/**', allowEmptyArchive: true
        }
    }
}