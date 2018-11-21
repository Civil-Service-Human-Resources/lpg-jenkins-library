def call(def terraformModuleName, def terraformVarTag) {
    sh "set +e; rm -rf lpg-terraform-paas"
    sh "git clone https://github.com/Civil-Service-Human-Resources/lpg-terraform-paas.git"
    dir("lpg-terraform-paas/environments/master") {
        sh "ln -s ${SF}/azure/cabinet-azure/00-integration/state.tf state.tf"
        sh "ln -s ${SF}/azure/cabinet-azure/00-integration/integration-vars.tf integration-vars.tf"
        sh "ln -s ../00-integration/00-vars.tf 00-vars.tf"
        sh "terraform --version"
        sh "terraform init"
        sh "terraform validate"
        sh "terraform plan -target=module.${terraformModuleName} -var '$terraformVarTag=${env.BRANCH_NAME}-${env.BUILD_ID}' -var 'docker_registry_server_username=${acr_username}' -var 'docker_registry_server_password=${acr_password}'"
        //sh "terraform apply -target=module.${terraformModuleName} -var '$terraformVarTag=${env.BRANCH_NAME}-${env.BUILD_ID}' -var 'docker_registry_server_username=${acr_username}' -var 'docker_registry_server_password=${acr_password}' -auto-approve"
    }
}