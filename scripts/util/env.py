import json
import os
from dotenv import load_dotenv

ENV_FILE = os.path.join((os.path.dirname(os.path.abspath(__file__))), '../../', '.env')
BOT_CREDENTIALS_URL = 'https://password.wdf.sap.corp/passvault/#/pw/0000258320'
BOT_ADMIN_CREDENTIAL_URL = 'https://password.wdf.sap.corp/passvault/#/pwd/0000320022'
CF_USER = 'https://password.wdf.sap.corp/passvault/#/pwd/0000198416'
CIS_CREDS = 'https://password.wdf.sap.corp/passvault/#/pwd/0000342998'
print(f"loading environment variables from {ENV_FILE}")
load_dotenv(ENV_FILE)


class Environment:

    @classmethod
    def npm_registry(cls):
        return cls.__get_env_variable(name = 'NPM_REGISTRY', default_value = 'https://int.repositories.cloud.sap/artifactory/api/npm/build-releases-npm/')
    
    @classmethod
    def username(cls):
        return cls.__must_get_env_variable('CFAMSUSER', BOT_CREDENTIALS_URL)
    
    @classmethod
    def password(cls):
        return cls.__must_get_env_variable('CFAMSPASSWORD', BOT_CREDENTIALS_URL)

    @classmethod
    def username_def_idp(cls):
        return cls.__must_get_env_variable('CF_USERNAME', CF_USER)

    @classmethod
    def password_def_idp(cls):
        return cls.__must_get_env_variable('CF_PASSWORD', CF_USER)

    @classmethod
    def ias_client_cert(cls):
        return cls.__must_get_env_variable('IAS_ADMIN_CLIENT_CERT', BOT_ADMIN_CREDENTIAL_URL)
    
    @classmethod
    def ias_client_key(cls):
        return cls.__must_get_env_variable('IAS_ADMIN_CLIENT_KEY', BOT_ADMIN_CREDENTIAL_URL)
    
    @classmethod
    def provider_tenant_name(cls):
        return cls.__get_env_variable('PROVIDER_TENANT_NAME')
    
    @classmethod
    def provider_tenant_id(cls):
        return cls.__get_env_variable('PROVIDER_TENANT_ID')

    @classmethod
    def subscriber_tenant_name(cls):
        return cls.__get_env_variable('SUBSCRIBER_TENANT_NAME')

    @classmethod
    def subscriber_tenant_id(cls):
        return cls.__get_env_variable('SUBSCRIBER_TENANT_ID')
    
    @classmethod
    def cis_credentials(cls):
        """
        Credentials to get the token for subscriber's subaccount, required for MT subscription flow testing purposes
        :rtype: Python object
        :return: json with credentials for each landscape {"org_name": {"clientid": xxx, "clientsecret": xxx, "url": xxx}}
        """
        return json.loads(cls.__must_get_env_variable('CIS_CREDENTIALS', CIS_CREDS))

    @classmethod
    def __get_env_variable(cls, name, default_value=None):
        value = os.getenv(name)
        if (value is None):
            value = os.getenv(name.casefold(), default_value)
        return value
    
    @classmethod
    def __must_get_env_variable(cls, name, msg):
        value = os.getenv(name)
        if value is None:
            raise Exception(f'ENV "{name}" not found, please set via .env file on root level, see also: {msg}')
        return value
