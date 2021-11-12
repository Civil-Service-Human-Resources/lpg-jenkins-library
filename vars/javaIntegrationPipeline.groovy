def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent none

        environment {
            jwt_key = "test-key"
        }

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
                            def acrRepoName =  "${pipelineParams.dockerRepository}/test"
                            def customImage = docker.build("${acrRepoName}:${env.BRANCH_NAME}")
                            customImage.push("${env.BRANCH_NAME}")
                        }
                    }
                    stash 'workspace'
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
                    cleanup {
                        deleteDir()
                    }
                }
            }
        }
    }
}