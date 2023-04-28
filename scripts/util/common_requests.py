import logging
import os
from requests import Request, Session
from uuid import uuid4
from retry import retry
from base64 import b64encode
from urllib.parse import urlencode
import requests
from typing import Callable

logger = logging.getLogger(__name__)
logger.setLevel(logging.DEBUG)


class RequestFailedError(Exception):
    pass

class HttpUtil:
    EXPECT_200 = 'Expected HTTP status 200'
    EXPECT_201 = 'Expected HTTP status 201'
    EXPECT_204 = 'Expected HTTP status 204'
    EXPECT_301 = 'Expected HTTP status 301'
    EXPECT_400 = 'Expected HTTP status 400'
    EXPECT_401 = 'Expected HTTP status 401'
    EXPECT_403 = 'Expected HTTP status 403'
    EXPECT_500 = 'Expected HTTP status 500'

    @classmethod
    @retry(requests.exceptions.ConnectionError, tries=7, delay=10)
    def get_request(cls, url, id_token=None):
        logger.info('Performing GET request to {} {}'.format(url, 'using oidc token' if id_token else ''))
        response = requests.get(url=url, headers=cls.__authorization_header(id_token))
        if response.status_code != 200:
            logger.info(f"GET request to '{url}' failed - {response.status_code}: {response.text}") # its not always an error
        else:
            logger.info(f"GET request to '{url}' succeeded - {response.text}")
        return response
    
    @classmethod
    @retry(RequestFailedError, tries=10, delay=3)
    def get_request_retry(cls, url: str, expected_status_code: int, id_token=None):
        response = requests.get(url=url, headers=cls.__authorization_header(id_token))
        cls.expect_status_code(response.status_code, expected_status_code, f'get request to "{url}" failed: {response.text}')
        return response
    
    @classmethod
    @retry(RequestFailedError, tries=10, delay=3)
    def get_request_verify_response(cls, url, correct: Callable[[requests.Response], bool], id_token=None):
        headers = cls.__authorization_header(id_token)
        response = requests.get(url=url, headers=headers, allow_redirects=False)
        if response.status_code == 307:
            response = requests.get(url=response.headers['Location'], headers=headers)
        else:
            cls.expect_status_code(response.status_code, 200, f'get request to "{url}" failed: {response.text}')
        if correct(response) == False:
            raise RequestFailedError(f'response of get request to "{url}" could not be verified. response: {response.text}')
        return response

    @classmethod
    def get_request_via_mtls(cls, url, certificate, key, id_token=None, redirects=True):
        cert_name_tuple = cls.__write_cert(certificate, key)
        s = Session()
        req = Request('GET', url=url, headers=cls.__authorization_header(id_token))
        res = s.send(req.prepare(), cert=cert_name_tuple, allow_redirects=redirects)
        s.close()
        cls.__delete_cert(cert_name_tuple)
        if res.status_code != 200:
            logger.info(f"GET request to '{url}' failed - {res.status_code}: {res.text}") # its not always an error
        else:
            logger.info(f"GET request to '{url}' succeeded - {res.text}")
        return res

    @classmethod
    def post_request(cls, url, json=None, data=None, id_token=None, headers={}):
        if headers == {}:
            headers = cls.__authorization_header(id_token)
        response = requests.post(url=url, json=json, data=data, headers=headers)
        return response
    
    @classmethod
    def post_request_auth_redirect(cls, url, json=None, data=None, id_token=None):
        """
        all http libraries drop the authorization header after a redirect (redirect is per default true)
        for security reasons. This function adds the authorization header to the redirect url.
        """
        headers = cls.__authorization_header(id_token)
        response = requests.post(url=url, json=json, data=data, headers=headers, allow_redirects=False)
        if response.status_code == 307:
            response = requests.post(url=response.headers['Location'], json=json, data=data, headers=headers)
        return response
    
    @classmethod
    def put_request(cls, url, data=None, json=None, id_token=None, headers=None):
        if headers == None and id_token != None:
            headers=cls.__authorization_header(id_token)
        response = requests.put(url=url, data=data, json=json, headers=headers)
        return response
    
    @classmethod
    def put_request_cert(cls, url, cert, key, json=None, data=None, headers=None):
        cert_name_tuple = cls.__write_cert(cert, key)
        response = requests.put(url=url, json=json, data=data, cert=cert_name_tuple, headers=headers)
        cls.__delete_cert(cert_name_tuple)
        return response
    
    @classmethod
    def delete_request(cls, url, id_token=None, data=None):
        response = requests.delete(url=url, data=data, headers=cls.__authorization_header(id_token))
        return response
    
    @classmethod
    def delete_request_auth_redirect(cls, url, json=None, data=None, id_token=None):
        """
        all http libraries drop the authorization header after a redirect (redirect is per default true)
        for security reasons. This function adds the authorization header to the redirect url.
        """
        headers = cls.__authorization_header(id_token)
        response = requests.delete(url=url, json=json, data=data, headers=headers, allow_redirects=False)
        if response.status_code == 307:
            response = requests.delete(url=response.headers['Location'], json=json, data=data, headers=headers)
        return response
    
    @classmethod
    def patch_request(cls, url, json=None, headers=None):
        response = requests.patch(url=url, json=json, headers=headers)
        return response
    
    @classmethod
    def patch_request_cert(cls, url, cert, key, json=None, headers=None):
        cert_name_tuple = cls.__write_cert(cert, key)
        response = requests.patch(url=url, json=json, headers=headers, cert=cert_name_tuple)
        cls.__delete_cert(cert_name_tuple)
        return response

    @classmethod
    @retry(RequestFailedError, tries=10, delay=12)
    def get_token_via_mtls(cls, token_url, clientid, certificate, certificate_key, grant_type='password',
                           username=None, password=None, tenant_id=None):
        body = {'client_id': clientid,
                'grant_type': grant_type,
                'response_type': 'id_token',
                'username': username,
                'password': password}
        if tenant_id is not None:
            body['zone_uuid'] = tenant_id

        cert_name_tuple = cls.__write_cert(certificate, certificate_key)
        s = Session()
        headers = {'Content-Type': 'application/x-www-form-urlencoded'}
        req = Request('POST', url=token_url, data=urlencode(body).encode(), headers=headers)
        resp = s.send(req.prepare(), cert=cert_name_tuple)
        s.close()
        cls.__delete_cert(cert_name_tuple)
        if resp.status_code != 200:
            raise RequestFailedError(f"mTLS post request to '{token_url}' failed - {resp.status_code}: {resp.text}")

        token = resp.json().get('id_token')
        if token is not None:
            return token
        return resp.json().get('access_token')

    @classmethod
    def get_token(cls, token_url, clientid, clientsecret, grant_type='password', username=None, password=None):
        authorization_value = b64encode(bytes("{}:{}".format(clientid, clientsecret), 'utf-8')).decode("ascii")
        additional_headers = {'Authorization': 'Basic ' + authorization_value,
                              'Content-Type': 'application/x-www-form-urlencoded'}
        post_req_body = urlencode({'grant_type': grant_type,
                                   'username': username,
                                   'password': password}).encode()
        response = requests.post(token_url, data=post_req_body, headers=additional_headers)
        cls.expect_status_code(response.status_code, 200, f'post request to "{token_url}" failed: {response.text}')
        return response.json()
    
    @classmethod
    def basic_auth_header(cls, username, password):
        return b64encode(bytes("{}:{}".format(username, password), 'utf-8')).decode("ascii")
    
    @classmethod
    def expect_status_code(cls, status_code: int, expected_status_code: int, err_msg: str):
        if status_code != expected_status_code:
            raise RequestFailedError(f'{status_code} != {expected_status_code}: {err_msg}')

    @classmethod
    def __authorization_header(cls, id_token=None):
        if id_token:
            return {'Authorization': 'Bearer ' + id_token}
        else:
            return None
    
    @classmethod
    def __write_cert(cls, cert, key):
        id = str(uuid4())
        cert_name = f'cert_{id}.pem'
        key_name = f'key_{id}.key'
        with open(cert_name, mode='w', encoding='utf-8') as cert_pem:
            cert_pem.write(cert)
        with open(key_name, mode='w', encoding='utf-8') as key_pem:
            key_pem.write(key)
        return cert_name, key_name
    
    @classmethod
    def __delete_cert(cls, cert_tuple):
        os.unlink(cert_tuple[0])
        os.unlink(cert_tuple[1])