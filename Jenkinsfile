pipeline {
    agent any

    tools {
        maven 'maven3'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(name: 'ENABLE_DEPLOY', defaultValue: true, description: 'Desplegar a Ubuntu por SSH')
        booleanParam(name: 'ENABLE_DAST', defaultValue: true, description: 'Ejecutar ZAP contra la app desplegada')
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
                    echo "Contenido inicial de REPORTS_DIR:"
                    ls -lah "$REPORTS_DIR"
                '''
            }
        }

        stage('SAST - Semgrep') {
            steps {
                sh '''
                    set +e
                    rm -f "$REPORTS_DIR/semgrep_report.json" || true

                    docker run --rm \
                      -u 0:0 \
                      -v "$WORKSPACE:/src" \
                      -v "$REPORTS_DIR:/reports" \
                      -w /src \
                      returntocorp/semgrep \
                      semgrep --config auto . --json --output /reports/semgrep_report.json

                    EXIT_CODE=$?
                    echo "Semgrep exit code: $EXIT_CODE"
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
                    rm -f "$REPORTS_DIR/trivy_report.json" || true

                    docker run --rm \
                      -u 0:0 \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      -v "$REPORTS_DIR:/reports" \
                      aquasec/trivy:latest image \
                      --timeout 15m \
                      --format json \
                      --output /reports/trivy_report.json \
                      "$IMAGE_NAME"

                    EXIT_CODE=$?
                    echo "Trivy exit code: $EXIT_CODE"
                    echo "Contenido tras Trivy:"
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
                    rm -f "$REPORTS_DIR/zap_report.json" "$REPORTS_DIR/zap_report.html" "$REPORTS_DIR/zap_report.md" || true
                    chmod -R 777 "$REPORTS_DIR" || true

                    docker run --rm \
                      -u 0:0 \
                      -v "$REPORTS_DIR:/zap/wrk" \
                      --workdir /zap/wrk \
                      ghcr.io/zaproxy/zaproxy:stable \
                      zap-baseline.py \
                      -t http://host.docker.internal:8081/health \
                      -J zap_report.json \
                      -r zap_report.html \
                      -w zap_report.md

                    EXIT_CODE=$?
                    echo "ZAP exit code: $EXIT_CODE"
                    echo "Contenido tras ZAP:"
                    ls -lah "$REPORTS_DIR" || true
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