properties([
  pipelineTriggers([
   [$class: 'GenericTrigger',
    genericVariables: [
     [ key: 'tag', value: '$.ref' ],
     [ key: 'project_name', value: '$.repository.name' ],
     [ key: 'commit', value: '$.changes[0].toHash' ],
     [ key: 'clone_url', value: '$.repository.links.clone[0].href' ]
    ],
     
    causeString: '$committer_name pushed tag $tag to $clone_url referencing $commit',
    
    token: 'java-tag',
    
    printContributedVariables: true,
    printPostContent: true,
    
    regexpFilterText: '$ref',
    regexpFilterExpression: '^refs/tags/.*'
   ]
  ])
 ])

pipeline {
    agent none

    environment {
        jwt_key = "test-key"
    }

    stages {
        stage("Prepare") {
            agent { label 'master' }
            deleteDir()
            sh '''
            echo git clone $clone_url
            echo git checkout tags/$tag
            sleep 1
            '''
        }
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
                        def acrRepoName =  "${project_name}/prod"
                        def customImage = docker.build("${acrRepoName}:${tag}")
                        customImage.push("${tag}")
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
