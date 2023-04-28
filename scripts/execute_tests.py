#!/usr/bin/env python3
from unittest import TestCase, skipIf, main as unittest_main, skip
from subprocess import Popen, PIPE, check_call
from contextlib import suppress
import json
import os
import sys
import uuid
import jwt
from time import time
import re

from util.env import Environment as Env
from util.subscription_client import SMSClient
from util.cf_client import CFClient
from util.common_requests import HttpUtil as http
from util.ias_client import IASClient


# Usage information
# To run this script you must be logged into CF via 'cf login' Also make sure
# to change settings in vars.yml to your needs. This script deploys sample
# apps and fires post request against their endpoints.
#
# Requirements
# CFAMSUSER and CFAMSPASSWORD
#    For some samples it needs to create a password token for which you need to provide
#    a valid ias user and password. Either supply them via the system environment variables
#    or by typing when the script prompts for the user name and password.
#    https://password.wdf.sap.corp/passvault/#/pw/0000210950
#
# Dependencies
# The script depends on python3 and the cloud foundry command line tool 'cf'.
# To install its python dependencies cd into this folder and run
#     pip3 install -r requirements.txt
#
# Running the script
#     python3 ./execute_tests.py -v
#
# By default it will run all unit tests.
# It is also possible to run specific test classes:
#     python3 -m unittest execute_tests.TestAmsSpring.test_authenticated
# OR
#     make test-[all|java|spring|node]

if CFClient.is_logged_off():
    sys.exit('To run this script you must be logged into CF via "cf login"')
cf_client = CFClient() # checks for login

class App:
    def __init__(self, name, dir):
        self.sms_credentials = None
        self.name = name
        self.dir = dir
        self.url = "{}-{}.{}".format(name, cf_client.space_name, cf_client.landscape_domain)
        self.cert_url = "{}-{}.cert.{}".format(name, cf_client.space_name, cf_client.landscape_domain)
        vcap_services = cf_client.get_vcap_services(self.name)
        if vcap_services is not None and vcap_services.get('identity') is not None:
            self.ias_credentials = vcap_services.get('identity')[0]['credentials']
            self.ias_instance_guid = vcap_services.get('identity')[0]['instance_guid']
            if vcap_services.get('subscription-manager') is not None:
                self.sms_credentials = vcap_services.get('subscription-manager')[0]['credentials']
        else:
            raise(f"ERROR: No identity service found for '{self.name}'")

    def ias(self, property_name):
        if self.ias_credentials is None:
            raise Exception(f'ias credentials are None for "{self.name}"')
        return self.ias_credentials.get(property_name)

    @property
    def working_dir(self):
        return '../' + self.dir

    def __str__(self):
        return 'Name: {}, IAS-Service-Name: {}, ' \
            .format(self.name, self.ias['name'])

# Abstract base class for sample app tests classes
class Common(object):
    def perform_delayed_get_request_with_token(cls, path):
        url = 'https://{}{}'.format(cls.app.url, path)
        resp = http.get_request_retry(url, expected_status_code=200, id_token=cls.ias.token)
        return resp

    def perform_get_request_with_token(cls, path):
        return cls.perform_get_request(path=path, id_token=cls.ias.token)

    def perform_get_request(cls, path, id_token=None):
        url = 'https://{}{}'.format(cls.app.url, path)
        return http.get_request(url, id_token=id_token)

    def perform_get_request_via_mtls(cls, path, id_token=None):
        url = 'https://{}{}'.format(cls.app.cert_url, path)
        return http.get_request_via_mtls(url, cls.ias.app_cert, cls.ias.app_key, id_token)

    def perform_admin_policy_check(cls, policy_dcl, path):
        """
        Arguments:
            policy: the dcl policy for example: 'POLICY salesOrderDelete { GRANT delete ON salesOrder; }'
            path: the path where the policy can be verified for example: '/salesOrder/delete'
        """
        # test policy before creation and assigning
        cls.ias.check_policy_is_not_assigned('https://{}{}'.format(cls.app.url, path))

        # this step can be omitted in future when ias and ams are in the same data center
        cls.ias.add_assertion_attribute()

        policy_resp, _ = cls.ias.create_policy(policy_dcl)
        policy_group_id = policy_resp.get("group_id")
        policy_id = policy_resp.get("id")
        decoded_ias_token = jwt.decode(cls.ias.token, options={"verify_signature": False})
        user_uuid = decoded_ias_token.get("user_uuid")
        cls.ias.assign_user_to_policy(user_uuid, policy_group_id)

        resp = cls.perform_delayed_get_request_with_token(path)
        http.expect_status_code(resp.status_code, 200, http.EXPECT_200)

        cls.ias.delete_policy(policy_id)



