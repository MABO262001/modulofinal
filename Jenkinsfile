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
        GIT_URL = 'https://github.com/MABO262001/modulofinal.git'
        VM_IP = 'host.docker.internal'
        VM_SSH_PORT = '2222'
        APP_PATH = '/tmp'
        REPORTS_DIR = "${WORKSPACE}/modulofin"
        IMAGE_NAME = 'app-segura:latest'
        APP_PORT = '8081'
    }

    stages {
        stage('Verificacion de Entorno') {
            steps {
                sh '''
                    echo "========== ENTORNO =========="
                    echo "Workspace: $WORKSPACE"
                    whoami
                    uname -a
                    java -version
                    git --version
                    docker --version
                    docker ps || true
                '''
            }
        }

        stage('Preparar Workspace') {
            steps {
                sh '''
                    set -e
                    mkdir -p "$WORKSPACE"
                    git config --global --add safe.directory "$WORKSPACE" || true
                    git config --global --add safe.directory '*' || true
                    rm -rf "$WORKSPACE/.git"
                '''
            }
        }

        stage('Descarga de Codigo') {
            steps {
                git branch: 'main', url: "${env.GIT_URL}"
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
                    docker run --rm \
                      -u 0:0 \
                      -v "$WORKSPACE:/src" \
                      -w /src \
                      -v "$REPORTS_DIR:/reports" \
                      returntocorp/semgrep \
                      semgrep --config auto . \
                      --json \
                      --output /reports/semgrep_report.json || true

                    echo "Contenido tras Semgrep:"
                    ls -lah "$REPORTS_DIR" || true
                '''
            }
        }

        stage('Compilacion') {
            steps {
                sh '''
                    mvn -B clean package -DskipTests
                    ls -lah target
                '''
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    docker build -t "$IMAGE_NAME" .
                '''
            }
        }

        stage('Container Scan - Trivy') {
            steps {
                sh '''
                    docker run --rm \
                      -u 0:0 \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      -v "$REPORTS_DIR:/mnt" \
                      aquasec/trivy:latest image \
                      --timeout 15m \
                      --format json \
                      --output /mnt/trivy_report.json \
                      "$IMAGE_NAME" || true

                    echo "Contenido tras Trivy:"
                    ls -lah "$REPORTS_DIR" || true
                '''
            }
        }

        stage('Despliegue') {
            when {
                expression { return params.ENABLE_DEPLOY }
            }
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'vm-access', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
                    sh '''
                        set -euxo pipefail

                        echo "========== PRUEBA SSH =========="
                        ssh -vv -p "$VM_SSH_PORT" \
                          -o StrictHostKeyChecking=no \
                          -o BatchMode=yes \
                          -o ConnectTimeout=10 \
                          -i "$SSH_KEY" \
                          "$SSH_USER@$VM_IP" 'echo CONEXION_OK && whoami && hostname && java -version'

                        echo "========== DETENER APP ANTERIOR =========="
                        ssh -p "$VM_SSH_PORT" \
                          -o StrictHostKeyChecking=no \
                          -o BatchMode=yes \
                          -i "$SSH_KEY" \
                          "$SSH_USER@$VM_IP" '
                            pkill -f app.jar || true
                            fuser -k '"$APP_PORT"'/tcp || true
                            sleep 2
                          '

                        echo "========== COPIAR JAR =========="
                        scp -v -P "$VM_SSH_PORT" \
                          -o StrictHostKeyChecking=no \
                          -o BatchMode=yes \
                          -i "$SSH_KEY" \
                          target/*.jar \
                          "$SSH_USER@$VM_IP:$APP_PATH/app.jar"

                        echo "========== ARRANCAR APP =========="
                        ssh -p "$VM_SSH_PORT" \
                          -o StrictHostKeyChecking=no \
                          -o BatchMode=yes \
                          -i "$SSH_KEY" \
                          "$SSH_USER@$VM_IP" '
                            nohup java -jar /tmp/app.jar --server.port='"$APP_PORT"' > /tmp/app.log 2>&1 &
                          '

                        echo "========== VALIDAR ARRANQUE =========="
                        ssh -p "$VM_SSH_PORT" \
                          -o StrictHostKeyChecking=no \
                          -o BatchMode=yes \
                          -i "$SSH_KEY" \
                          "$SSH_USER@$VM_IP" '
                            sleep 10
                            echo "---- JAR ----"
                            ls -l /tmp/app.jar
                            echo "---- PUERTO ----"
                            ss -tulpn | grep '"$APP_PORT"' || true
                            echo "---- PROCESO ----"
                            ps -ef | grep app.jar | grep -v grep || true
                            echo "---- LOG ----"
                            tail -n 50 /tmp/app.log || true
                            echo "---- HEALTH ----"
                            curl -f http://127.0.0.1:'"$APP_PORT"'/health
                          '
                    '''
                }
            }
        }

        stage('DAST - OWASP ZAP') {
            when {
                allOf {
                    expression { return params.ENABLE_DEPLOY }
                    expression { return params.ENABLE_DAST }
                }
            }
            steps {
                sh '''
                    chmod -R 777 "$REPORTS_DIR" || true

                    docker run --rm \
                      -u 0:0 \
                      -v "$REPORTS_DIR:/zap/wrk:rw" \
                      ghcr.io/zaproxy/zaproxy:stable zap-baseline.py \
                      -t http://host.docker.internal:8081/health \
                      -J zap_report.json \
                      -r zap_report.html || true

                    echo "Contenido tras ZAP:"
                    ls -lah "$REPORTS_DIR" || true
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
