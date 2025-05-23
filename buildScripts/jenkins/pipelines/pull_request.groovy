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
        TF_ENV    = 'dev'
    }

    options {
        ansiColor('xterm')
        skipDefaultCheckout()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Checkout PR branch') {
            steps {
                checkout([$class: 'GitSCM',
                          branches: [[name: "${env.ghprbActualCommit}"]],
                          userRemoteConfigs: [[
                              url: env.REPO_URL,
                              credentialsId: 'github-ssh-key',
                              refspec: "+refs/pull/${env.ghprbPullId}/head:refs/remotes/origin/pr/${env.ghprbPullId}"
                          ]]])
            }
        }

        stage('Determine TF_ENV') {
            steps {
                script {
                    def tfvarsFile = sh(
                        script: 'git diff --name-only origin/master...HEAD | grep .tfvars || true',
                        returnStdout: true
                    ).trim().split('\n').find { it.endsWith('.tfvars') }
        
                    if (tfvarsFile) {
                        env.TF_ENV = tfvarsFile.replace('.tfvars','')
                        echo "Detected TF_ENV from PR diff: ${env.TF_ENV}"
                    } else {
                        echo "No .tfvars changes — using default TF_ENV: ${env.TF_ENV}"
                    }
                }
            }
        }

        stage('Terraform Plan') {
            steps {
                script {
                    withCredentials([string(credentialsId: 'terraform-cloud-token', variable: 'RAW_TFC_TOKEN')]) {
                        withEnv(["TERRAFORM_CLOUD_TOKEN=${env.RAW_TFC_TOKEN}"]) {
                            sh '''
                                set -eux
                                terraform init
                                terraform workspace select -or-create $TF_ENV
                                terraform plan -var-file="$TF_ENV.tfvars"
                            '''
                        }
                    }
                }
            }
        }
    }

    post {
        cleanup { cleanWs() }
    }
}
