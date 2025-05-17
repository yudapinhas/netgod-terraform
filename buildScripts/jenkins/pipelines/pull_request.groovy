@Library('netgod-jenkins-shared-lib@master') _

pipeline {
    agent any

    environment {
        CICD = '1'
        TOOL_DIR = '/var/jenkins_home/tools/bin'
        PATH = "${TOOL_DIR}:${env.PATH}"
        REPO_URL = "git@github.com:yudapinhas/netgod-terraform.git"
        TF_ENV = 'dev' // Default for CI
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        skipDefaultCheckout()
    }

    stages {
        stage("Checkout Terraform") {
            steps {
                script {
                    def repoUrl = env.ghprbGhRepository ? "git@github.com:${env.ghprbGhRepository}.git" : env.REPO_URL
                    def commit = env.ghprbActualCommit ?: '*/master'

                    // Debug: Log PR variables
                    echo "ghprbActualCommit: ${env.ghprbActualCommit}"
                    echo "ghprbGhRepository: ${env.ghprbGhRepository}"
                    echo "Checkout commit: ${commit}"
                    echo "Repo URL: ${repoUrl}"

                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: commit]],
                        userRemoteConfigs: [[
                            url: repoUrl,
                            credentialsId: 'github-ssh-key',
                            refspec: '+refs/pull/*:refs/remotes/origin/pr/* +refs/heads/*:refs/remotes/origin/*'
                        ]],
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'netgod-terraform']]
                    ])

                    // Debug: Log checked-out commit
                    dir('netgod-terraform') {
                        sh 'git rev-parse HEAD'
                    }
                }
            }
        }

        stage('Clone Private Creds') {
            steps {
                script {
                    def status = sh(
                        script: 'git clone git@github.com:yudapinhas/netgod-private.git netgod-private',
                        returnStatus: true
                    )
                    if (status != 0) {
                        echo "⚠️  netgod-private repo not reachable — skipping credentials clone"
                    } else {
                        echo "✅  netgod-private cloned successfully"
                    }
                }
            }
        }

        stage('Determine TF_ENV') {
            steps {
                dir('netgod-terraform') {
                    script {
                        def changedFiles = sh(
                            script: 'git diff --name-only origin/master...HEAD',
                            returnStdout: true
                        ).trim()

                        def tfvarsFiles = changedFiles.split('\n').findAll { it.endsWith('.tfvars') }
                        if (tfvarsFiles) {
                            def tfvars = tfvarsFiles[0]
                            env.TF_ENV = tfvars.replace('.tfvars', '')
                            echo "Detected TF_ENV: ${env.TF_ENV} from changed file: ${tfvars}"
                        } else {
                            echo "No .tfvars files changed. Using default TF_ENV: ${env.TF_ENV}"
                        }
                    }
                }
            }
        }

        stage('Prepare Terraform') {
            steps {
                dir('netgod-terraform') {
                    sh '''
                        set -eux
                        terraform init
                        terraform workspace select -or-create ${TF_ENV}
                    '''
                }
            }
        }

        stage('Terraform Plan') {
            steps {
                dir('netgod-terraform') {
                    script {
                        def changedFiles = sh(
                            script: 'git diff --name-only origin/master...HEAD',
                            returnStdout: true
                        ).trim().split('\n')

                        def terraformDir = 'netgod-terraform/'
                        def affectedFiles = changedFiles.findAll { it.startsWith(terraformDir) }

                        if (affectedFiles) {
                            echo "Changed files in scope: ${affectedFiles.join(', ')}"
                            sh """
                                set -eux
                                terraform workspace show
                                terraform plan -var-file="\${TF_ENV}.tfvars"
                            """
                        } else {
                            echo "No changes in ${terraformDir}. Skipping Terraform plan."
                        }
                    }
                }
            }
        }

        stage('CI Passed - Notify Yuda') {
            steps {
                script {
                    def emailInfo = notifyYuda('SUCCESS')
                    emailext(
                        to: emailInfo.to,
                        subject: emailInfo.subject,
                        body: emailInfo.body,
                        mimeType: emailInfo.mimeType
                    )
                }
            }
        }
    }

    post {
        failure {
            script {
                def emailInfo = notifyYuda('FAILURE')
                emailext(
                    to: emailInfo.to,
                    subject: emailInfo.subject,
                    body: emailInfo.body,
                    mimeType: emailInfo.mimeType
                )
            }
        }
        cleanup {
            cleanWs()
        }
    }
}
