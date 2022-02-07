pipeline {
    agent none

    options {
        skipDefaultCheckout()
    }

    stages {
        stage("Clear tokens") {
            agent { label 'master' }
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: 'master']],
                    userRemoteConfigs: [[url: 'https://github.com/Civil-Service-Human-Resources/lpg-jenkins-library']]
                ])
                withCredentials([
                    string(crdentialsId: 'prod_mysql_host', variable: 'SQL_HOST'),
                    string(crdentialsId: 'prod_mysql_gp_user', variable: 'SQL_USER'),
                    string(crdentialsId: 'Prod_MySQL_GP_Pass', variable: 'SQL_PASSWORD')
                ]) {
                    dir('./release-pipelines/jobs/teardown_tokens') {
                        script {
                            sh 'python3 -m pip install -r requirements.txt'
                            sh 'python3 script.py'
                        }
                    }
                }
            }
        }
    }
}