class TestAmsSpring(TestCase, Common):
    @classmethod
    def setUpClass(cls):
        cls.app = App('spring-ams', 'spring-security-ams')
        cls.ias = IASClient(cls.app.ias, cls.app.ias_instance_guid)

    def test_is_healthy(self):
        resp = self.perform_get_request('/health')
        self.assertEqual(resp.status_code, 200, http.EXPECT_200)

    def test_not_authenticated(self):
        resp = self.perform_get_request('/authenticate')
        self.assertEqual(resp.status_code, 401, http.EXPECT_401)

    def test_authenticated(self):
        resp = self.perform_get_request_with_token('/authenticate')
        self.assertEqual(resp.status_code, 200, http.EXPECT_200)
        self.assertRegex(resp.text, 'You are an authenticated user.', 'Expected to find success message in response')

    def test_not_authorized(self):
        resp = self.perform_get_request_with_token('/read')
        self.assertEqual(resp.status_code, 403, http.EXPECT_403)

    def test_authorized(self):
        # Works only for SCP BOT-User (dl_5eb27aaf4de077027e59aa60@global.corp.sap)
        #       as this one has default policy assigned (see base dcls)
        resp = self.perform_delayed_get_request_with_token('/salesOrders/readByCountry/DE')
        self.assertEqual(resp.status_code, 200, http.EXPECT_200)

    def test_technical_communication(self):
        resp = self.perform_get_request_via_mtls('/technical-communication', self.ias.client_token)
        self.assertEqual(resp.status_code, 200, http.EXPECT_200)

        resp = self.perform_get_request_via_mtls('/technical-communication')
        self.assertEqual(resp.status_code, 401, http.EXPECT_401)

        resp = self.perform_get_request('/technical-communication', self.ias.client_token)
        self.assertEqual(resp.status_code, 401, http.EXPECT_401)

        resp = self.perform_get_request('/technical-communication')
        self.assertEqual(resp.status_code, 401, http.EXPECT_401)

        resp = self.perform_get_request_with_token('/technical-communication')
        self.assertEqual(resp.status_code, 403, http.EXPECT_403)

    def test_local_setup(self):
        with suppress(KeyError):
            del os.environ['KUBERNETES_SERVICE_HOST']
        os.environ['VCAP_APPLICATION'] = '{}'
        os.environ['VCAP_SERVICES'] = json.dumps({ 'identity': [ { 'credentials': {
            'clientid': self.app.ias('clientid'),
            'clientsecret': self.app.ias('clientsecret'),
            'domains': self.app.ias('domains')},
            'name': self.app.ias('name') }]})
        try:
            process = Popen(['mvn', '-q', 'spring-boot:run', '-Dspring-boot.run.arguments=--server.port=0',
                             '-Dspring-boot.run.useTestClasspath', '-Dspring.profiles.active=local'], stdout=PIPE, cwd=self.app.working_dir)
            now = time()
            timeout = 60
            port = 0
            while process.poll() is None and now > (time() - timeout):
                line = str(process.stdout.readline())
                if 'Tomcat started on port(s): ' in line:
                    port = re.findall(r'(\d+)\D*$', line)[0]
                    break
            self.assertNotEqual(port, 0, "port shouldn't be 0")
            resp = http.get_request(f'http://0.0.0.0:{port}/health')
            self.assertEqual(resp.status_code, 200, http.EXPECT_200)
        finally:
            process.terminate()
            process.wait()

        check_call(['mvn', '-q', 'dcl-compiler:principalToPolicies', "-DzoneId={}".format(uuid.uuid4()),
                        '-DprincipalId={}'.format(uuid.uuid4()),
                        '-Dpolicies=ams.readAll'],
                        cwd=self.app.working_dir)


