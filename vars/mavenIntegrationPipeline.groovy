def call(body) {
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
            stage('Build') {
                agent { label 'master' }
                steps {
                    sh 'mvn clean package'
                    stash 'workspace'
                }
            }
            stage('Build Container & Push to ACR') {
                agent { label 'master' }
                steps {
                    unstash 'workspace'
                    script {
                        docker.withRegistry("${env.DOCKER_REGISTRY_URL}", 'docker_registry_credentials') {
                            def repo_name = env.GIT_URL.replaceFirst(/^.*\/([^\/]+?).git$/, '$1')
                            def acrRepoName =  "${repo_name}/test"
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
                    echo 'cleanup'
                }
                post {
                    cleanup {
                        deleteDir()
                    }
                }
            }
        }
    }
}