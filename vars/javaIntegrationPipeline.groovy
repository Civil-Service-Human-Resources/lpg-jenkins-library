def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent none
        stages {
            stage('Gradle Build') {
                agent { label 'master' }
                steps {
                    sh './gradlew clean'
                    sh './gradlew build'
                    stash 'workspace'
                }
            }
            stage('Test') {
                agent { label 'master' }
                steps {
                    unstash 'workspace'
                    publishHTML([allowMissing         : false,
                                 alwaysLinkToLastBuild: true,
                                 keepAll              : true,
                                 reportDir            : 'build/reports/tests/test',
                                 reportFiles          : 'index.html',
                                 reportName           : 'HTML Report',
                                 reportTitles         : ''])
                    stash 'workspace'
                }
            }
            stage('Build Container & Push to ACR') {
                agent { label 'master' }
                steps {
                    unstash 'workspace'
                    script {
                        docker.withRegistry("${env.DOCKER_REGISTRY_URL}", 'docker_registry_credentials') {
                            def customImage = docker.build(pipelineParams.dockerRepository)
                            customImage.push("${env.BRANCH_NAME}-${env.BUILD_ID}")
                        }
                    }
                    stash 'workspace'
                }
            }
            stage('Deploy to Integration') {
                agent { label 'master' }
                steps {
                    script {
                        def tfHome = tool name: 'Terraform', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
                        env.PATH = "${tfHome}:${env.PATH}"
                    }
                    withCredentials([
                            string(credentialsId: 'SECURE_FILES', variable: 'SF'),
                            usernamePassword(credentialsId: 'docker_registry_credentials', usernameVariable: 'acr_username', passwordVariable: 'acr_password')
                    ]) {
                        tfdeploy(pipelineParams.terraformModuleName, pipelineParams.terraformVarTag, pipelineParams.environment)
                    }
                }
            }
            stage('Post') {
                agent { label 'master' }
                steps {
                    unstash 'workspace'
                }
                post {
                    always {
                        junit 'build/test-results/**/TEST-*.xml'
                    }
                }
            }
        }
    }
}