def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent none
        stages {
            stage('NPM Test') {
                agent { label 'master' }
                steps {
                    script {
                        if (pipelineParams.terraformModuleName == 'lpg-ui'){
                            nodejs(nodeJSInstallationName: 'NodeJS 16.3.0') {
                                sh 'npm cache clean --force'
                                sh 'npm install'
                                sh 'npm run lint'
                                sh 'npm run lint:webdriver'
                                sh 'npm run build'
                                sh 'npm run test:ts'
                                stash 'workspace'
                            }
                        }
                        else if (pipelineParams.terraformModuleName == 'data-transchriver'){
                            nodejs(nodeJSInstallationName: 'NodeJS 10.4.0') {
                                sh 'npm install'
                                stash 'workspace'
                            }
                        }
                        else {
                            nodejs(nodeJSInstallationName: 'NodeJS 16.3.0') {
                                sh 'npm install'
                                sh 'npm run build'
                                sh 'npm test'
                                stash 'workspace'
                            }
                        }
                    }
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
