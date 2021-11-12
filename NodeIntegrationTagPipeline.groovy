properties([
  pipelineTriggers([
   [$class: 'GenericTrigger',
    genericVariables: [
     [ key: 'tag', value: '$.ref' ],
     [ key: 'project_name', value: '$.repository.name' ],
     [ key: 'clone_url', value: '$.repository.clone_url' ],
     [ key: 'username', value: '$.sender.login' ]
    ],
     
    causeString: 'commit',
    
    token: 'java-tag',
    
    printContributedVariables: true,
    printPostContent: true,
    
    regexpFilterText: '$tag',
    regexpFilterExpression: '^v\\d{1,2}(\\.\\d{1,2}){2}$'
   ]
  ])
 ])

pipeline {
    agent none
    stages {
        stage("Prepare") {
            agent { label 'master' }
            steps {
                deleteDir()
                script {
                    currentBuild.displayName = "${project_name} ${tag}"
                    currentBuild.description = "Building tag ${tag} for repo ${project_name}. Tag created by ${username}."
                }
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: 'refs/tags/$tag']],
                    userRemoteConfigs: [[url: '$clone_url']]
                ])
                stash 'workspace'
            }
        }
        stage('NPM Test') {
            agent { label 'master' }
            steps {
                script {
                    if (project_name == 'lpg-services'){
                        nodejs(nodeJSInstallationName: 'NodeJS 10.4.0') {
                            sh 'npm cache clean --force'
                            sh 'npm install'
                            sh 'npm run lint'
                            sh 'npm run lint:webdriver'
                            sh 'npm run build'
                            sh 'npm test'
                            stash 'workspace'
                        }
                    }
                    else if (project_name == 'data-transchiver'){
                        nodejs(nodeJSInstallationName: 'NodeJS 10.4.0') {
                            sh 'npm install'
                            stash 'workspace'
                        }
                    }
                    else {
                        nodejs(nodeJSInstallationName: 'NodeJS 10.4.0') {
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
                        def acrRepoName =  "${env.JOB_BASE_NAME}/prod"
                        def customImage = docker.build("${acrRepoName}:${tag}")
                        customImage.push("${tag}")
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
