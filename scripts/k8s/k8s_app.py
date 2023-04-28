import base64
import logging
import subprocess
import json
import yaml
import os
from retry import retry

logger = logging.getLogger("k8s_app")
logger.setLevel(logging.DEBUG)
current_dir = os.path.dirname(os.path.realpath(__file__))


class DeployedApp:

    def __init__(self, secrets):
        self.secrets = secrets

    @property
    def clientid(self):
        return self.get_property('clientid')

    @property
    def clientsecret(self):
        return self.get_property('clientsecret')

    @property
    def key(self):
        return self.get_property('key')

    @property
    def certificate(self):
        return self.get_property('certificate')

    @property
    def ias_url(self):
        return self.get_property('url')

    @property
    def domains(self):
        return self.get_property('domains')

    @property
    def instanceid(self):
        return self.get_property('authorization_instance_id')

    @property
    def authz_url(self):
        return self.get_property('authorization_url')

    def get_property(self, property_name):
        secret = base64.b64decode(self.secrets[property_name]).decode()
        logger.debug(f'Secret {property_name}: {secret}')
        return secret

    def __str__(self):
        return json.dumps(self.secrets, indent=2)


class K8sApp:
    deployed_app = None
    image_repository = "cloud-security-integration.int.repositories.cloud.sap"
    image_version = "e2e-test"

    def __init__(self, name, credentials, app_router_name=None):
        if name is None:
            raise(Exception('Name must be provided'))
        self.name = name
        self.app_router_name = app_router_name
        self.credentials = credentials

    @property
    def working_dir(self):
        return '../' + self.name

    def prepare_sap_image_registry(self):
        subprocess.run(['kubectl', 'create', 'secret', 'docker-registry', 'sap-repo-registry', f'--docker-server={self.image_repository}', f'--docker-username={self.credentials.common_artifactory_user}', f'--docker-password={self.credentials.common_artifactory_api_token}', '-n', f'{self.credentials.namespace}'])

    def prepare_images(self):
        if self.deployed_app is not None:
            return
        self.prepare_image(self.working_dir, self.name)
        self.prepare_image(f'{self.working_dir}/approuter', self.app_router_name)

    def prepare_image(self, working_dir, app_name):
        if app_name == "spring-security-ams":
            subprocess.run(['mvn', '-q', 'spring-boot:build-image', f'-Dspring-boot.build-image.imageName={self.image_repository}/{app_name}:{self.image_version}', '-DskipUnitTests=true'], cwd=working_dir).check_returncode()
        else:
            try:
                with open(f"{working_dir}/Dockerfile", encoding="utf-8"):
                    subprocess.run(['docker', 'build', '-t', f'{self.image_repository}/{app_name}:{self.image_version}', '-f', './Dockerfile', '.'], cwd=working_dir).check_returncode()
            except:
                raise AssertionError('There exists no Dockerfile')
        subprocess.run(['docker', 'login', self.image_repository, '--username', self.credentials.common_artifactory_user, '--password', self.credentials.common_artifactory_api_token], cwd=working_dir).check_returncode()
        subprocess.run(['docker', 'push', f'{self.image_repository}/{app_name}:{self.image_version}'], cwd=working_dir).check_returncode()

    def deploy(self):
        if self.deployed_app is not None:
            return

        self.__deploy()
        self.__upload_base_dcl()

        cmd = ['kubectl', 'get', 'secret', 'identity', '-o', 'jsonpath={.data}', '-n', f'{self.credentials.namespace}']
        secret_string = subprocess.run(cmd, capture_output=True).stdout.decode()

        secret_json = json.loads(secret_string)
        self.deployed_app = DeployedApp(secret_json)

    @retry(subprocess.CalledProcessError, tries=7, delay=30) # wait until secret is available
    def __upload_base_dcl(self):
        subprocess.run(['kubectl', 'dcl', 'upload', 'identity', 'src/main/resources', '--namespace', f'{self.credentials.namespace}'], cwd=self.working_dir).check_returncode()


    def __deploy(self):
        deployment_file_path = os.path.join(os.path.realpath(self.working_dir), 'k8s', 'deployment.yml')
        try:
            with open(deployment_file_path, encoding="utf-8") as deployment_file:
                docs = yaml.load_all(deployment_file, Loader=yaml.FullLoader)

                with open("tmp_deployment.yml", mode="w", encoding="utf-8") as new_deployment_file:
                    for doc in docs:
                        new_deployment_file.write("---\n")
                        if doc['kind'] == 'Deployment':
                            doc['spec']['template']['spec']['imagePullSecrets'] = [{'name': 'sap-repo-registry'}] # secret created with scripts/util/k8s_app.py
                            deployment_yaml = yaml.dump(doc) \
                                .replace("<YOUR IMAGE REPOSITORY>", f'{self.image_repository}/{self.name}:{self.image_version}') \
                                .replace("<YOUR APPROUTER IMAGE REPOSITORY>",  f'{self.image_repository}/{self.app_router_name}:{self.image_version}')
                            logger.debug(deployment_yaml)
                            new_deployment_file.write(deployment_yaml)
                        elif doc['kind'] == 'Service':
                            service_yaml = yaml.dump(doc) \
                                .replace("targetPort: 5000", "targetPort: 8080") # call ams sample direct instead of approuter
                            logger.debug(service_yaml)
                            new_deployment_file.write(service_yaml)
                        else:
                            new_deployment_file.write(yaml.dump(doc))
        except:
            raise AssertionError(f'There exists no {deployment_file_path}')

        subprocess.run(['kubectl', 'apply', '-f', 'tmp_deployment.yml', '-n', f'{self.credentials.namespace}']).check_returncode()


    def delete(self):
        logger.info('Skip cleaning up workspace...')
        os.remove(os.path.join(current_dir, '..', 'tmp_deployment.yml'))

    def __str__(self):
        return 'Name: {}, App-Router-Name: {}'.format(
            self.name, self.app_router_name)