class TestAmsJava(TestCase, Common):
    @classmethod
    def setUpClass(cls):
        cls.app = App('java-ams', 'java-security-ams')
        cls.ias = IASClient(cls.app.ias, cls.app.ias_instance_guid)

    def test_is_healthy(self):
        resp = self.perform_get_request('/health')
        self.assertEqual(200, resp.status_code, http.EXPECT_200 + " from " + self.app.url)

    def test_not_authenticated(self):
        resp = self.perform_get_request('/app/hello-java-security')
        self.assertEqual(401, resp.status_code, http.EXPECT_401)

    def test_authenticated(self):
        resp = self.perform_get_request_with_token('/app/hello-java-security')
        self.assertEqual(403, resp.status_code, http.EXPECT_403)

    def test_authorized(self):
        # Works only for SCP BOT-User (dl_5eb27aaf4de077027e59aa60@global.corp.sap)
        #       as this one has default policy assigned (see base dcls)
        resp = self.perform_delayed_get_request_with_token('/app/java-security')
        self.assertEqual(200, resp.status_code, http.EXPECT_200)

    def test_value_help(self):
        resp = self.perform_get_request_via_mtls('/app/callback/value-help/$metadata', self.ias.client_token)
        self.assertEqual(200, resp.status_code, http.EXPECT_200)

        resp = self.perform_get_request_via_mtls('/app/callback/value-help/Country?$skip=0&top=100$filter=contains(ID,%27i%27)', self.ias.client_token)
        self.assertEqual(200, resp.status_code, http.EXPECT_200)
    
    @skipIf('cfapps.sap.hana.ondemand.com' == cf_client.landscape_domain, "MT can't be tested in dev landscape")
    @skipIf(Env.subscriber_tenant_id() is None, "If no subscriber tenant is provided MT is not setup")
    def test_subscription(self):
        token = SMSClient.get_cis_token(cf_client.org_name)
        subscription_resp = SMSClient.subscribe(self.app.sms_credentials.get('subscription_manager_url'),
                                                self.app.sms_credentials.get('app_name'), token)
        # asserts that creation of async subscription job was successful
        self.assertEqual(202, subscription_resp, "Expected the response from POST "
                                                 "'/saas-manager/v1/applications/subscriptions' to be 202")

        # asserts that subscription state is SUBSCRIBED
        subscription_url = SMSClient.get_subscription_url(self.app.sms_credentials.get('subscription_manager_url'),
                                                          self.app.sms_credentials.get('app_name'), token)
        self.assertIsNotNone(subscription_url, "subscriptionUrl shouldn't be None")
        # asserts that the subscriber route in approuter is functional
        # TODO doesn't work for now returns 500 back. Approuter behaves different, clarify with approuter what's going on
        # resp = http.get_request(subscription_url)
        # self.assertEqual(200, resp.status_code)




class TestAmsNode(TestCase, Common):
    @classmethod
    def setUpClass(cls):
        cls.app = App('node-ams', 'node-ams')
        cls.ias = IASClient(cls.app.ias, cls.app.ias_instance_guid)

    def test_is_healthy(self):
        resp = self.perform_get_request('/health')
        self.assertEqual(resp.status_code, 200, http.EXPECT_200)

    def test_not_authenticated(self):
        resp = self.perform_get_request('/authenticate')
        self.assertEqual(resp.status_code, 401, http.EXPECT_401)

    def test_authenticated(self):
        resp = self.perform_get_request_with_token('/authenticate')
        self.assertEqual(resp.status_code, 200, http.EXPECT_200)
    
    # TODO wait for implementation in security client lib
    # def test_technical_communication(self):
    #     resp = self.perform_get_request_via_mtls('/technical-communication', self.get_client_token())
    #     self.assertEqual(resp.status_code, 200, http.EXPECT_200)

    #     resp = self.perform_get_request_via_mtls('/technical-communication')
    #     self.assertEqual(resp.status_code, 401, http.EXPECT_401)

    #     resp = self.perform_get_request('/technical-communication', self.get_client_token())
    #     self.assertEqual(resp.status_code, 401, http.EXPECT_401)

    #     resp = self.perform_get_request('/technical-communication')
    #     self.assertEqual(resp.status_code, 401, http.EXPECT_401)

    #     resp = self.perform_get_request_with_token('/technical-communication')
    #     self.assertEqual(resp.status_code, 403, http.EXPECT_403)
    
    def test_authorized(self):
        # Works only for SCP BOT-User (dl_5eb27aaf4de077027e59aa60@global.corp.sap)
        #       as this one has default policy assigned (see base dcls)
        resp = self.perform_delayed_get_request_with_token('/read')
        self.assertEqual(resp.status_code, 200, http.EXPECT_200)

    def testAdminPolicy(self):
        policy = 'POLICY salesOrderDelete { GRANT delete ON salesOrder; }'
        self.perform_admin_policy_check(policy, '/salesOrder/delete')


if __name__ == '__main__':
    unittest_main()