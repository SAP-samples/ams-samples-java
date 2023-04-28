#!/usr/bin/env python3
import abc
import subprocess
import unittest
import logging
import os

import yaml

from getpass import getpass
from retry import retry
from k8s.k8s_app import K8sApp
from util.common_requests import HttpUtil


# Usage information
# Prerequisites:
#   - kubectl with oidc-login and dcl plugin, as described here: https://kyma-project.io/docs/kyma/latest/03-tutorials/03-set-up-idp#configure-kubectl-access
#   - kubectl dcl installation as described here: https://github.wdf.sap.corp/CPSecurity/kubectl-dcl#installation
#   - docker daemon must be running
#   - a kyma NAMESPACE for deployments
#
# In case arguments are missing you will prompted to enter these values at the runtime.
#
# Environment Variables:
# CFAMSUSER and CFAMSPASSWORD
#    For some samples it needs to create a password token for which you need to provide
#    a valid ias user and password. Either supply them via the system environment variables
#    or by typing when the script prompts for the user name and password.
#    https://password.wdf.sap.corp/passvault/#/pw/0000210950
# CFAMSUSERUUID
#    For some samples it needs to assign a policy to the ias user.
#    For that the ids from ias user management are required.
#    Either supply them via the system environment variables
#    or by typing when the script prompts for the user id and guid.
# COMMONARTIFACTORYUSER and COMMONARTIFACTORYAPITOKEN
#    To deploy the images to Common Artifactory you need a valid
#    user and password. Either supply them via the system environment variables.
#
# K8SNAMESPACE the prepared kyma NAMESPACE for deployments - prepared with service account
# KUBECONFIG needs to point to a valid ./kubeconfig.yaml (generated for example by /scripts/kyma_create_service_account.py)
#
# Dependencies
# The script depends on python3 and the kubernetes command line tool 'kubectl' and requires in addition 'jq' cli.
# To install its python dependencies cd into this folder and run
#
# pip3 install -r requirements.txt
#
# Running the script
#
# python3 ./deploy_and_test_k8s.py -v
#
# By default it will run all unit tests.
# It is also possible to run specific test classes:
# python3 -m unittest deploy_and_test_k8s.TestAmsSpring.test_authenticated

logger = logging.getLogger("deploy_and_test_k8s")
logger.setLevel(logging.DEBUG)

script_dir = os.path.dirname(os.path.realpath(__file__))
ias_token_endpoint = '/oauth2/token'

varsFile = open(f"{script_dir}/../vars.yml")
varsFile.close()

RUN_TESTS = "------------------- Running '{}' tests -------------------"
CF_APP_LOGS = "------------------- Application logs for '{}' -------------------"
EXPECT_200 = "Expected HTTP status 200"
EXPECT_401 = "Expected HTTP status 401"
EXPECT_403 = "Expected HTTP status 403"
EXPECT_500 = "Expected HTTP status 500"

def setup_module():
    logger.info("Setting up...")

class Credentials:
    def __init__(self):
        self.username = self.__get_env_variable('CFAMSUSER', lambda: input("Username: "))
        self.useruuid = self.__get_env_variable('CFAMSUSERUUID', lambda: input("User UUID: "))
        self.password = self.__get_env_variable('CFAMSPASSWORD', lambda: input("User API Token (https://password.wdf.sap.corp/passvault/#/pw/0000210950): "))
        self.namespace = self.__get_env_variable('K8SNAMESPACE', lambda: input("Kyma Namespace: "))
        self.common_artifactory_user = self.__get_env_variable('COMMONARTIFACTORYUSER', lambda: 'Common artifactory user: ')
        self.common_artifactory_api_token = self.__get_env_variable('COMMONARTIFACTORYAPITOKEN', lambda: input("Common artifactory user API Token (https://password.wdf.sap.corp/passvault/#/pw/0000258320): "))

    def __get_env_variable(self, env_variable_name, prompt_function):
        value = os.getenv(env_variable_name)
        if (value is None):
            value = prompt_function()
        return value

