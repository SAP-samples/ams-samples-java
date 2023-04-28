from util.common_requests import HttpUtil as http, RequestFailedError
from util.env import Environment as Env
from retry import retry

class IASClient:
    TOKEN_ENDPOINT = '/oauth2/token'
    AMS_API_ENDPOINT = '/authorization/sap/ams/v1/ams-instances'

    def __init__(self, ias_credentials, ias_guid):
        self.guid = ias_guid
        self.url = ias_credentials('url')
        self.ams_instance_id = ias_credentials('authorization_instance_id')
        self.client_id = ias_credentials('clientid')
        self.client_secret = ias_credentials('clientsecret')
        self.app_cert = ias_credentials('certificate')
        self.app_key = ias_credentials('key')
        self.policies_url = f'{self.url}{self.AMS_API_ENDPOINT}/{self.ams_instance_id}/policies'
        self.token = self.__get_token()
        self.client_token = self.__get_client_token()
    
    def create_policy(self, policy:str):
        """
        creates policy at AMS
        Arguments:
            policy: the dcl policy for example: 'POLICY salesOrderDelete { GRANT delete ON salesOrder; }'
        Returns: {
            id: str
            name: str
            package: str[]
            label: str
            broken: bool
            modifiable: bool
            composed: bool
            ui_index: json
            dcl_file: json
            group_id: str
        }, policy_location
        see also: https://ams-qa-dev.accounts400.ondemand.com/authorization/sap/ams/v1/docs
        """
        policy_body = { 'dcl_file': { 'content': policy }}
        resp = http.post_request_auth_redirect(self.policies_url, json=policy_body, id_token=self.token)
        http.expect_status_code(resp.status_code, 201, f'{http.EXPECT_201}, POST to {self.policies_url}, error: {resp.text}')
        policy_location = resp.headers.get('Location')
        # poll for group_id in policy until available
        resp_has_group_id = lambda response: response.json()['group_id'] != None
        resp = http.get_request_verify_response(f'{self.url}/authorization{policy_location}', correct=resp_has_group_id, id_token=self.token)
        return resp.json(), policy_location

    def assign_user_to_policy(self, user_uuid:str, group_id:str):
        addUserBody = {
            "schemas": [ "urn:ietf:params:scim:api:messages:2.0:PatchOp" ],
            "Operations": [{
                "op": "add",
                "path": "members",
                "value": [{ "value": user_uuid }]
            }]
        }
        # need ias admin client_id and secret from technical user and not ias application token!
        headers = {
            'Content-Type': 'application/scim+json'
        }
        patchUrl = f'{self.url}/scim/Groups/{group_id}'
        resp = http.patch_request_cert(patchUrl, cert=Env.ias_client_cert(), key=Env.ias_client_key(), json=addUserBody, headers=headers)
        http.expect_status_code(resp.status_code, 204, f'{http.EXPECT_204} for PATCH request to {patchUrl}')
        return resp
    
    def delete_policy(self, policy_id:str):
        delete_url = f'{self.policies_url}/{policy_id}'
        resp = http.delete_request_auth_redirect(url=delete_url, id_token=self.token)
        http.expect_status_code(resp.status_code, 204, f'{http.EXPECT_204} for DELETE request to {delete_url}, error: {resp.text}')
        return resp
    
    def add_assertion_attribute(self):
        """
        This is only needed for landscapes where the ams is not in the same data center as ias, for now qa and live.
        We won't need this function in future anymore
        """
        url_service_get = self.url + '/service/sps?sp_name=' + self.guid
        # dont allow redirects because of this bug: https://support.wdf.sap.corp/sap/support/message/2270166355
        resp = http.get_request_via_mtls(
            url_service_get, 
            certificate=self.app_cert, 
            key=self.app_key, 
            redirects=False)
        http.expect_status_code(resp.status_code, 301, http.EXPECT_301)
        service_id = resp.headers.get('Location').rsplit('/', 1)[-1]
        url_service_put = self.url + '/service/sps/' + service_id
        json = {'constant_attributes':[{'assertionAttribute':'ias_admin','value':'${customAttribute1}'}]}
        resp = http.put_request_cert(url_service_put, cert=self.app_cert, key=self.app_key, json=json)
        http.expect_status_code(resp.status_code, 200, http.EXPECT_200)
        # renew tokens
        self.token = self.__get_token()
        self.client_token = self.__get_client_token()
    
    @retry(RequestFailedError, tries=10, delay=2)
    def check_policy_is_not_assigned(self, path):
        resp = http.get_request(path, id_token=self.token)
        http.expect_status_code(resp.status_code, 403, 
            f'policy for path: {path} should not be assigned or even created, {http.EXPECT_403}')

    def __get_client_token(self):
        if self.app_cert is not None:
            return http.get_token_via_mtls(
                token_url = self.url + self.TOKEN_ENDPOINT,
                clientid = self.client_id,
                tenant_id = Env().provider_tenant_id(),
                certificate = self.app_cert,
                certificate_key = self.app_key,
                grant_type = 'client_credentials')

    def __get_token(self):
        if self.app_cert is not None:
            return http.get_token_via_mtls(
                token_url = self.url + self.TOKEN_ENDPOINT,
                clientid = self.client_id,
                tenant_id = Env.provider_tenant_id(),
                certificate = self.app_cert,
                certificate_key = self.app_key,
                username = Env.username(),
                password = Env.password())
        return http.get_token(
                token_url = self.url + self.TOKEN_ENDPOINT,
                clientid = self.client_id,
                clientsecret = self.client_secret,
                username = Env.username(),
                password = Env.password()).get('id_token')
