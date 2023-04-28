import base64
import os
import subprocess
import yaml

# Prepares kubeconfig.yaml for a technical service user.
# To be executed with a Kyma OIDC user.
# python3 ./kyma_create_service_account.py
#
# References:
# - https://developers.sap.com/tutorials/kyma-create-service-account.html
# - https://sap.stackenterprise.co/questions/5071/5094#5094

namespace = input("Kyma Namespace to be created/updated: ")
script_dir = os.path.dirname(os.path.realpath(__file__))

subprocess.run(['kubectl', 'create', 'namespace', f'{namespace}'])

current_context = subprocess.run(['kubectl', 'config', 'current-context'], capture_output=True).stdout.decode().strip()
print(f'Current Context: {current_context}')

#---- create service account
subprocess.run(['kubectl', 'apply', '-f', 'technical-user-account.yml', '-n', f'{namespace}'])

#---- get server api url
api_server_url = subprocess.run(['kubectl', 'config', 'view', '-ojsonpath=\'{.clusters[0].cluster.server}\''], capture_output=True).stdout.decode().strip("'")
print(f'API Server Url: {api_server_url}')

#---- read secrets from service account
secret_name = subprocess.run(['kubectl', 'get', 'serviceaccount', 'technical-user-account', '-n', f'{namespace}', '-ojsonpath=\'{.secrets[0].name}\''], capture_output=True).stdout.decode().strip("'")
print(f'Secret Name: {secret_name}')

token = subprocess.run(['kubectl', 'get', 'secret', secret_name, '-n', f'{namespace}', '-ojsonpath=\'{.data.token}\''], capture_output=True).stdout.decode().strip("'")
token = base64.b64decode(token).decode()

certificate = subprocess.run(['kubectl', 'get', 'secret', secret_name, '-n', f'{namespace}', '-ojsonpath=\'{.data.ca\\.crt}\''], capture_output=True).stdout.decode().strip("'")
print(f'Certificate: {certificate}')

#---- create kubeconfig file
with open(f"{script_dir}/kubeconfig_template.yaml") as kubeconfig_file:
    kubeconfig_template = yaml.safe_load(kubeconfig_file)
    kubeconfig_template['clusters'][0]['cluster']['server'] = api_server_url
    kubeconfig_template['clusters'][0]['cluster']['certificate-authority-data'] = certificate
    kubeconfig_template['users'][0]['user']['token'] = token
    kubeconfig_template['contexts'][0]['name'] = current_context
    kubeconfig_template['contexts'][0]['context']['namespace'] = namespace
    kubeconfig_template['current-context'] = current_context

with open(f"{script_dir}/kubeconfig.yaml", 'w') as outstream:
    outstream.write(yaml.dump(kubeconfig_template))