# Script Initialization
credentials = Credentials()
setup_module()

# Abstract base class for sample app tests classes
class SampleTest(abc.ABC, unittest.TestCase):
    k8s_app: K8sApp

    @classmethod
    def setUpClass(cls):
        cls.k8s_app.prepare_sap_image_registry()
        cls.k8s_app.prepare_images()
        cls.k8s_app.deploy()
        cls.perform_health_check()

    def setUp(self):
        logger.info(RUN_TESTS.format(unittest.TestCase.id(self)))

    @classmethod
    def tearDownClass(cls):
        cls.k8s_app.delete()

    @retry(AssertionError, tries=7, delay=30) # wait some seconds until adc has fetched updated ams bundle
    def perform_delayed_get_request_with_token(self, path):
        resp = self.perform_get_request_with_token(path)
        logger.info('GET request to {} returned with status {} (retries up to 7 times to check whether permission is '
                    'available)'.format(path, resp.status_code))
        assert resp.status_code == 200
        return resp

    def perform_get_request_with_token(self, path):
        return self.perform_get_request(path=path, id_token=self.get_token())

    def get_token(self):
        if self.k8s_app.deployed_app.certificate is not None:
            return HttpUtil().get_token_via_mtls(
                token_url=self.k8s_app.deployed_app.ias_url + ias_token_endpoint,
                clientid = self.k8s_app.deployed_app.clientid,
                certificate= self.k8s_app.deployed_app.certificate,
                certificate_key= self.k8s_app.deployed_app.key,
                username = credentials.username,
                password = credentials.password)
        return HttpUtil().get_token(
            token_url =self.k8s_app.deployed_app.ias_url + ias_token_endpoint,
            clientid = self.k8s_app.deployed_app.clientid,
            clientsecret = self.k8s_app.deployed_app.clientsecret,
            username = credentials.username,
            password = credentials.password).get('id_token')

    @classmethod
    def perform_get_request(cls, path, id_token=None):
        cmd = ['kubectl', 'config', 'view', '-ojsonpath=\'{.clusters[0].cluster.server}\'']
        url = subprocess.run(cmd, capture_output=True).stdout.decode().strip("'").replace("api", "ams-sample-app-api")
        return HttpUtil().get_request(f'{url}{path}', id_token=id_token)

    @classmethod
    @retry(AssertionError, tries=7, delay=20) # wait some seconds until adc has fetched updated ams bundle
    def perform_health_check(cls):
        response = cls.perform_get_request('/health')
        assert response.status_code == 200

    def print_recent_logs(self):
        logger.info(CF_APP_LOGS.format(unittest.TestCase.id(self)))

class TestAmsSpring(SampleTest):

    k8s_app = K8sApp(name='spring-security-ams', credentials=credentials, app_router_name='ams-approuter')

    def test_is_healthy(self):
        resp = self.perform_get_request('/health')
        self.assertEqual(resp.status_code, 200, EXPECT_200)

    def test_not_authenticated(self):
        resp = self.perform_get_request('/authenticate')
        self.assertEqual(resp.status_code, 401, EXPECT_401)

    def test_authenticated(self):
        resp = self.perform_get_request_with_token('/authenticate')
        self.assertEqual(resp.status_code, 200, EXPECT_200)
        self.assertRegex(resp.text, 'You are an authenticated user.', 'Expected to find success message in response')

    def test_not_authorized(self):
        resp = self.perform_get_request_with_token('/read')
        self.assertEqual(resp.status_code, 403, EXPECT_403)

    def test_authorized(self):
        # Works only for SCP BOT-User (dl_5eb27aaf4de077027e59aa60@global.corp.sap)
        #       as this one has default policy assigned (see base dcls)
        resp = self.perform_delayed_get_request_with_token('/salesOrders/readByCountry/DE')
        self.assertEqual(resp.status_code, 200, EXPECT_200)


if __name__ == '__main__':
    import doctest
    doctest.testmod()
    unittest.main()