import subprocess
import asyncio
import re
import tempfile
import time
import os
from pathlib import Path
import json
from cloudfoundry_client.client import CloudFoundryClient
from cloudfoundry_client.common_objects import JsonObject
from util.subscription_client import SMSClient
from util.env import Environment

class UpdateFailedError(Exception):
    pass
class InProgressException(Exception):
    pass
class CreationFailedError(Exception):
    pass

class CFClient:
    @classmethod
    def is_logged_off(cls):
        target = subprocess.run(['cf', 'oauth-token'], capture_output=True)
        return not target or target.stdout.decode().startswith('FAILED')

    def __init__(self):
        token = subprocess.run(['cf', 'oauth-token'], capture_output=True, check=True)
        target = subprocess.run(['cf', 'target'], capture_output=True, check=True)
        self.bearer_token = token.stdout.strip().decode()
        self.api_endpoint, self.user_id, self.org_name, self.space_name = self.__parse_target_output(
            target.stdout.decode()
        )
        self.landscape_domain = self.api_endpoint.replace('https://api.cf', 'cfapps')
        space = subprocess.run(['cf', 'space', self.space_name, '--guid'], capture_output=True, check=True)
        self.space_guid = space.stdout.decode().strip()
        self.client = self.__init_client_py()

    def __init_client_py(self):
        client = CloudFoundryClient(self.api_endpoint, verify=True)
        cf_home = os.getenv('CF_HOME', Path.home())
        with open(f'{cf_home}/.cf/config.json', encoding='utf-8') as f:
            config = json.load(f)
        client.init_with_token(config['RefreshToken'])
        return client

    def __parse_target_output(self, target_output):
        api_endpoint_match = re.search(
            r'api endpoint:(.*)', target_output, re.IGNORECASE
        )
        user_id_match = re.search(r'user:(.*)', target_output)
        space_match = re.search(r'space:(.*)', target_output)
        org_match = re.search(r'org:(.*)', target_output)
        api_endpoint = api_endpoint_match.group(1).strip()
        user_id = user_id_match.group(1).strip()
        space_name = space_match.group(1).strip()
        org_name = org_match.group(1).strip()
        return [api_endpoint, user_id, org_name, space_name]

    async def __async_operation(self, app_name, command, operation, w_dir=None):
        start_time = time.time()
        print(f'{operation} {app_name} triggered')
        proc = await asyncio.create_subprocess_exec(
            *command,
            cwd=w_dir,
            stderr=asyncio.subprocess.PIPE,
            stdout=asyncio.subprocess.PIPE
        )
        stdout, stderr = await proc.communicate()
        if proc.returncode == 0:
            print(f'{operation} {app_name} SUCCESS after {"{:.2f}".format(time.time() - start_time)}s')
        else:
            print(f'${app_name}: {operation} FAIL:\n{stdout.decode()} {stderr.decode()}')
            e = subprocess.CalledProcessError(proc.returncode, command)
            e.stdout, e.stderr = stdout.decode(), stderr.decode()
            raise e


    async def delete_app_subscriptions(self):
        subscriptions_cleanup = []
        for app in self.client.v3.apps.list(space_guids=f'{self.space_guid}'):
            print(f'Checking app {app["name"]}')
            vcap_services = self.get_vcap_services(app['name'])
            if vcap_services is not None:
                # check if app has a bound sms service - deletes subscriptions
                vcap_sms = vcap_services.get('subscription-manager')
                if vcap_sms is not None:
                    subscriptions_cleanup.append(SMSClient.delete_subscriptions(vcap_sms[0]['credentials']))
                else:
                    print(f'App {app["name"]} is not bound to SMS service')
            else:
                print(f'No VCAP_SERVICES found for app {app["name"]}')
        await asyncio.gather(*subscriptions_cleanup)

    async def delete_app_bindings(self):
        bindings_cleanup = []
        for app in self.client.v3.apps.list(space_guids=f'{self.space_guid}'):
            print(f'Checking app {app["name"]}')
            vcap_services = self.get_vcap_services(app['name'])
            if vcap_services is not None:
                vcap_ias = vcap_services.get('identity')
                if vcap_ias is not None:
                    bindings_cleanup.append(self.unbind_service(app, vcap_ias[0]['instance_name']))
                else:
                    print(f'App {app["name"]} has no identity service bindings')
            else:
                print(f'No VCAP_SERVICES found for app {app["name"]}')
        await asyncio.gather(*bindings_cleanup)

    async def delete_apps(self):
        apps_to_delete = []
        for app in self.client.v3.apps.list(space_guids=f'{self.space_guid}'):
            apps_to_delete.append(self.delete_app(app))
        await asyncio.gather(*apps_to_delete)

    async def delete_services(self):
        services_to_delete = []
        for service in self.client.v3.service_instances.list(space_guids=f'{self.space_guid}'):
            services_to_delete.append(self.delete_service(service))
        await asyncio.gather(*services_to_delete)

    async def delete_orphaned_routes(self):
        await self.__async_operation(
            app_name='delete-orphaned-routes',
            command=[ 'cf8', 'delete-orphaned-routes', '-f' ],
            operation='delete-service'
        )

    def __get_app_env(self, name):
        app_json = self.client.v3.apps.get_first(
            names=name, space_guids=self.space_guid
        )
        if app_json is not None:
            return self.client.v3.apps.get_env(app_json['guid'])
     
    def get_vcap_services(self, name) -> JsonObject:
        app_env = self.__get_app_env(name)
        if app_env is not None:
            return app_env['system_env_json']['VCAP_SERVICES']

    def get_vcap_application(self, name) -> JsonObject:
        app_env = self.__get_app_env(name)
        if app_env is not None:
            return app_env['application_env_json']['VCAP_APPLICATION']

    async def unbind_service(self, app_name, service_name):
        await self.__async_operation(
            app_name=f'{service_name} from {app_name}',
            command=['cf8', 'unbind-service', app_name, service_name, '--wait'],
            operation='unbind-service'
        )

    async def deploy_app_name(self, manifest_dir, app_name, is_mt):
        command = ['cf8', 'push', app_name,
                   '--var', f'ID={self.space_name}',
                   '--var', f'LANDSCAPE_APPS_DOMAIN={self.landscape_domain}']

        if is_mt:
            env = Environment()
            command = command + [
                '--var', f'PROVIDER_TENANT_NAME={env.provider_tenant_name()}',
                '--var', f'PROVIDER_TENANT_ID={env.provider_tenant_id()}',
                '--var', f'SUBSCRIBER_TENANT_NAME={env.subscriber_tenant_name()}',
                '--var', f'SUBSCRIBER_TENANT_ID={env.subscriber_tenant_id()}',
            ]
        elif "java-ams" in app_name:
            # for non MT java-ams deployment - creates a new tmp manifest file from the existing manifest.yml
            # with removed MT references
            tmp_fd, tmp_path = tempfile.mkstemp(suffix=".yml", prefix="manifest-non-mt-", dir='java-security-ams', text=True)
            with open("java-security-ams/manifest.yml", "r") as f:
                lines = f.readlines()
                with os.fdopen(tmp_fd, 'w') as fd:
                    for line in lines:
                        if line.strip("\n").strip() == "- java-ams-sms" \
                                or line.strip("\n").strip().startswith("- route: ((SUBSCRIBER_TENANT_ID))"):
                            continue
                        if line.strip("\n").strip().startswith("- route: ((PROVIDER_TENANT_ID))"):
                            line = line.replace('((PROVIDER_TENANT_ID))--((PROVIDER_TENANT_NAME))-ar', 'java-ams-approuter')
                        fd.write(line)
            command = command + ['-f', tmp_path]
        try:
            await self.__async_operation(
                app_name=app_name,
                command=command,
                operation='push',
                w_dir=manifest_dir
            )
        finally:
            if 'tmp_path' in locals():
                os.unlink(tmp_path)

    async def delete_app(self, app):
        await self.__exec_async(
            f'delete-app {app["name"]}',
            self.client.v3.apps.remove, app['guid'], False)

    async def delete_service(self, service):
        await self.__exec_async(
            f'delete-service {service["name"]}',
            self.client.v3.service_instances.remove, service['guid'], False)

    async def unbind_service(self, app, service_name):
        service = self.client.v2.service_instances.get_first(name=service_name, space_guid=self.space_guid)
        binding = self.client.v2.service_bindings.get_first(app_guid=app['guid'], service_instance_guid=service['metadata']['guid'])
        if binding is not None:
            await self.__exec_async(
                f'unbinding {service_name} from {app["name"]}',
                self.client.v2.service_bindings.remove,
                binding['metadata']['guid'])

    async def create_service_instance(self, service_name:str, service_plan_name:str, instance_name: str, config:dict={}):
        service = self.client.v2.services.get_first(label=service_name)
        service_plan = self.client.v3.service_plans.get_first(service_broker_guids=service['entity']['service_broker_guid'], names=service_plan_name, space_guids=self.space_guid)
        await self.__exec_async(
            f'create-service {instance_name}',
            self.client.v3.service_instances.create,
            name=instance_name,
            space_guid=self.space_guid,
            service_plan_guid=service_plan['guid'],
            parameters=config)
        await self.__poll_service(instance_name, 'succeeded')

    def __get_app_env(self, name):
        app_json = self.client.v3.apps.get_first(
            names=name, space_guids=self.space_guid
        )
        if app_json is not None:
            return self.client.v3.apps.get_env(app_json['guid'])

    async def __poll_service(self, instance_name, state, timeout=180):
        print(f'polling service "{instance_name}" for last_operation {state}')
        start_time = time.time()
        service = self.client.v2.service_instances.get_first(name=instance_name, space_guid=self.space_guid)
        while(service['entity']['last_operation']['state'] != state):
            if (time.time() - start_time) > timeout:
                print(f"service last operation: {service['entity']['last_operation']}")
                raise Exception('timeout reached while polling for ' + instance_name +
                    ' to turn last operation ' + state + ' after ' + str(time.time() - start_time))
            service = self.client.v2.service_instances.get(service['metadata']['guid'])
            await asyncio.sleep(1)
        print(f'polling {instance_name} SUCCESS after {"{:.2f}".format(time.time() - start_time)}s')

    async def __exec_async(self, operation:str, sync_func, /, *args, **kwargs):
        start_time = time.time()
        print(f'{operation} triggered')
        res = await asyncio.gather(
            asyncio.to_thread(sync_func, *args, **kwargs)
        )
        print(f'{operation} SUCCESS after {"{:.2f}".format(time.time() - start_time)}s')
        return res