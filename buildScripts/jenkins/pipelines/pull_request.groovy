@Library('netgod-jenkins-shared-lib@master') _

pipeline {
    agent any

    environment {
        CICD      = '1'
        TOOL_DIR  = '/var/jenkins_home/tools/bin'
        PATH      = "${TOOL_DIR}:${env.PATH}"
        REPO_NAME = "netgod-terraform"
        ORG = 'yudapinhas'
        REPO_URL  = "git@github.com:${ORG}/${REPO_NAME}.git"
        TF_ENV    = 'dev'    // default for CI
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Determine TF_ENV') {
            steps {
                script {
                    def changedFiles = sh(
                        script: 'git diff --name-only origin/master...HEAD',
                        returnStdout: true
                    ).trim()

                    def tfvarsFile = changedFiles
                                     .split('\n')
                                     .find { it.endsWith('.tfvars') }
                    if (tfvarsFile) {
                        env.TF_ENV = tfvarsFile.replace('.tfvars','')
                        echo "Detected TF_ENV: ${env.TF_ENV}"
                    } else {
                        echo "No .tfvars changes – using default TF_ENV: ${env.TF_ENV}"
                    }
                }
            }
        }

        stage('Prepare Terraform') {
            steps {
                sh '''
                    set -eux
                    terraform init
                    terraform workspace select -or-create ${TF_ENV}
                '''
            }
        }

        stage('Terraform Plan') {
            steps {
                script {
                    def changedFiles = sh(
                        script: 'git diff --name-only origin/master...HEAD',
                        returnStdout: true
                    ).trim().split('\n')

                    if (changedFiles) {
                        echo "Changed files: ${changedFiles.join(', ')}"
                        sh '''
                            set -eux
                            terraform workspace show
                            terraform plan -var-file="${TF_ENV}.tfvars"
                        '''
                    } else {
                        echo "No changes detected. Skipping terraform plan."
                    }
                }
            }
        }
    }

    post {
        cleanup { cleanWs() }
    }
}
