import subprocess
import argparse
import sys
import asyncio
import json

from util.env import Environment as Env
from util.cf_client import CFClient

# Dependencies
# The script depends on python3 and the cloud foundry command line tool 'cf' version v8.
# To install its python dependencies cd into this folder and run
#     pip3 install -r requirements.txt
#
# Input: loads from .env
#
# Running the script
#    python3 ./cf_deploy.py --type java --path java-security-ams --name java-ams --multi-tenant
# OR
#   make deploy-[all|java|spring|node]
#   for multi-tenant setup MT argument needs to be defined with values (true,1,True) e.g make deploy-java MT=1

parser = argparse.ArgumentParser()
parser.add_argument(
    '-p', '--path', choices=('spring-security-ams', 'java-security-ams', 'nodejs-ams'),
    help='path to the manifest file', required=False, dest='path')
parser.add_argument(
    '-c', '--cleanup', choices=('True','False'), default='True',
    help='should cf space be cleaned before deploying', dest='cleanup')
parser.add_argument(
    '-t', '--type', choices=('java','node'),
    help='type of the application', dest='type')
parser.add_argument(
    '--multi-tenant', action='store_true',
    help='creates sms service for multi tenant apps', dest='multi_tenant')
parser.add_argument(
    '--auditlog', choices=('True','False'), default='False',
    help='creates auditlog service', dest='auditlog')
parser.add_argument('-n', '--name', help="the name of the app", required=True, dest='name')
parser.add_argument('--package-path', help='path to the package.json', dest='package_path')
args = parser.parse_args()


async def deploy():
    await asyncio.gather(
        cf_client.deploy_app_name(manifest_dir=args.path, app_name=args.name, is_mt=args.multi_tenant),
        cf_client.deploy_app_name(manifest_dir=args.path, app_name=f'{args.name}-approuter', is_mt=args.multi_tenant)
    )

async def setup():
    setup_tasks = []
    if args.type == 'java':
        setup_tasks.append(__async_subprocess(
            ['mvn', '-q', 'clean', 'package', '-DskipTests', '--batch-mode'], w_dir=args.path
        ))
    elif args.type == 'node':
        setup_tasks.append(__async_subprocess(
            ['npm', 'install', f'--registry={Env.npm_registry()}'], w_dir=args.package_path
        ))
    setup_tasks.append(__create_ias_service(args, cf_client))
    if args.auditlog == 'True':
        setup_tasks.append(
            cf_client.create_service_instance('auditlog', 'oauth2', f'{args.name}-auditlog')
        )
    await asyncio.gather(*setup_tasks)
    if args.multi_tenant:  # sms service has a dependency on ias service
        await __create_sms_service(args, cf_client)

async def __create_ias_service(args, cf_client):
    with open(f'{args.path}/ias-config.json', encoding="utf-8") as configFile:
        service_config = configFile.read()
        service_config = service_config.replace('((ID))', cf_client.space_name)
        service_config = service_config.replace('((LANDSCAPE_APPS_DOMAIN))', cf_client.landscape_domain)
        service_config = service_config.replace('((MT))', str(args.multi_tenant))
        await cf_client.create_service_instance('identity', 'application', f'{args.name}-ias', json.loads(service_config))

async def __create_sms_service(args, cf_client):
    with open(f'{args.path}/sms-config.json', encoding="utf-8") as configFile:
        service_config = configFile.read()
        service_config = service_config.replace('((ID))', cf_client.space_name)
        service_config = service_config.replace('((LANDSCAPE_APPS_DOMAIN))', cf_client.landscape_domain)
        await cf_client.create_service_instance('subscription-manager', 'provider', f'{args.name}-sms', json.loads(service_config))

async def __async_subprocess(command, w_dir=None):
    proc = await asyncio.create_subprocess_exec(
        *command,
        cwd=w_dir,
        stderr=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.PIPE
    )
    stdout, stderr = await proc.communicate()
    if proc.returncode != 0:
        print(f'${" ".join(command)} FAIL:\n{stdout.decode()} {stderr.decode()}')
        e = subprocess.CalledProcessError(proc.returncode, command)
        e.stdout, e.stderr = stdout.decode(), stderr.decode()
        raise e

async def main():
    if args.cleanup == 'True': 
        await cf_client.delete_app_subscriptions()
        await cf_client.delete_app_bindings()
        await cf_client.delete_apps()
        await cf_client.delete_services()
        await cf_client.delete_orphaned_routes()
    if args.name != '':
        if args.path is None:
            raise Exception("the --path argument must be set!")
        await setup()
        await deploy()

if CFClient.is_logged_off():
    sys.exit('To run this script you must be logged into CF via "cf login"')
cf_client = CFClient()
asyncio.run(main())
