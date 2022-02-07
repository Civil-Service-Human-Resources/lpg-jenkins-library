pipeline {
    agent none

    options {
        skipDefaultCheckout()
    }

    stages {
        stage("Clear tokens") {
            agent { label 'master' }
            steps {
                withCredentials([
                    string(crdentialsId: 'prod_mysql_host', variable: 'SQL_HOST'),
                    string(crdentialsId: 'prod_mysql_gp_user', variable: 'SQL_USER'),
                    string(crdentialsId: 'Prod_MySQL_GP_Pass', variable: 'SQL_PASSWORD')
                ]) {
                    script {
                        sh 'python3 -m pip install -r requirements.txt'
                        sh 'python3 script.py'
                    }
                }
            }
        }
    }
}